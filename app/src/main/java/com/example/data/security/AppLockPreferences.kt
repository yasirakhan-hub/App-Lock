package com.example.data.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages local persistence for AppLock configuration, hashes, biometric preferences, and protected packages.
 * No personal data or plaintext credentials are ever stored.
 */
class AppLockPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory cache of protected package names to eliminate SharedPreferences XML/map queries
    // on every AccessibilityEvent foreground window state check.
    @Volatile
    private var cachedProtectedPackages: Set<String>? = null

    companion object {
        private const val PREFS_NAME = "secure_applock_prefs"

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"

        private const val KEY_QUESTION = "security_question"
        private const val KEY_ANSWER_HASH = "answer_hash"
        private const val KEY_ANSWER_SALT = "answer_salt"

        private const val KEY_PROTECTED_PACKAGES = "protected_packages"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_RELOCK_ON_SCREEN_OFF = "relock_on_screen_off"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DARK_THEME_OVERRIDE = "dark_theme_override"

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_TIMESTAMP = "lockout_until_timestamp"

        private const val KEY_CALCULATOR_DISGUISE_ENABLED = "calculator_disguise_enabled"
        private const val KEY_CALC_SECRET_HASH = "calc_secret_hash"
        private const val KEY_CALC_SECRET_SALT = "calc_secret_salt"
        private const val KEY_CALC_SECRET_LENGTH = "calc_secret_length"

        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    }

    /**
     * Checks if initial PIN setup has been completed.
     */
    fun isPinConfigured(): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null)
        val salt = prefs.getString(KEY_PIN_SALT, null)
        return !hash.isNullOrEmpty() && !salt.isNullOrEmpty()
    }

    /**
     * Saves a new PIN securely with a freshly generated salt and MessageDigest hash.
     */
    fun savePin(pin: String) {
        val saltedHash = PinHashUtil.createSaltedHash(pin)
        prefs.edit()
            .putString(KEY_PIN_SALT, saltedHash.salt)
            .putString(KEY_PIN_HASH, saltedHash.hash)
            .putInt(KEY_PIN_LENGTH, pin.length)
            .apply()
    }

    /**
     * Returns expected PIN length (usually 4 or 6, defaults to 4).
     */
    fun getPinLength(): Int {
        return prefs.getInt(KEY_PIN_LENGTH, 4)
    }

    /**
     * Verifies the input PIN against the stored MessageDigest salted hash.
     */
    fun verifyPin(candidatePin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        return PinHashUtil.verifyPin(candidatePin, salt, hash)
    }

    /**
     * Biometric unlock preference (defaults to true).
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Gets the user's manual dark theme preference, or null if following system default.
     */
    fun isDarkThemeOverride(): Boolean? {
        if (!prefs.contains(KEY_DARK_THEME_OVERRIDE)) return null
        return prefs.getBoolean(KEY_DARK_THEME_OVERRIDE, false)
    }

    fun setDarkThemeOverride(isDark: Boolean?) {
        if (isDark == null) {
            prefs.edit().remove(KEY_DARK_THEME_OVERRIDE).apply()
        } else {
            prefs.edit().putBoolean(KEY_DARK_THEME_OVERRIDE, isDark).apply()
        }
    }

    /**
     * Saves a security recovery question and salted answer hash.
     */
    fun saveRecoveryQuestion(question: String, answer: String) {
        val normalizedAnswer = answer.trim().lowercase()
        val saltedHash = PinHashUtil.createSaltedHash(normalizedAnswer)
        prefs.edit()
            .putString(KEY_QUESTION, question.trim())
            .putString(KEY_ANSWER_SALT, saltedHash.salt)
            .putString(KEY_ANSWER_HASH, saltedHash.hash)
            .apply()
    }

    fun hasRecoveryQuestion(): Boolean {
        val q = prefs.getString(KEY_QUESTION, null)
        val h = prefs.getString(KEY_ANSWER_HASH, null)
        return !q.isNullOrBlank() && !h.isNullOrBlank()
    }

    fun getSecurityQuestion(): String? {
        return prefs.getString(KEY_QUESTION, null)
    }

    /**
     * Verifies the recovery answer using MessageDigest constant-time comparison.
     */
    fun verifyRecoveryAnswer(answer: String): Boolean {
        val normalizedAnswer = answer.trim().lowercase()
        val salt = prefs.getString(KEY_ANSWER_SALT, null) ?: return false
        val hash = prefs.getString(KEY_ANSWER_HASH, null) ?: return false
        return PinHashUtil.verifyPin(normalizedAnswer, salt, hash)
    }

    /**
     * Set of package names protected by AppLock.
     * Caches protected packages in memory for sub-microsecond lookups during window transition events.
     */
    fun getProtectedPackages(): Set<String> {
        return cachedProtectedPackages ?: synchronized(this) {
            cachedProtectedPackages ?: (prefs.getStringSet(KEY_PROTECTED_PACKAGES, emptySet()) ?: emptySet()).also {
                cachedProtectedPackages = it
            }
        }
    }

    fun setProtectedPackages(packages: Set<String>) {
        val newSet = HashSet(packages)
        synchronized(this) {
            cachedProtectedPackages = newSet
        }
        prefs.edit()
            .putStringSet(KEY_PROTECTED_PACKAGES, newSet)
            .apply()
    }

    fun isPackageProtected(packageName: String): Boolean {
        if (!isProtectionEnabled()) return false
        val set = getProtectedPackages()
        return set.contains(packageName)
    }

    fun setPackageProtected(packageName: String, isProtected: Boolean) {
        val current = HashSet(getProtectedPackages())
        if (isProtected) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        setProtectedPackages(current)
    }

    /**
     * Master toggle for app protection.
     */
    fun isProtectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
    }

    fun setProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }

    /**
     * Relock policy:
     * true = relock when screen turns off (grace period while device is on)
     * false = relock immediately every time user exits the app
     */
    fun isRelockOnScreenOff(): Boolean {
        return prefs.getBoolean(KEY_RELOCK_ON_SCREEN_OFF, false)
    }

    fun setRelockOnScreenOff(relockOnScreenOff: Boolean) {
        prefs.edit().putBoolean(KEY_RELOCK_ON_SCREEN_OFF, relockOnScreenOff).apply()
    }

    // --- Brute force lockout management ---

    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun recordFailedAttempt(): Long {
        val attempts = getFailedAttempts() + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        var lockoutUntil = 0L
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            editor.putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, lockoutUntil)
        }
        editor.apply()
        return lockoutUntil
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .apply()
    }

    /**
     * Returns remaining lockout seconds, or 0 if not locked out.
     */
    fun getRemainingLockoutSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            (((lockoutUntil - now) + 999) / 1000).toInt()
        } else {
            0
        }
    }

    // --- Calculator Disguise & Secret Gateway ---

    /**
     * Checks whether a secret sequence has been configured for the Calculator Disguise.
     */
    fun isCalculatorSecretConfigured(): Boolean {
        val hash = prefs.getString(KEY_CALC_SECRET_HASH, null)
        val salt = prefs.getString(KEY_CALC_SECRET_SALT, null)
        return !hash.isNullOrEmpty() && !salt.isNullOrEmpty()
    }

    /**
     * Saves the numeric secret sequence (4–8 digits) as a salted SHA-256 hash.
     * Never stores plaintext secret sequences.
     */
    fun saveCalculatorSecretSequence(sequenceDigits: String) {
        val trimmed = sequenceDigits.trim()
        require(trimmed.length in 4..8 && trimmed.all { it.isDigit() }) {
            "Secret sequence must be 4 to 8 digits"
        }
        val saltedHash = PinHashUtil.createSaltedHash(trimmed)
        prefs.edit()
            .putString(KEY_CALC_SECRET_SALT, saltedHash.salt)
            .putString(KEY_CALC_SECRET_HASH, saltedHash.hash)
            .putInt(KEY_CALC_SECRET_LENGTH, trimmed.length)
            .apply()
    }

    fun getCalculatorSecretLength(): Int {
        return prefs.getInt(KEY_CALC_SECRET_LENGTH, 4)
    }

    /**
     * Verifies candidate digits against the stored calculator secret hash using constant-time comparison.
     */
    fun verifyCalculatorSecretSequence(candidateDigits: String): Boolean {
        if (candidateDigits.isEmpty()) return false
        val hash = prefs.getString(KEY_CALC_SECRET_HASH, null) ?: return false
        val salt = prefs.getString(KEY_CALC_SECRET_SALT, null) ?: return false
        return PinHashUtil.verifyPin(candidateDigits, salt, hash)
    }

    /**
     * Returns true only if Calculator Disguise is enabled AND a secret sequence is configured.
     */
    fun isCalculatorDisguiseEnabled(): Boolean {
        return prefs.getBoolean(KEY_CALCULATOR_DISGUISE_ENABLED, false) && isCalculatorSecretConfigured()
    }

    fun setCalculatorDisguiseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALCULATOR_DISGUISE_ENABLED, enabled).apply()
    }

    /**
     * Resets all AppLock data to fresh state.
     */
    fun clearAllData() {
        synchronized(this) {
            cachedProtectedPackages = null
        }
        prefs.edit().clear().apply()
    }
}
