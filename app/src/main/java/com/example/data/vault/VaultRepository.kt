package com.example.data.vault

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DuplicateVaultItemException(message: String) : Exception(message)

/**
 * Repository orchestrating secure storage, indexing, atomic imports,
 * in-memory decryption previews, SAF exports, and portable backups.
 */
class VaultRepository(private val context: Context) {

    companion object {
        private val repoLock = Any()
        private val activeImportCount = AtomicInteger(0)
        private val bitmapCache = object : LruCache<String, Bitmap>(24) {}
    }

    private val vaultRootDir: File = File(context.filesDir, "vault")
    private val metadataDir: File = File(vaultRootDir, "metadata")
    private val picturesDir: File = File(vaultRootDir, "pictures")
    private val videosDir: File = File(vaultRootDir, "videos")
    private val documentsDir: File = File(vaultRootDir, "documents")
    private val indexFile: File = File(metadataDir, "vault_index.enc")

    private val itemsLock get() = repoLock
    private val memoryItems = mutableListOf<VaultItem>()

    init {
        synchronized(itemsLock) {
            ensureDirectories()
            if (activeImportCount.get() == 0) {
                cleanupOrphanedTmpFiles()
            }
            loadIndex()
        }
    }

    private fun ensureDirectories() {
        if (!vaultRootDir.exists()) vaultRootDir.mkdirs()
        if (!metadataDir.exists()) metadataDir.mkdirs()
        if (!picturesDir.exists()) picturesDir.mkdirs()
        if (!videosDir.exists()) videosDir.mkdirs()
        if (!documentsDir.exists()) documentsDir.mkdirs()

        // Place .nomedia to prevent Android MediaScanner from indexing vault files
        val nomediaFile = File(vaultRootDir, ".nomedia")
        if (!nomediaFile.exists()) {
            try {
                nomediaFile.createNewFile()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cleans up any leftover .tmp files from interrupted imports (power loss, crash).
     * Only runs when no import is currently in flight.
     */
    private fun cleanupOrphanedTmpFiles() {
        if (activeImportCount.get() > 0) return
        listOf(picturesDir, videosDir, documentsDir, metadataDir).forEach { dir ->
            dir.listFiles { file -> file.name.endsWith(".tmp") }?.forEach { tmpFile ->
                tmpFile.delete()
            }
        }
    }

    private fun getCategoryDir(category: VaultCategory): File {
        return when (category) {
            VaultCategory.PICTURE -> picturesDir
            VaultCategory.VIDEO -> videosDir
            VaultCategory.DOCUMENT -> documentsDir
        }
    }

    private fun loadIndex() {
        synchronized(itemsLock) {
            memoryItems.clear()
            if (!indexFile.exists() || indexFile.length() == 0L) {
                return
            }

            try {
                val encryptedBytes = indexFile.readBytes()
                val plainBytes = VaultCryptoEngine.decryptBytes(encryptedBytes)
                val jsonString = String(plainBytes, Charsets.UTF_8)
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val item = VaultItem.fromJsonObject(obj)
                    // Verify the corresponding encrypted file actually exists
                    val encFile = File(getCategoryDir(item.category), item.encryptedFileName)
                    if (encFile.exists() && encFile.length() > 0L) {
                        memoryItems.add(item)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VaultRepository", "Failed to load or decrypt vault index", e)
            }
        }
    }

    @Throws(Exception::class)
    private fun saveIndex() {
        synchronized(itemsLock) {
            ensureDirectories()
            val tmpIndex = File(metadataDir, "vault_index_${UUID.randomUUID().toString().take(8)}.tmp")
            try {
                val jsonArray = JSONArray()
                memoryItems.forEach { item ->
                    jsonArray.put(item.toJsonObject())
                }
                val plainBytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
                val encryptedBytes = VaultCryptoEngine.encryptBytes(plainBytes)

                tmpIndex.writeBytes(encryptedBytes)
                if (indexFile.exists() && !indexFile.delete()) {
                    // Fallback: overwrite indexFile directly if delete is blocked
                    indexFile.writeBytes(encryptedBytes)
                    tmpIndex.delete()
                    return
                }
                if (!tmpIndex.renameTo(indexFile)) {
                    indexFile.writeBytes(encryptedBytes)
                    tmpIndex.delete()
                }
            } catch (e: Exception) {
                tmpIndex.delete()
                android.util.Log.e("VaultRepository", "Failed to save encrypted vault index", e)
                throw e
            }
        }
    }

    fun getAllItems(): List<VaultItem> {
        synchronized(itemsLock) {
            if (memoryItems.isEmpty() && indexFile.exists() && indexFile.length() > 0L) {
                loadIndex()
            }
            return memoryItems.toList().sortedByDescending { it.importedTimestamp }
        }
    }

    fun getItems(category: VaultCategory): List<VaultItem> {
        synchronized(itemsLock) {
            if (memoryItems.isEmpty() && indexFile.exists() && indexFile.length() > 0L) {
                loadIndex()
            }
            return memoryItems.filter { it.category == category }.sortedByDescending { it.importedTimestamp }
        }
    }

    fun getItemCount(category: VaultCategory): Int {
        synchronized(itemsLock) {
            if (memoryItems.isEmpty() && indexFile.exists() && indexFile.length() > 0L) {
                loadIndex()
            }
            return memoryItems.count { it.category == category }
        }
    }

    fun getTotalVaultBytes(): Long {
        synchronized(itemsLock) {
            if (memoryItems.isEmpty() && indexFile.exists() && indexFile.length() > 0L) {
                loadIndex()
            }
            return memoryItems.sumOf { it.fileSizeRaw }
        }
    }

    fun getEncryptedFile(item: VaultItem): File {
        return File(getCategoryDir(item.category), item.encryptedFileName)
    }

    /**
     * Opens a readable InputStream for any content:// or file:// Uri, with fallback to
     * ContentResolver.openAssetFileDescriptor for virtual/cloud/media documents.
     */
    private fun openUriInputStream(uri: Uri): InputStream? {
        val contentResolver = context.contentResolver
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {
            // Not all URIs (e.g. PhotoPicker or file:// URIs) support persistable permissions; safe to ignore
        }

        try {
            val directStream = contentResolver.openInputStream(uri)
            if (directStream != null) return directStream
        } catch (_: Throwable) {
        }

        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Imports a file from a content Uri via SAF / Photo Picker.
     * Streams directly from the source URI through SHA-256 calculation and AES-256-GCM encryption
     * in a single pass without writing unencrypted temporary files or loading full files into memory.
     */
    suspend fun importFromUri(
        uri: Uri,
        preferredName: String? = null,
        preferredMimeType: String? = null,
        fallbackCategory: VaultCategory? = null
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        activeImportCount.incrementAndGet()
        var tempEncFile: File? = null
        var targetEncFile: File? = null

        try {
            ensureDirectories()
            val contentResolver = context.contentResolver

            val fileName = preferredName ?: queryFileName(uri) ?: "vault_file_${System.currentTimeMillis()}"
            val rawMime = preferredMimeType ?: runCatching { contentResolver.getType(uri) }.getOrNull()
            val mimeType = if (!rawMime.isNullOrBlank() && rawMime != "application/octet-stream") {
                rawMime
            } else {
                guessMimeType(fileName, fallbackCategory)
            }
            val category = if (mimeType == "application/octet-stream" && fallbackCategory != null) {
                fallbackCategory
            } else {
                VaultCategory.fromMimeType(mimeType)
            }
            val categoryDir = getCategoryDir(category)
            if (!categoryDir.exists()) categoryDir.mkdirs()

            val id = UUID.randomUUID().toString()
            val encFileName = "${category.name.lowercase()}_${id.take(8)}.enc"
            targetEncFile = File(categoryDir, encFileName)
            tempEncFile = File(categoryDir, "$encFileName.tmp")

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            val sourceStream = openUriInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot open selected file"))

            sourceStream.use { rawIn ->
                val hashingIn = object : FilterInputStream(rawIn) {
                    override fun read(): Int {
                        val b = super.read()
                        if (b != -1) {
                            digest.update(b.toByte())
                            totalBytes += 1
                        }
                        return b
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val count = super.read(b, off, len)
                        if (count > 0) {
                            digest.update(b, off, count)
                            totalBytes += count
                        }
                        return count
                    }
                }

                FileOutputStream(tempEncFile).use { encOut ->
                    VaultCryptoEngine.encryptStream(hashingIn, encOut)
                }
            }

            if (totalBytes <= 0L) {
                tempEncFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Selected file is empty or unreadable"))
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

            synchronized(itemsLock) {
                if (memoryItems.isEmpty() && indexFile.exists() && indexFile.length() > 0L) {
                    loadIndex()
                }
                val exists = memoryItems.any { it.contentSha256 == sha256 }
                if (exists) {
                    tempEncFile.delete()
                    return@withContext Result.failure(
                        DuplicateVaultItemException("This file is already in your private vault.")
                    )
                }

                // Atomic rename (with copy fallback if filesystem blocks rename)
                if (!tempEncFile.renameTo(targetEncFile)) {
                    try {
                        tempEncFile.copyTo(targetEncFile, overwrite = true)
                        tempEncFile.delete()
                    } catch (copyErr: Exception) {
                        tempEncFile.delete()
                        targetEncFile.delete()
                        return@withContext Result.failure(
                            java.io.IOException("Failed to finalize encrypted file in vault storage", copyErr)
                        )
                    }
                }

                val newItem = VaultItem(
                    id = id,
                    originalFileName = fileName,
                    mimeType = mimeType,
                    fileSizeRaw = totalBytes,
                    encryptedFileName = encFileName,
                    category = category,
                    importedTimestamp = System.currentTimeMillis(),
                    contentSha256 = sha256
                )

                memoryItems.add(newItem)
                try {
                    saveIndex()
                } catch (indexError: Exception) {
                    memoryItems.remove(newItem)
                    targetEncFile.delete()
                    throw indexError
                }

                Result.success(newItem)
            }
        } catch (t: Throwable) {
            tempEncFile?.delete()
            android.util.Log.e("VaultRepository", "Vault import failed for URI: $uri", t)
            val exception = if (t is Exception) t else RuntimeException(t.message ?: "Vault import error", t)
            Result.failure(exception)
        } finally {
            activeImportCount.decrementAndGet()
        }
    }

    /**
     * Decrypts an image into an in-memory downsampled Bitmap for private preview without
     * exhausting heap memory or exceeding Canvas maximum texture limits on high-res photos.
     */
    suspend fun loadDecryptedBitmap(item: VaultItem, maxDimension: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${item.id}_$maxDimension"
        synchronized(bitmapCache) {
            val cached = bitmapCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        val encFile = getEncryptedFile(item)
        if (!encFile.exists() || encFile.length() == 0L) return@withContext null

        var tempPreviewFile: File? = null
        try {
            ensureDirectories()
            activeImportCount.incrementAndGet()
            tempPreviewFile = File(metadataDir, "preview_${UUID.randomUUID()}.tmp")

            FileInputStream(encFile).use { encIn ->
                FileOutputStream(tempPreviewFile).use { plainOut ->
                    VaultCryptoEngine.decryptStream(encIn, plainOut)
                }
            }

            // 1. Decode image dimensions only without allocating pixel memory
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempPreviewFile.absolutePath, boundsOptions)

            val outWidth = boundsOptions.outWidth
            val outHeight = boundsOptions.outHeight
            if (outWidth <= 0 || outHeight <= 0) {
                return@withContext null
            }

            // 2. Compute power-of-2 sample size so decoded bitmap fits safely within maxDimension
            var sampleSize = 1
            val targetMax = maxDimension.coerceAtLeast(128)
            while ((outWidth / sampleSize) > targetMax || (outHeight / sampleSize) > targetMax) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = try {
                BitmapFactory.decodeFile(tempPreviewFile.absolutePath, decodeOptions)
            } catch (_: OutOfMemoryError) {
                val fallbackOptions = BitmapFactory.Options().apply {
                    inSampleSize = (sampleSize * 2).coerceAtLeast(2)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(tempPreviewFile.absolutePath, fallbackOptions)
            }

            if (decoded != null) {
                synchronized(bitmapCache) {
                    bitmapCache.put(cacheKey, decoded)
                }
            }
            decoded
        } catch (_: Throwable) {
            null
        } finally {
            tempPreviewFile?.delete()
            activeImportCount.decrementAndGet()
        }
    }

    /**
     * Decrypts an item directly into a user's destination SAF Uri.
     */
    suspend fun exportItemToUri(item: VaultItem, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val encFile = getEncryptedFile(item)
        if (!encFile.exists()) return@withContext Result.failure(Exception("Encrypted file not found"))

        try {
            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                FileInputStream(encFile).use { inStream ->
                    VaultCryptoEngine.decryptStream(inStream, outStream)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open export destination"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an item from the vault and updates the encrypted index.
     */
    fun deleteItem(item: VaultItem): Boolean {
        synchronized(itemsLock) {
            val file = getEncryptedFile(item)
            if (file.exists()) {
                file.delete()
            }
            synchronized(bitmapCache) {
                bitmapCache.remove("${item.id}_512")
                bitmapCache.remove("${item.id}_1024")
            }
            val removed = memoryItems.removeAll { it.id == item.id }
            if (removed) {
                runCatching { saveIndex() }
            }
            return removed
        }
    }

    /**
     * Exports entire vault into a portable encrypted .salvault backup container.
     */
    suspend fun exportBackup(passphrase: String, outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val tempZipFile = File(metadataDir, "backup_archive.tmp")
            val itemsToBackup: List<VaultItem>
            synchronized(itemsLock) {
                itemsToBackup = memoryItems.toList()
            }

            ZipOutputStream(FileOutputStream(tempZipFile)).use { zipOut ->
                // Write manifest.json
                val jsonArray = JSONArray()
                itemsToBackup.forEach { jsonArray.put(it.toJsonObject()) }
                zipOut.putNextEntry(ZipEntry("manifest.json"))
                zipOut.write(jsonArray.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // Write each payload decrypted from device-local key into the passphrase-encrypted archive
                // so that portable .salvault backups remain 100% recoverable after reinstall or on a new device
                itemsToBackup.forEach { item ->
                    val file = getEncryptedFile(item)
                    if (file.exists()) {
                        zipOut.putNextEntry(ZipEntry("files/${item.encryptedFileName}"))
                        FileInputStream(file).use { fileIn ->
                            VaultCryptoEngine.decryptStream(fileIn, zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            // Encrypt archive with PBKDF2 passphrase
            FileInputStream(tempZipFile).use { zipIn ->
                VaultCryptoEngine.encryptWithPassphrase(zipIn, outputStream, passphrase.toCharArray())
            }

            tempZipFile.delete()
            Result.success(itemsToBackup.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores an encrypted .salvault backup into the vault, re-encrypting each file
     * with the current device's Android Keystore master key. Skips existing duplicates.
     */
    suspend fun restoreBackup(passphrase: String, inputStream: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        val tempZipFile = File(metadataDir, "restore_archive.tmp")
        try {
            FileOutputStream(tempZipFile).use { zipOut ->
                VaultCryptoEngine.decryptWithPassphrase(inputStream, zipOut, passphrase.toCharArray())
            }

            var restoredCount = 0
            val manifestItems = mutableListOf<VaultItem>()

            ZipInputStream(FileInputStream(tempZipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.json") {
                        val text = zipIn.bufferedReader().readText()
                        val array = JSONArray(text)
                        for (i in 0 until array.length()) {
                            manifestItems.add(VaultItem.fromJsonObject(array.getJSONObject(i)))
                        }
                    } else if (entry.name.startsWith("files/")) {
                        val fileName = entry.name.removePrefix("files/")
                        val manifestItem = manifestItems.firstOrNull { it.encryptedFileName == fileName }
                        if (manifestItem != null) {
                            val categoryDir = getCategoryDir(manifestItem.category)
                            val targetFile = File(categoryDir, fileName)

                            // Check duplicate
                            var isDuplicate = false
                            synchronized(itemsLock) {
                                isDuplicate = memoryItems.any { it.contentSha256 == manifestItem.contentSha256 }
                            }

                            if (!isDuplicate) {
                                FileOutputStream(targetFile).use { fileOut ->
                                    VaultCryptoEngine.encryptStream(zipIn, fileOut)
                                }
                                synchronized(itemsLock) {
                                    memoryItems.add(manifestItem)
                                    restoredCount++
                                }
                            }
                        }
                    }
                    entry = zipIn.nextEntry
                }
            }

            tempZipFile.delete()
            synchronized(itemsLock) {
                saveIndex()
            }
            Result.success(restoredCount)
        } catch (e: Exception) {
            tempZipFile.delete()
            Result.failure(e)
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val queried = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (_: Throwable) {
            null
        }
        if (!queried.isNullOrBlank()) return queried

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
    }

    private fun guessMimeType(fileName: String, fallbackCategory: VaultCategory? = null): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "bmp" -> "image/bmp"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt", "csv", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> when (fallbackCategory) {
                VaultCategory.PICTURE -> "image/jpeg"
                VaultCategory.VIDEO -> "video/mp4"
                VaultCategory.DOCUMENT, null -> "application/octet-stream"
            }
        }
    }
}
