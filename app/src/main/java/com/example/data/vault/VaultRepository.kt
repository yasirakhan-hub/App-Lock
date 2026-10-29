package com.example.data.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DuplicateVaultItemException(message: String) : Exception(message)

/**
 * Repository orchestrating secure storage, indexing, atomic imports,
 * in-memory decryption previews, SAF exports, and portable backups.
 */
class VaultRepository(private val context: Context) {

    private val vaultRootDir: File = File(context.filesDir, "vault")
    private val metadataDir: File = File(vaultRootDir, "metadata")
    private val picturesDir: File = File(vaultRootDir, "pictures")
    private val videosDir: File = File(vaultRootDir, "videos")
    private val documentsDir: File = File(vaultRootDir, "documents")
    private val indexFile: File = File(metadataDir, "vault_index.enc")

    private val itemsLock = Any()
    private val memoryItems = mutableListOf<VaultItem>()

    init {
        ensureDirectories()
        cleanupOrphanedTmpFiles()
        loadIndex()
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
     */
    private fun cleanupOrphanedTmpFiles() {
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
            val tmpIndex = File(metadataDir, "vault_index.tmp")
            try {
                val jsonArray = JSONArray()
                memoryItems.forEach { item ->
                    jsonArray.put(item.toJsonObject())
                }
                val plainBytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
                val encryptedBytes = VaultCryptoEngine.encryptBytes(plainBytes)

                tmpIndex.writeBytes(encryptedBytes)
                if (indexFile.exists() && !indexFile.delete()) {
                    throw java.io.IOException("Unable to replace existing vault index file")
                }
                if (!tmpIndex.renameTo(indexFile)) {
                    throw java.io.IOException("Unable to commit updated vault index file")
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
            return memoryItems.toList().sortedByDescending { it.importedTimestamp }
        }
    }

    fun getItems(category: VaultCategory): List<VaultItem> {
        synchronized(itemsLock) {
            return memoryItems.filter { it.category == category }.sortedByDescending { it.importedTimestamp }
        }
    }

    fun getItemCount(category: VaultCategory): Int {
        synchronized(itemsLock) {
            return memoryItems.count { it.category == category }
        }
    }

    fun getTotalVaultBytes(): Long {
        synchronized(itemsLock) {
            return memoryItems.sumOf { it.fileSizeRaw }
        }
    }

    fun getEncryptedFile(item: VaultItem): File {
        return File(getCategoryDir(item.category), item.encryptedFileName)
    }

    /**
     * Imports a file from a content Uri via SAF / Photo Picker.
     * Computes SHA-256, verifies duplicates, writes to .tmp, encrypts to .enc,
     * and atomically saves metadata.
     */
    suspend fun importFromUri(
        uri: Uri,
        preferredName: String? = null,
        preferredMimeType: String? = null
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        val fileName = preferredName ?: queryFileName(uri) ?: "vault_file_${System.currentTimeMillis()}"
        val mimeType = preferredMimeType ?: contentResolver.getType(uri) ?: guessMimeType(fileName)
        val category = VaultCategory.fromMimeType(mimeType)
        val categoryDir = getCategoryDir(category)

        val tempPlainFile = File(categoryDir, "import_${UUID.randomUUID()}.tmp")
        var tempEncFile: File? = null
        var targetEncFile: File? = null
        val digest = MessageDigest.getInstance("SHA-256")

        var totalBytes = 0L

        try {
            contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(tempPlainFile).use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalBytes += read
                    }
                    outStream.flush()
                }
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot open selected file"))

            if (totalBytes <= 0L) {
                tempPlainFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Selected file is empty or unreadable"))
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

            // Check for duplicate
            synchronized(itemsLock) {
                val exists = memoryItems.any { it.contentSha256 == sha256 }
                if (exists) {
                    tempPlainFile.delete()
                    return@withContext Result.failure(
                        DuplicateVaultItemException("This file is already in your private vault.")
                    )
                }
            }

            // Encrypt from tempPlainFile into final encrypted file
            val id = UUID.randomUUID().toString()
            val encFileName = "${category.name.lowercase()}_${id.take(8)}.enc"
            targetEncFile = File(categoryDir, encFileName)
            tempEncFile = File(categoryDir, "$encFileName.tmp")

            FileInputStream(tempPlainFile).use { plainIn ->
                FileOutputStream(tempEncFile).use { encOut ->
                    VaultCryptoEngine.encryptStream(plainIn, encOut)
                }
            }

            // Remove plaintext temp immediately after encryption completes
            tempPlainFile.delete()

            // Atomic rename
            if (!tempEncFile.renameTo(targetEncFile)) {
                tempEncFile.delete()
                return@withContext Result.failure(java.io.IOException("Failed to finalize encrypted file in vault storage"))
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

            synchronized(itemsLock) {
                memoryItems.add(newItem)
                try {
                    saveIndex()
                } catch (indexError: Exception) {
                    memoryItems.remove(newItem)
                    targetEncFile.delete()
                    throw indexError
                }
            }

            Result.success(newItem)
        } catch (e: Exception) {
            tempPlainFile.delete()
            tempEncFile?.delete()
            android.util.Log.e("VaultRepository", "Vault import failed for URI", e)
            Result.failure(e)
        }
    }

    /**
     * Decrypts an image into an in-memory Bitmap for private preview without writing to disk.
     */
    suspend fun loadDecryptedBitmap(item: VaultItem): Bitmap? = withContext(Dispatchers.IO) {
        val encFile = getEncryptedFile(item)
        if (!encFile.exists()) return@withContext null

        try {
            val encBytes = encFile.readBytes()
            val plainBytes = VaultCryptoEngine.decryptBytes(encBytes)
            BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
        } catch (_: Exception) {
            null
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
            val removed = memoryItems.removeAll { it.id == item.id }
            if (removed) {
                saveIndex()
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
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
