package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.vault.DuplicateVaultItemException
import com.example.data.vault.VaultCategory
import com.example.data.vault.VaultCryptoEngine
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
}
