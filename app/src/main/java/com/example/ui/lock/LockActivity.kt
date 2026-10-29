package com.example.ui.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoInsetBox
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.repository.AppDiscoveryRepository
import com.example.data.repository.AppLockManager
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.ui.theme.AppLockTheme
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen lock screen activity displayed over protected applications.
 * Supports both numeric PIN authentication and BiometricPrompt (Fingerprint) unlock.
 */
class LockActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_CLASS = "extra_target_class"
        const val EXTRA_IS_TEST_MODE = "extra_is_test_mode"
        const val EXTRA_ACTION_DEACTIVATE_ADMIN = "extra_action_deactivate_admin"
    }

    private lateinit var appLockManager: AppLockManager
    private lateinit var appDiscoveryRepository: AppDiscoveryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLockManager = AppLockManager.getInstance(this)
        appDiscoveryRepository = AppDiscoveryRepository(this)
        appLockManager.isLockActivityActive = true

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        val targetClass = intent.getStringExtra(EXTRA_TARGET_CLASS)
        val isTestMode = intent.getBooleanExtra(EXTRA_IS_TEST_MODE, false)
        val isDeactivateAdmin = intent.getBooleanExtra(EXTRA_ACTION_DEACTIVATE_ADMIN, false)

        if (!isTestMode && !isDeactivateAdmin && targetPackage.isNotBlank()) {
            appLockManager.storeInterceptedTarget(targetPackage, targetClass)
        }

        val appName = if (isDeactivateAdmin) {
            "Deactivate Device Administrator"
        } else if (targetPackage.isNotBlank()) {
            appDiscoveryRepository.getAppName(targetPackage)
        } else {
            "Protected Application"
        }
        val appIcon = if (targetPackage.isNotBlank()) {
            appDiscoveryRepository.getAppIcon(targetPackage)
        } else null

        setContent {
            AppLockTheme {
                LockScreen(
                    targetAppName = appName,
                    targetAppIcon = appIcon,
                    targetPackage = targetPackage,
                    isTestMode = isTestMode,
                    onUnlockSuccess = {
                        if (!isTestMode && targetPackage.isNotBlank()) {
                            appLockManager.onPackageUnlocked(targetPackage)
                            appLockManager.launchInterceptedTarget(this@LockActivity, targetPackage)
                        }
                        finish()
                    },
                    onSafeExit = {
                        exitToHomeScreen()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        appLockManager.isLockActivityActive = false
        super.onDestroy()
    }

    /**
     * Prevents bypassing the lock: when user presses Back, redirect to Android Home screen.
     */
    private fun exitToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    @Composable
    private fun LockScreen(
        targetAppName: String,
        targetAppIcon: ImageBitmap?,
        targetPackage: String,
        isTestMode: Boolean,
        onUnlockSuccess: () -> Unit,
        onSafeExit: () -> Unit
    ) {
        BackHandler {
            onSafeExit()
        }

        val prefs = appLockManager.preferences
        val pinLength = prefs.getPinLength()
        val scope = rememberCoroutineScope()

        var enteredPin by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var lockoutSeconds by remember { mutableIntStateOf(prefs.getRemainingLockoutSeconds()) }
        var showRecoveryDialog by remember { mutableStateOf(false) }

        val isBiometricSupported = remember {
            BiometricHelper.isBiometricAvailable(this@LockActivity)
        }
        val isBiometricEnabled = prefs.isBiometricEnabled() && isBiometricSupported

        // Function to launch BiometricPrompt
        fun triggerBiometricPrompt() {
            if (isBiometricEnabled && lockoutSeconds == 0) {
                BiometricHelper.showBiometricPrompt(
                    activity = this@LockActivity,
                    title = "Unlock $targetAppName",
                    subtitle = "Touch the fingerprint sensor to unlock",
                    negativeButtonText = "Use PIN",
                    onSuccess = {
                        onUnlockSuccess()
                    },
                    onError = { errorCode, _ ->
                        if (errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                        ) {
                            errorMessage = "Fingerprint sensor locked. Please use PIN."
                        }
                    }
                )
            }
        }

        // Automatically trigger BiometricPrompt on screen appearance if enabled
        LaunchedEffect(Unit) {
            if (isBiometricEnabled && lockoutSeconds == 0) {
                delay(300L)
                triggerBiometricPrompt()
            }
        }

        // Cooldown timer ticker
        LaunchedEffect(lockoutSeconds) {
            if (lockoutSeconds > 0) {
                delay(1000L)
                lockoutSeconds = prefs.getRemainingLockoutSeconds()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header: App Identity & Lock Badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

                    if (isTestMode) {
                        NeoStatusBadge(
                            text = "SIMULATION TEST MODE",
                            color = NeoPalette.PrimaryCyan,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isDarkTheme) {
                                        listOf(NeoPalette.SurfaceLight, NeoPalette.SurfaceDark)
                                    } else {
                                        listOf(Color.White, MaterialTheme.colorScheme.surfaceVariant)
                                    }
                                )
                            )
                            .border(
                                1.5.dp,
                                if (isDarkTheme) NeoPalette.SpecularBorder else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (targetAppIcon != null) {
                            Image(
                                bitmap = targetAppIcon,
                                contentDescription = targetAppName,
                                modifier = Modifier.size(52.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_applock_shield_logo),
                                contentDescription = "Locked",
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = targetAppName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isBiometricEnabled) "Use fingerprint or enter PIN" else "Enter PIN to access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // PIN Dots
                    PinDots(
                        pinLength = pinLength,
                        enteredLength = enteredPin.length,
                        isError = isError
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Center/Bottom Keypad
                PinKeypad(
                    onDigitClick = { digit ->
                        if (lockoutSeconds > 0) return@PinKeypad
                        if (enteredPin.length < pinLength) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            isError = false
                            errorMessage = null

                            if (newPin.length == pinLength) {
                                scope.launch {
                                    val isCorrect = prefs.verifyPin(newPin)
                                    if (isCorrect) {
                                        onUnlockSuccess()
                                    } else {
                                        isError = true
                                        prefs.recordFailedAttempt()
                                        lockoutSeconds = prefs.getRemainingLockoutSeconds()
                                        val remaining = prefs.getFailedAttempts()
                                        errorMessage = if (lockoutSeconds > 0) {
                                            "Locked out for $lockoutSeconds seconds"
                                        } else {
                                            "Incorrect PIN. Attempt $remaining of 5"
                                        }
                                        delay(400L)
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
                            errorMessage = null
                        }
                    },
                    lockoutSeconds = lockoutSeconds,
                    extraLeftAction = {
                        if (isBiometricEnabled) {
                            // Fingerprint button to re-trigger BiometricPrompt
                            NeoCircularButton(
                                onClick = { triggerBiometricPrompt() },
                                size = 60.dp,
                                modifier = Modifier.testTag("biometric_trigger_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Unlock with Fingerprint",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        } else if (prefs.hasRecoveryQuestion()) {
                            NeoCircularButton(
                                onClick = { showRecoveryDialog = true },
                                size = 60.dp,
                                modifier = Modifier.testTag("forgot_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "Forgot PIN",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                )

                // Bottom Actions: Forgot PIN link & Cancel
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBiometricEnabled && prefs.hasRecoveryQuestion()) {
                        TextButton(
                            onClick = { showRecoveryDialog = true },
                            modifier = Modifier.testTag("forgot_pin_link_button")
                        ) {
                            Text(
                                text = "Forgot PIN?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    TextButton(
                        onClick = onSafeExit,
                        modifier = Modifier.testTag("exit_to_home_button")
                    ) {
                        Text(
                            text = "Cancel & Return to Home",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recovery Dialog
            if (showRecoveryDialog) {
                RecoveryDialog(
                    securityQuestion = prefs.getSecurityQuestion() ?: "Security Question",
                    onDismiss = { showRecoveryDialog = false },
                    onVerifyAnswer = { answer ->
                        if (prefs.verifyRecoveryAnswer(answer)) {
                            showRecoveryDialog = false
                            onUnlockSuccess()
                            true
                        } else {
                            false
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun RecoveryDialog(
        securityQuestion: String,
        onDismiss: () -> Unit,
        onVerifyAnswer: (String) -> Boolean
    ) {
        var answerText by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Forgot PIN?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Answer your security question to unlock without revealing your existing PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoInsetBox(
                        shape = NeoShapes.SmallControl,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = securityQuestion,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = {
                            answerText = it
                            error = null
                        },
                        label = { Text("Your Answer") },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_answer_input")
                    )
                    if (error != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                NeoButton(
                    text = "Unlock",
                    isPrimaryGlowing = true,
                    shape = NeoShapes.SmallControl,
                    onClick = {
                        if (onVerifyAnswer(answerText)) {
                            // Verified
                        } else {
                            error = "Incorrect answer. Please check spelling."
                        }
                    },
                    modifier = Modifier.testTag("recovery_submit_button")
                )
            },
            dismissButton = {
                NeoButton(
                    text = "Cancel",
                    shape = NeoShapes.SmallControl,
                    onClick = onDismiss
                )
            }
        )
    }
}
