package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized, reusable authentication dialog providing BiometricPrompt with PIN fallback.
 * Strictly action-scoped: authorizes only the specific invoking action and never leaks
 * authorization across unrelated sensitive flows.
 */
@Composable
fun AuthChallengeDialog(
    title: String,
    subtitle: String = "Use fingerprint or enter your master PIN to confirm",
    preferences: AppLockPreferences,
    onAuthorized: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val pinLength = preferences.getPinLength()

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(preferences.getRemainingLockoutSeconds()) }

    val isBiometricSupported = remember(activity) {
        activity != null && BiometricHelper.isBiometricAvailable(activity)
    }
    val isBiometricEnabled = isBiometricSupported && preferences.isBiometricEnabled()

    fun triggerBiometric() {
        if (activity != null && isBiometricEnabled && lockoutSeconds == 0) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = title,
                subtitle = subtitle,
                negativeButtonText = "Use PIN",
                onSuccess = {
                    onAuthorized()
                },
                onError = { _, _ ->
                    // Fall back to on-screen PIN gracefully
                }
            )
        }
    }

    // Automatically trigger biometric on first appearance if available and not locked out
    LaunchedEffect(Unit) {
        if (isBiometricEnabled && lockoutSeconds == 0) {
            delay(250L)
            triggerBiometric()
        }
    }

    // Cooldown countdown
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = preferences.getRemainingLockoutSeconds()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PinKeypad(
                    onDigitClick = { digit ->
                        if (lockoutSeconds > 0) return@PinKeypad
                        if (enteredPin.length < pinLength) {
                            val next = enteredPin + digit
                            enteredPin = next
                            isError = false
                            errorMsg = null

                            if (next.length == pinLength) {
                                scope.launch {
                                    if (preferences.verifyPin(next)) {
                                        preferences.resetFailedAttempts()
                                        onAuthorized()
                                    } else {
                                        isError = true
                                        preferences.recordFailedAttempt()
                                        lockoutSeconds = preferences.getRemainingLockoutSeconds()
                                        errorMsg = if (lockoutSeconds > 0) {
                                            "Locked out for $lockoutSeconds seconds"
                                        } else {
                                            "Incorrect PIN"
                                        }
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
                            IconButton(
                                onClick = { triggerBiometric() },
                                modifier = Modifier.testTag("auth_challenge_biometric_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Authenticate with Fingerprint",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("auth_challenge_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
