package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Utility class wrapping AndroidX BiometricPrompt API for fingerprint/face authentication.
 * Guarantees:
 * - Atomic single-success execution: [onSuccess] is invoked at most once per prompt lifecycle.
 * - Concurrency protection: duplicate trigger attempts while a prompt is already active are ignored.
 * - Post-success dismissal error suppression: system cancellation codes (e.g. ERROR_CANCELED)
 *   emitted during dialog dismissal do not trigger failure handlers or error banners.
 * - Safe cancellation: provides [cancelCurrentPrompt] to clean up on activity/dialog disposal.
 */
object BiometricHelper {

    private val authLock = Any()

    @Volatile
    private var activePrompt: BiometricPrompt? = null

    @Volatile
    private var isAuthenticating: Boolean = false

    /**
     * Checks if biometric hardware is present, enabled, and has enrolled credentials.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            canAuth == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if device hardware supports fingerprint authentication.
     */
    fun hasFingerprintHardware(context: Context): Boolean {
        return try {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if device hardware supports face authentication.
     */
    fun hasFaceHardware(context: Context): Boolean {
        return try {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FACE)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if fingerprint authentication is available and enrolled.
     * Checks hardware feature and FingerprintManagerCompat enrollment.
     */
    @Suppress("DEPRECATION")
    fun isFingerprintAvailableAndEnrolled(context: Context): Boolean {
        if (!hasFingerprintHardware(context)) return false
        return try {
            val fpCompat = androidx.core.hardware.fingerprint.FingerprintManagerCompat.from(context)
            fpCompat.isHardwareDetected && fpCompat.hasEnrolledFingerprints()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if face authentication hardware is supported and available on this device.
     *
     * Note on Android platform architecture:
     * Android BiometricPrompt aggregates all biometrics (fingerprint, face, iris) into unified authenticators.
     * Android does not expose modality-specific callbacks to third-party applications on authentication.
     */
    fun isFaceSupportedAndAvailable(context: Context): Boolean {
        if (!hasFaceHardware(context)) return false
        return isBiometricAvailable(context)
    }

    fun isFaceAvailableAndEnrolled(context: Context): Boolean {
        return isFaceSupportedAndAvailable(context)
    }

    /**
     * Discloses whether the device environment can guarantee separate modality enforcement.
     * Always returns false on standard Android platforms because BiometricPrompt aggregates
     * all enrolled biometrics into a single system authentication dialog.
     */
    fun canEnforceSeparateBiometricModality(): Boolean = false

    /**
     * Cancels any currently active system BiometricPrompt without firing error callbacks.
     */
    fun cancelCurrentPrompt() {
        synchronized(authLock) {
            try {
                activePrompt?.cancelAuthentication()
            } catch (_: Exception) {
            }
            activePrompt = null
            isAuthenticating = false
        }
    }

    /**
     * Displays the system BiometricPrompt dialog.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Unlock with Fingerprint",
        subtitle: String = "Touch the fingerprint sensor to unlock",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {},
        onCanceled: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        synchronized(authLock) {
            if (isAuthenticating) {
                // A prompt is already actively showing; prevent duplicate prompt dialogs
                return
            }
            isAuthenticating = true
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val isConsumed = AtomicBoolean(false)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (isConsumed.compareAndSet(false, true)) {
                    synchronized(authLock) {
                        isAuthenticating = false
                        activePrompt = null
                    }
                    onSuccess()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (isConsumed.get()) {
                    // Success callback already fired; ignore dismissal cancellation code from system
                    synchronized(authLock) {
                        isAuthenticating = false
                        activePrompt = null
                    }
                    return
                }

                synchronized(authLock) {
                    isAuthenticating = false
                    activePrompt = null
                }

                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onCanceled()
                } else {
                    onError(errorCode, errString)
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                if (!isConsumed.get()) {
                    onFailed()
                }
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            synchronized(authLock) {
                activePrompt = biometricPrompt
            }
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {
            synchronized(authLock) {
                isAuthenticating = false
                activePrompt = null
            }
            onCanceled()
        }
    }
}
