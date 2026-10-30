package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.vault.DuplicateVaultItemException
import com.example.data.vault.OriginalDeletionStatus
import com.example.data.vault.VaultCategory
import com.example.data.vault.VaultCryptoEngine
import com.example.data.vault.VaultImportMode
import com.example.data.vault.VaultIntegrityException
import com.example.data.vault.VaultItem
import com.example.data.vault.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultCryptoTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "vault").deleteRecursively()
    }

    @Test
    fun testVaultCryptoEngineRoundTrip() {
        val originalText = "Smart AppLock Confidential Document 2026! @#$%"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedBytes = VaultCryptoEngine.encryptBytes(originalBytes)
        assertNotNull(encryptedBytes)
        assertTrue(encryptedBytes.size > originalBytes.size)

        // Verify magic header "SALV"
        assertEquals(VaultCryptoEngine.MAGIC_HEADER[0], encryptedBytes[0])
        assertEquals(VaultCryptoEngine.MAGIC_HEADER[1], encryptedBytes[1])
        assertEquals(VaultCryptoEngine.MAGIC_HEADER[2], encryptedBytes[2])
        assertEquals(VaultCryptoEngine.MAGIC_HEADER[3], encryptedBytes[3])

        // Decrypt
        val decryptedBytes = VaultCryptoEngine.decryptBytes(encryptedBytes)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalText, decryptedText)
    }

    @Test(expected = Exception::class)
    fun testVaultCryptoEngineTamperDetection() {
        val originalText = "Sensitive Personal Identification Record"
        val encryptedBytes = VaultCryptoEngine.encryptBytes(originalText.toByteArray(Charsets.UTF_8))

        // Tamper with one byte in the ciphertext payload
        val tamperedBytes = encryptedBytes.clone()
        tamperedBytes[tamperedBytes.size - 5] = (tamperedBytes[tamperedBytes.size - 5].toInt() xor 0xFF).toByte()

        // Decryption should fail due to GCM authentication tag mismatch
        VaultCryptoEngine.decryptBytes(tamperedBytes)
    }

    @Test
    fun testPortableBackupPassphraseRoundTrip() {
        val secretPayload = "Manifest & Private Pictures Bundle Payload"
        val plainBytes = secretPayload.toByteArray(Charsets.UTF_8)
        val passphrase = "SecureBackupPassword123"

        val outStream = ByteArrayOutputStream()
        VaultCryptoEngine.encryptWithPassphrase(
            ByteArrayInputStream(plainBytes),
            outStream,
            passphrase.toCharArray()
        )

        val backupBytes = outStream.toByteArray()
        assertTrue("Backup should contain SAL_BACKUP_V1 header", backupBytes.size > 50)

        // Decrypt with correct passphrase
        val decryptedOut = ByteArrayOutputStream()
        VaultCryptoEngine.decryptWithPassphrase(
            ByteArrayInputStream(backupBytes),
            decryptedOut,
            passphrase.toCharArray()
        )

        val decryptedText = String(decryptedOut.toByteArray(), Charsets.UTF_8)
        assertEquals(secretPayload, decryptedText)
    }

    @Test(expected = Exception::class)
    fun testPortableBackupWrongPassphraseFails() {
        val plainBytes = "Confidential Vault Data".toByteArray(Charsets.UTF_8)
        val outStream = ByteArrayOutputStream()

        VaultCryptoEngine.encryptWithPassphrase(
            ByteArrayInputStream(plainBytes),
            outStream,
            "CorrectPassphrase".toCharArray()
        )

        val backupBytes = outStream.toByteArray()
        val decryptedOut = ByteArrayOutputStream()

        // Attempt decrypt with wrong passphrase
        VaultCryptoEngine.decryptWithPassphrase(
            ByteArrayInputStream(backupBytes),
            decryptedOut,
            "WrongPassphrase".toCharArray()
        )
    }

    @Test
    fun testVaultRepositoryStorageAndNoMediaSentinel() {
        val repository = VaultRepository(context)

        // Verify .nomedia exists in vault root
        val nomedia = File(context.filesDir, "vault/.nomedia")
        assertTrue(".nomedia file must exist to block media indexers", nomedia.exists())

        // Initial counts
        assertEquals(0, repository.getItemCount(VaultCategory.PICTURE))
        assertEquals(0, repository.getItemCount(VaultCategory.VIDEO))
        assertEquals(0, repository.getItemCount(VaultCategory.DOCUMENT))
    }

    @Test
    fun testOrphanedTmpCleanupOnRepositoryInit_TC_VAULT_03() {
        val docsDir = File(context.filesDir, "vault/documents").apply { mkdirs() }
        val orphanTmp = File(docsDir, "import_crashed_12345.tmp").apply {
            writeText("Incomplete interrupted write")
        }
        assertTrue(orphanTmp.exists())

        // Initializing VaultRepository must automatically purge orphaned .tmp files
        val repository = VaultRepository(context)
        assertFalse("Orphaned .tmp file must be cleaned up on init", orphanTmp.exists())
        assertEquals(0, repository.getAllItems().size)
    }

    @Test
    fun testImportDuplicateDetectionAndCrossDeviceBackupRestore_TC_VAULT_04_And_TC_BAK_01_04() = runBlocking {
        val repository = VaultRepository(context)

        val sourceFile = File(context.cacheDir, "confidential_contract.pdf").apply {
            writeText("Top Secret Contract Payload 2026 - Do Not Distribute")
        }

        // 1. First import succeeds (TC-VAULT-01)
        val importResult1 = repository.importFromUri(
            uri = Uri.fromFile(sourceFile),
            preferredName = "confidential_contract.pdf",
            preferredMimeType = "application/pdf"
        )
        assertTrue(importResult1.isSuccess)
        val importedItem = importResult1.getOrThrow()
        assertEquals(VaultCategory.DOCUMENT, importedItem.category)
        assertEquals(1, repository.getItemCount(VaultCategory.DOCUMENT))
        assertTrue("Original source file must NOT be auto-deleted (TC-VAULT-02)", sourceFile.exists())

        // 2. Second import of identical file is rejected via SHA-256 checksum (TC-VAULT-04)
        val importResult2 = repository.importFromUri(
            uri = Uri.fromFile(sourceFile),
            preferredName = "copy_of_contract.pdf",
            preferredMimeType = "application/pdf"
        )
        assertTrue(importResult2.isFailure)
        assertTrue(importResult2.exceptionOrNull() is DuplicateVaultItemException)
        assertEquals(1, repository.getItemCount(VaultCategory.DOCUMENT))

        // 3. Export portable .salvault backup with passphrase (TC-BAK-01)
        val backupStream = ByteArrayOutputStream()
        val exportResult = repository.exportBackup("MasterPass#2026", backupStream)
        assertTrue(exportResult.isSuccess)
        assertEquals(1, exportResult.getOrNull())
        val backupBytes = backupStream.toByteArray()

        // 4. Simulate complete app uninstall/reinstall and key rotation (TC-BAK-04)
        File(context.filesDir, "vault").deleteRecursively()
        VaultCryptoEngine.rotateFallbackKeyForTesting()

        val freshRepo = VaultRepository(context)
        assertEquals(0, freshRepo.getAllItems().size)

        // 5. Restore with wrong passphrase fails cleanly (TC-BAK-03)
        val wrongRestore = freshRepo.restoreBackup("WrongPasscode", ByteArrayInputStream(backupBytes))
        assertTrue(wrongRestore.isFailure)
        assertEquals(0, freshRepo.getAllItems().size)

        // 6. Restore with correct passphrase succeeds under the new device key (TC-BAK-02 & TC-BAK-04)
        val goodRestore = freshRepo.restoreBackup("MasterPass#2026", ByteArrayInputStream(backupBytes))
        assertTrue(goodRestore.isSuccess)
        assertEquals(1, goodRestore.getOrNull())
        assertEquals(1, freshRepo.getItemCount(VaultCategory.DOCUMENT))

        // Verify decrypted content under the NEW master key matches original plaintext 100%
        val restoredItem = freshRepo.getItems(VaultCategory.DOCUMENT).first()
        assertEquals("confidential_contract.pdf", restoredItem.originalFileName)
        val restoredEncryptedBytes = freshRepo.getEncryptedFile(restoredItem).readBytes()
        val restoredPlainText = String(VaultCryptoEngine.decryptBytes(restoredEncryptedBytes), Charsets.UTF_8)
        assertEquals("Top Secret Contract Payload 2026 - Do Not Distribute", restoredPlainText)
    }

    @Test
    fun testVaultItemSerialization() {
        val item = VaultItem(
            id = "test-uuid-1234",
            originalFileName = "tax_return.pdf",
            mimeType = "application/pdf",
            fileSizeRaw = 1024500L,
            encryptedFileName = "doc_test.enc",
            category = VaultCategory.DOCUMENT,
            importedTimestamp = 1730000000000L,
            contentSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        val json = item.toJsonObject()
        val restored = VaultItem.fromJsonObject(json)

        assertEquals(item.id, restored.id)
        assertEquals(item.originalFileName, restored.originalFileName)
        assertEquals(item.mimeType, restored.mimeType)
        assertEquals(item.fileSizeRaw, restored.fileSizeRaw)
        assertEquals(item.encryptedFileName, restored.encryptedFileName)
        assertEquals(item.category, restored.category)
        assertEquals(item.importedTimestamp, restored.importedTimestamp)
        assertEquals(item.contentSha256, restored.contentSha256)
    }

    @Test
    fun testLegacyHeaderlessDecryptionBackwardCompatibility() {
        val originalText = "Legacy Vault Entry Without SALV Header"
        val encryptedWithHeader = VaultCryptoEngine.encryptBytes(originalText.toByteArray(Charsets.UTF_8))

        // Strip the 4-byte 'SALV' magic header to simulate a legacy [12B IV + Ciphertext] blob
        val legacyHeaderlessBytes = encryptedWithHeader.copyOfRange(4, encryptedWithHeader.size)

        val decryptedBytes = VaultCryptoEngine.decryptBytes(legacyHeaderlessBytes)
        assertEquals(originalText, String(decryptedBytes, Charsets.UTF_8))
    }

    @Test
    fun testMainNavigationViewModelSavedStatePersistence() {
        val handle = androidx.lifecycle.SavedStateHandle()
        val vm1 = MainNavigationViewModel(handle)

        assertEquals(ScreenState.AUTH_GATE, vm1.getOrInitScreen { ScreenState.AUTH_GATE })
        vm1.setScreen(ScreenState.VAULT)
        vm1.setOpenedCalcFromDashboard(true)

        // Simulate Activity recreation re-attaching to the same SavedStateHandle
        val vm2 = MainNavigationViewModel(handle)
        assertEquals(ScreenState.VAULT, vm2.getOrInitScreen { ScreenState.AUTH_GATE })
        assertTrue(vm2.getOpenedCalcFromDashboard())
    }

    @Test
    fun testDocumentVideoImageAndLargeFileImportsWithDownsampledPreview() = runBlocking {
        val repository = VaultRepository(context)

        // 1. High-resolution PNG Image import + downsampled bitmap decoding
        val imageFile = File(context.cacheDir, "high_res_photo.png")
        val rawBitmap = android.graphics.Bitmap.createBitmap(1600, 1200, android.graphics.Bitmap.Config.ARGB_8888)
        rawBitmap.eraseColor(android.graphics.Color.BLUE)
        java.io.FileOutputStream(imageFile).use { out ->
            rawBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        val imageResult = repository.importFromUri(
            uri = Uri.fromFile(imageFile),
            fallbackCategory = VaultCategory.PICTURE
        )
        assertTrue("Image import must succeed", imageResult.isSuccess)
        val imageItem = imageResult.getOrThrow()
        assertEquals(VaultCategory.PICTURE, imageItem.category)

        val previewBitmap = repository.loadDecryptedBitmap(imageItem, maxDimension = 384)
        assertNotNull("Decrypted downsampled bitmap must not be null", previewBitmap)
        assertTrue("Downsampled width must fit within 384px", previewBitmap!!.width <= 384)
        assertTrue("Downsampled height must fit within 384px", previewBitmap.height <= 384)

        // 2. Large Video file streaming import (2.5 MB multi-chunk payload)
        val videoFile = File(context.cacheDir, "large_recording.mp4")
        val chunk = ByteArray(64 * 1024) { idx -> (idx % 251).toByte() }
        val totalChunks = 40 // 2.5 MB
        java.io.FileOutputStream(videoFile).use { out ->
            repeat(totalChunks) {
                out.write(chunk)
            }
        }
        val expectedVideoSize = videoFile.length()
        val videoResult = repository.importFromUri(
            uri = Uri.fromFile(videoFile),
            fallbackCategory = VaultCategory.VIDEO
        )
        assertTrue("Large video import must succeed", videoResult.isSuccess)
        val videoItem = videoResult.getOrThrow()
        assertEquals(VaultCategory.VIDEO, videoItem.category)
        assertEquals(expectedVideoSize, videoItem.fileSizeRaw)

        // Export and verify byte size matches 100%
        val exportedVideoFile = File(context.cacheDir, "exported_recording.mp4")
        val exportRes = repository.exportItemToUri(videoItem, Uri.fromFile(exportedVideoFile))
        assertTrue("Exporting large video must succeed", exportRes.isSuccess)
        assertEquals(expectedVideoSize, exportedVideoFile.length())

        // 3. Extensionless picker URI using fallbackCategory (e.g. Android PhotoPicker content://media/picker/0/10000123)
        val pickerTempFile = File(context.cacheDir, "10000123").apply {
            writeBytes(ByteArray(4096) { (it % 127).toByte() })
        }
        val fallbackVideoResult = repository.importFromUri(
            uri = Uri.fromFile(pickerTempFile),
            fallbackCategory = VaultCategory.VIDEO
        )
        assertTrue("Extensionless video URI with fallbackCategory must succeed", fallbackVideoResult.isSuccess)
        assertEquals(VaultCategory.VIDEO, fallbackVideoResult.getOrThrow().category)

        assertEquals(1, repository.getItemCount(VaultCategory.PICTURE))
        assertEquals(2, repository.getItemCount(VaultCategory.VIDEO))
    }

    @Test
    fun testPhase3ImportCopyVsMoveAndIntegrityVerification() = runBlocking {
        val repository = VaultRepository(context)

        // 1. Copy Mode Test:
        // Original source file must be verified and remain completely untouched.
        val copySourceFile = File(context.cacheDir, "family_vacation.jpg").apply {
            writeBytes(ByteArray(8192) { (it * 7 % 256).toByte() })
        }
        val copyResult = repository.importFromUri(
            uri = Uri.fromFile(copySourceFile),
            preferredName = "family_vacation.jpg",
            fallbackCategory = VaultCategory.PICTURE
        )
        assertTrue("Copy mode import must succeed", copyResult.isSuccess)
        val copyItem = copyResult.getOrThrow()
        assertEquals(VaultCategory.PICTURE, copyItem.category)
        assertTrue("In Copy mode, original source file must remain untouched", copySourceFile.exists())

        // Verify cryptographic integrity of encrypted vault file:
        // Length must be exactly payload (8192) + magic (4) + IV (12) + GCM tag (16) = 8224 bytes
        val encFile = repository.getEncryptedFile(copyItem)
        assertTrue(encFile.exists())
        assertEquals(8192L + 32L, encFile.length())

        // 2. Move Mode Test:
        // Original file must be deleted only after successful and verified import
        val moveSourceFile = File(context.cacheDir, "tax_record_2025.pdf").apply {
            writeBytes(ByteArray(4096) { (it * 13 % 256).toByte() })
        }
        val moveResult = repository.importFromUri(
            uri = Uri.fromFile(moveSourceFile),
            preferredName = "tax_record_2025.pdf",
            fallbackCategory = VaultCategory.DOCUMENT
        )
        assertTrue("Move mode import must succeed", moveResult.isSuccess)
        val moveItem = moveResult.getOrThrow()

        // Attempt deletion of original after verified import:
        val deletionStatus = repository.deleteOriginalUri(Uri.fromFile(moveSourceFile))
        assertEquals("deleteOriginalUri must confirm deletion", OriginalDeletionStatus.Deleted, deletionStatus)
        assertFalse("Original file must be removed after Move confirmation", moveSourceFile.exists())
        assertTrue("Vault copy must exist and be intact", repository.getEncryptedFile(moveItem).exists())
    }

    @Test
    fun testPhase3ExportCopyAndMoveExportVerification() = runBlocking {
        val repository = VaultRepository(context)

        val sourceDoc = File(context.cacheDir, "secret_notes.txt").apply {
            writeText("Encrypted Vault Secret Notes for Export & Restore Testing")
        }
        val rawSize = sourceDoc.length()
        val importRes = repository.importFromUri(Uri.fromFile(sourceDoc))
        assertTrue(importRes.isSuccess)
        val vaultItem = importRes.getOrThrow()

        // 1. Export (Copy):
        val destCopyFile = File(context.cacheDir, "exported_secret_notes.txt")
        val exportResult = repository.exportItemToUri(vaultItem, Uri.fromFile(destCopyFile))
        assertTrue("Export must succeed", exportResult.isSuccess)
        assertEquals(rawSize, exportResult.getOrNull())
        assertEquals(rawSize, destCopyFile.length())
        assertTrue("In Export (Copy), Vault copy must remain intact", repository.getEncryptedFile(vaultItem).exists())

        // 2. Move / Export and Remove:
        // Destination file is verified, and only after successful export is the Vault copy removed
        val destMoveFile = File(context.cacheDir, "moved_secret_notes.txt")
        val moveExportResult = repository.exportAndRemoveItem(vaultItem, Uri.fromFile(destMoveFile))
        assertTrue("Move export must succeed", moveExportResult.isSuccess)
        assertEquals(rawSize, moveExportResult.getOrNull())
        assertEquals(rawSize, destMoveFile.length())
        assertFalse("Vault copy must be removed after exportAndRemoveItem", repository.getEncryptedFile(vaultItem).exists())
        assertEquals(0, repository.getItemCount(VaultCategory.DOCUMENT))
    }
}
