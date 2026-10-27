package com.example.data.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Result container holding the generated salt and the resulting cryptographic hash.
 */
data class SaltedHash(
    val hash: String,
    val salt: String
)

/**
 * Secure utility class using [MessageDigest] (SHA-256) to salt, stretch, and hash
 * PINs before storing them locally, with constant-time verification.
 *
 * Security Principles:
 * 1. Cryptographically Secure Salt: Generated using [SecureRandom] so identical PINs
 *    produce completely different hashes, neutralizing rainbow-table attacks.
 * 2. Key Stretching: Applies multiple iterations of [MessageDigest] to increase
 *    the computational work required for brute-force attacks on short numeric PINs.
 * 3. Timing-Attack Resistance: Verifies candidate hashes using [MessageDigest.isEqual],
 *    ensuring comparisons take constant time regardless of how many bytes match.
 */
object PinHashUtil {

    private const val HASH_ALGORITHM = "SHA-256"
    private const val SALT_SIZE_BYTES = 16
    private const val STRETCHING_ITERATIONS = 5000

    /**
     * Generates a cryptographically strong random salt.
     *
     * @return Base64-encoded string representation of the random salt bytes.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_SIZE_BYTES)
        random.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    /**
     * Salts and hashes a new PIN with a freshly generated salt.
     * Use this when creating or changing a PIN.
     *
     * @param pin The raw numeric PIN entered by the user.
     * @return A [SaltedHash] containing both the Base64 salt and Base64 hash for storage.
     */
    fun createSaltedHash(pin: String): SaltedHash {
        require(pin.isNotEmpty()) { "PIN cannot be empty" }
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        return SaltedHash(hash = hash, salt = salt)
    }

    /**
     * Hashes a PIN using an existing salt and [MessageDigest].
     *
     * @param pin The plain-text PIN.
     * @param saltBase64 The Base64-encoded salt string.
     * @return Base64-encoded hash string.
     */
    fun hashPin(pin: String, saltBase64: String): String {
        require(pin.isNotEmpty()) { "PIN cannot be empty" }
        require(saltBase64.isNotEmpty()) { "Salt cannot be empty" }

        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
        val pinBytes = pin.toByteArray(Charsets.UTF_8)

        val digest = MessageDigest.getInstance(HASH_ALGORITHM)

        // Initial salted hash round: hash(salt + pin)
        digest.update(saltBytes)
        digest.update(pinBytes)
        var currentHash = digest.digest()

        // Key stretching iterations: repeatedly hash(salt + previousHash + pin)
        // This slows down offline brute-force attempts on 4-6 digit numeric PINs.
        for (i in 1 until STRETCHING_ITERATIONS) {
            digest.reset()
            digest.update(saltBytes)
            digest.update(currentHash)
            digest.update(pinBytes)
            currentHash = digest.digest()
        }

        return Base64.encodeToString(currentHash, Base64.NO_WRAP)
    }

    /**
     * Verifies an entered PIN against a stored salt and hash in constant time.
     *
     * @param inputPin The PIN entered by the user attempting to authenticate.
     * @param storedSalt The Base64-encoded salt previously stored for the user.
     * @param storedHash The Base64-encoded hash previously stored for the user.
     * @return `true` if the input PIN produces an identical hash; `false` otherwise.
     */
    fun verifyPin(inputPin: String, storedSalt: String, storedHash: String): Boolean {
        if (inputPin.isEmpty() || storedSalt.isEmpty() || storedHash.isEmpty()) {
            return false
        }

        return try {
            val candidateHash = hashPin(inputPin, storedSalt)
            val candidateBytes = Base64.decode(candidateHash, Base64.NO_WRAP)
            val storedBytes = Base64.decode(storedHash, Base64.NO_WRAP)

            // MessageDigest.isEqual performs constant-time comparison to prevent timing attacks
            MessageDigest.isEqual(candidateBytes, storedBytes)
        } catch (_: Exception) {
            false
        }
    }
}
