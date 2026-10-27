package com.example.data.security

import android.util.Base64
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic utility providing salted hashing for PINs and security questions.
 * Never stores plain-text authentication secrets.
 *
 * Uses PBKDF2 with HMAC-SHA256 and cryptographically secure random salts.
 * Uses constant-time comparison (MessageDigest.isEqual) to defend against timing attacks.
 */
object CryptoUtils {

    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_SIZE_BYTES = 16

    /**
     * Generates a cryptographically strong random salt.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /**
     * Hashes a secret (PIN or security question answer) with the provided salt using PBKDF2.
     */
    fun hashWithSalt(secret: String, saltBase64: String): String {
        return try {
            val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
            val spec: KeySpec = PBEKeySpec(secret.toCharArray(), saltBytes, ITERATION_COUNT, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback for extreme environments without PBKDF2: Salted SHA-256
            fallbackSaltedSha256(secret, saltBase64)
        }
    }

    /**
     * Verifies a candidate secret against a stored hash and salt in constant time.
     */
    fun verify(candidate: String, storedSalt: String, storedHash: String): Boolean {
        if (candidate.isEmpty() || storedSalt.isEmpty() || storedHash.isEmpty()) {
            return false
        }
        val computedHash = hashWithSalt(candidate, storedSalt)
        val computedBytes = Base64.decode(computedHash, Base64.NO_WRAP)
        val storedBytes = Base64.decode(storedHash, Base64.NO_WRAP)
        return MessageDigest.isEqual(computedBytes, storedBytes)
    }

    private fun fallbackSaltedSha256(secret: String, saltBase64: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
        md.update(saltBytes)
        val digest = md.digest(secret.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
