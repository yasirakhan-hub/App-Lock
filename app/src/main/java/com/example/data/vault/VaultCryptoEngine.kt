package com.example.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Robust cryptographic engine for the Encrypted Private File Vault.
 *
 * Implements AES-256-GCM authenticated encryption backed by Android Keystore,
 * deterministic magic headers ("SALV"), 12-byte cryptographic nonces,
 * 128-bit integrity tags, and portable PBKDF2 passphrase-derived container backups.
 */
object VaultCryptoEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "SmartAppLock_Vault_MasterKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    // 4-byte Magic header: 'S', 'A', 'L', 'V' (0x53, 0x41, 0x4C, 0x56)
    val MAGIC_HEADER = byteArrayOf(0x53, 0x41, 0x4C, 0x56)

    // Portable backup constants
    val BACKUP_MAGIC_V1 = "SAL_BACKUP_V1".toByteArray(Charsets.US_ASCII)
    private const val BACKUP_SALT_LENGTH = 32
    private const val BACKUP_KDF_ITERATIONS = 100_000
    private const val BACKUP_KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Retrieves or generates the 256-bit AES master key from the Android Keystore.
     * Falls back to a local software key if Keystore is unavailable (e.g., unit test environments).
     */
    @Synchronized
    fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            val keyEntry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            keyEntry.secretKey
        } catch (_: Exception) {
            // Fallback for JVM/Robolectric test runners without hardware KeyStore provider
            getOrCreateFallbackSoftwareKey()
        }
    }

    private var fallbackSoftwareKey: SecretKey? = null

    @Synchronized
    private fun getOrCreateFallbackSoftwareKey(): SecretKey {
        if (fallbackSoftwareKey == null) {
            val keyBytes = ByteArray(32)
            secureRandom.nextBytes(keyBytes)
            fallbackSoftwareKey = SecretKeySpec(keyBytes, "AES")
        }
        return fallbackSoftwareKey!!
    }

    /**
     * Simulates an app reinstall or device migration in JVM tests by rotating the software fallback key.
     */
    @Synchronized
    fun rotateFallbackKeyForTesting() {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        fallbackSoftwareKey = SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext stream to output stream using AES-256-GCM.
     * Initializes encryption with the key so Android Keystore generates its own cryptographic IV,
     * then retrieves cipher.iv and persists it after MAGIC_HEADER.
     *
     * File Layout:
     * [Magic 4B: 'SALV'] + [IV 12B] + [Ciphertext + Tag 16B]
     */
    fun encryptStream(inputStream: InputStream, outputStream: OutputStream, key: SecretKey = getOrCreateMasterKey()) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        if (iv == null || iv.size != GCM_IV_LENGTH_BYTES) {
            throw IllegalStateException("Cipher failed to generate a valid $GCM_IV_LENGTH_BYTES-byte AES-GCM IV")
        }

        outputStream.write(MAGIC_HEADER)
        outputStream.write(iv)

        CipherOutputStream(outputStream, cipher).use { cipherOut ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                cipherOut.write(buffer, 0, bytesRead)
            }
            cipherOut.flush()
        }
    }

    /**
     * Decrypts an encrypted stream created by [encryptStream] back to plaintext.
     * Validates magic header (with backward-compatible fallback for headerless legacy [12B IV + Ciphertext] entries),
     * extracts stored IV, and decrypts with GCM tag verification.
     */
    fun decryptStream(inputStream: InputStream, outputStream: OutputStream, key: SecretKey = getOrCreateMasterKey()) {
        val prefix = ByteArray(MAGIC_HEADER.size)
        val prefixRead = inputStream.read(prefix)
        if (prefixRead != MAGIC_HEADER.size) {
            throw IllegalArgumentException("Invalid file format: encrypted stream is truncated")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        if (prefix.contentEquals(MAGIC_HEADER)) {
            val ivRead = inputStream.read(iv)
            if (ivRead != GCM_IV_LENGTH_BYTES) {
                throw IllegalArgumentException("Invalid file format: missing IV")
            }
        } else {
            // Backward compatibility for legacy headerless entries formatted as [12B IV] + [Ciphertext + Tag]
            System.arraycopy(prefix, 0, iv, 0, MAGIC_HEADER.size)
            val remainingIvBytes = GCM_IV_LENGTH_BYTES - MAGIC_HEADER.size
            val remRead = inputStream.read(iv, MAGIC_HEADER.size, remainingIvBytes)
            if (remRead != remainingIvBytes) {
                throw IllegalArgumentException("Invalid file format: missing SALV magic header or legacy IV")
            }
        }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        CipherInputStream(inputStream, cipher).use { cipherIn ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        }
    }

    /**
     * Decrypts an encrypted byte array directly in memory for fast preview.
     */
    fun decryptBytes(encryptedData: ByteArray, key: SecretKey = getOrCreateMasterKey()): ByteArray {
        val inputStream = ByteArrayInputStream(encryptedData)
        val outputStream = ByteArrayOutputStream()
        decryptStream(inputStream, outputStream, key)
        return outputStream.toByteArray()
    }

    /**
     * Encrypts plaintext bytes in memory.
     */
    fun encryptBytes(plainData: ByteArray, key: SecretKey = getOrCreateMasterKey()): ByteArray {
        val inputStream = ByteArrayInputStream(plainData)
        val outputStream = ByteArrayOutputStream()
        encryptStream(inputStream, outputStream, key)
        return outputStream.toByteArray()
    }

    /**
     * Derives a 256-bit AES key from a user passphrase using PBKDF2-HMAC-SHA256 with 100,000 iterations.
     */
    fun derivePassphraseKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, BACKUP_KDF_ITERATIONS, BACKUP_KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts data for portable container (.salvault) with passphrase key derivation.
     * Container format:
     * [SAL_BACKUP_V1 (13B)] + [Salt (32B)] + [IV (12B)] + [AES-256-GCM payload with Tag]
     */
    fun encryptWithPassphrase(
        inputStream: InputStream,
        outputStream: OutputStream,
        passphrase: CharArray
    ) {
        val salt = ByteArray(BACKUP_SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val derivedKey = derivePassphraseKey(passphrase, salt)

        outputStream.write(BACKUP_MAGIC_V1)
        outputStream.write(salt)
        outputStream.write(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        CipherOutputStream(outputStream, cipher).use { cipherOut ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                cipherOut.write(buffer, 0, bytesRead)
            }
            cipherOut.flush()
        }
    }

    /**
     * Decrypts portable container (.salvault) with passphrase.
     * Throws an exception if passphrase is wrong or file is tampered.
     */
    fun decryptWithPassphrase(
        inputStream: InputStream,
        outputStream: OutputStream,
        passphrase: CharArray
    ) {
        val magic = ByteArray(BACKUP_MAGIC_V1.size)
        val magicRead = inputStream.read(magic)
        if (magicRead != BACKUP_MAGIC_V1.size || !magic.contentEquals(BACKUP_MAGIC_V1)) {
            throw IllegalArgumentException("Invalid backup format: missing SAL_BACKUP_V1 header")
        }

        val salt = ByteArray(BACKUP_SALT_LENGTH)
        val saltRead = inputStream.read(salt)
        if (saltRead != BACKUP_SALT_LENGTH) {
            throw IllegalArgumentException("Corrupt backup: missing salt")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val ivRead = inputStream.read(iv)
        if (ivRead != GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Corrupt backup: missing IV")
        }

        val derivedKey = derivePassphraseKey(passphrase, salt)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        CipherInputStream(inputStream, cipher).use { cipherIn ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        }
    }
}
