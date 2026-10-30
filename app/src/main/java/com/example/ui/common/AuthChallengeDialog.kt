package com.example.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.security.AppLockPreferences
import com.example.data.security.AppLockPreferences.AuthMethod
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized, reusable authentication dialog providing BiometricPrompt and dynamic
 * support for all 4 authentication methods (4-Digit PIN, 6-Digit PIN, Pattern Lock, Password).
 * Styled in Black & Gold Neomorphism.
 */
@Composable
fun AuthChallengeDialog(
    title: String,
    subtitle: String = "Use fingerprint or enter your master credential to confirm",
    preferences: AppLockPreferences,
    onAuthorized: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val authMethod = remember(preferences) { preferences.getAuthMethod() }
    val pinLength = when (authMethod) {
        AuthMethod.PIN_6 -> 6
        else -> preferences.getPinLength()
    }

    var enteredPin by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(preferences.getRemainingLockoutSeconds()) }

    val isBiometricSupported = remember(activity) {
        activity != null && BiometricHelper.isBiometricAvailable(activity)
    }
    val isBiometricEnabled = isBiometricSupported && preferences.isBiometricEnabled()

    val negativeBtnLabel = when (authMethod) {
        AuthMethod.PIN_4, AuthMethod.PIN_6 -> "Use PIN"
        AuthMethod.PATTERN -> "Use Pattern"
        AuthMethod.PASSWORD -> "Use Password"
    }

    fun triggerBiometric() {
        if (activity != null && isBiometricEnabled && lockoutSeconds == 0) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = title,
                subtitle = subtitle,
                negativeButtonText = negativeBtnLabel,
                onSuccess = {
                    preferences.resetFailedAttempts()
                    onAuthorized()
                },
                onError = { errorCode, _ ->
                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT ||
                        errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                    ) {
                        errorMsg = "Fingerprint sensor locked. Please use ${authMethod.displayName}."
                    }
                }
            )
        }
    }

    fun handleFailedAttempt(customIncorrectMessage: String) {
        isError = true
        preferences.recordFailedAttempt()
        lockoutSeconds = preferences.getRemainingLockoutSeconds()
        errorMsg = if (lockoutSeconds > 0) {
            "Locked out for $lockoutSeconds seconds"
        } else {
            customIncorrectMessage
        }
    }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && lockoutSeconds == 0) {
            delay(250L)
            triggerBiometric()
        }
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = preferences.getRemainingLockoutSeconds()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeoPalette.SurfaceDark,
        icon = {
            NeoGoldPadlockEmblem(badgeSize = 56.dp, isOpenShackle = false)
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                when (authMethod) {
                    AuthMethod.PIN_4, AuthMethod.PIN_6 -> {
                        PinDots(
                            pinLength = pinLength,
                            enteredLength = enteredPin.length,
                            isError = isError
                        )

                        if (errorMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.ErrorRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("auth_challenge_error_text")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        PinKeypad(
                            buttonSize = 62.dp,
                            onDigitClick = { digit ->
                                if (lockoutSeconds > 0) return@PinKeypad
                                if (enteredPin.length < pinLength) {
                                    val next = enteredPin + digit
                                    enteredPin = next
                                    isError = false
                                    errorMsg = null

                                    if (next.length == pinLength) {
                                        scope.launch {
                                            if (preferences.verifyCredential(next)) {
                                                preferences.resetFailedAttempts()
                                                onAuthorized()
                                            } else {
                                                handleFailedAttempt("Incorrect PIN")
                                                delay(300L)
                                                enteredPin = ""
                                            }
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (lockoutSeconds > 0) return@PinKeypad
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                    isError = false
                                    errorMsg = null
                                }
                            },
                            lockoutSeconds = lockoutSeconds,
                            extraLeftAction = {
                                if (isBiometricEnabled) {
                                    NeoCircularButton(
                                        onClick = { triggerBiometric() },
                                        size = 58.dp,
                                        isGoldSolid = true,
                                        modifier = Modifier.testTag("auth_challenge_biometric_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Authenticate with Fingerprint",
                                            tint = NeoPalette.TextInverse,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    AuthMethod.PATTERN -> {
                        if (errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.ErrorRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("auth_challenge_error_text")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        PatternLockGrid(
                            enabled = lockoutSeconds == 0,
                            isError = isError,
                            gridSize = 216.dp,
                            onPatternStart = {
                                isError = false
                                errorMsg = null
                            },
                            onPatternTooShort = {
                                isError = true
                                errorMsg = "Connect at least 4 dots"
                            },
                            onPatternComplete = { pattern ->
                                if (lockoutSeconds > 0) return@PatternLockGrid
                                if (preferences.verifyCredential(pattern)) {
                                    preferences.resetFailedAttempts()
                                    onAuthorized()
                                } else {
                                    handleFailedAttempt("Incorrect pattern")
                                }
                            }
                        )

                        if (isBiometricEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BiometricActionRow(
                                onBiometricClick = { triggerBiometric() },
                                enabled = lockoutSeconds == 0,
                                testTag = "auth_challenge_biometric_button"
                            )
                        }
                    }

                    AuthMethod.PASSWORD -> {
                        if (errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.ErrorRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("auth_challenge_error_text")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        PasswordAuthInput(
                            value = enteredPassword,
                            onValueChange = {
                                enteredPassword = it
                                isError = false
                                errorMsg = null
                            },
                            enabled = lockoutSeconds == 0,
                            isError = isError,
                            submitButtonText = "Verify Password",
                            onSubmit = {
                                if (lockoutSeconds > 0) return@PasswordAuthInput
                                if (enteredPassword.isEmpty()) {
                                    isError = true
                                    errorMsg = "Password cannot be empty"
                                    return@PasswordAuthInput
                                }
                                if (preferences.verifyCredential(enteredPassword)) {
                                    preferences.resetFailedAttempts()
                                    onAuthorized()
                                } else {
                                    handleFailedAttempt("Incorrect password")
                                    enteredPassword = ""
                                }
                            }
                        )

                        if (isBiometricEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BiometricActionRow(
                                onBiometricClick = { triggerBiometric() },
                                enabled = lockoutSeconds == 0,
                                testTag = "auth_challenge_biometric_button"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            NeoButton(
                text = "Cancel",
                shape = NeoShapes.FullPill,
                onClick = onDismiss,
                modifier = Modifier.testTag("auth_challenge_cancel_button")
            )
        }
    )
}
