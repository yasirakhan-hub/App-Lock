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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.repository.AppDiscoveryRepository
import com.example.data.repository.AppLockManager
import com.example.data.security.AppLockPreferences.AuthMethod
import com.example.ui.common.BiometricActionRow
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoGoldPadlockEmblem
import com.example.ui.common.NeoInsetBox
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.ui.common.PasswordAuthInput
import com.example.ui.common.PatternLockGrid
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.ui.theme.AppLockTheme
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen lock screen activity displayed over protected applications.
 * Styled in Black & Gold Neomorphism matching Panel 2 & Panel 10.
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
            AppLockTheme(darkTheme = true) {
                LockScreen(
                    targetAppName = appName,
                    targetAppIcon = appIcon,
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
        isTestMode: Boolean,
        onUnlockSuccess: () -> Unit,
        onSafeExit: () -> Unit
    ) {
        BackHandler {
            onSafeExit()
        }

        val prefs = appLockManager.preferences
        val authMethod = remember { prefs.getAuthMethod() }
        val pinLength = when (authMethod) {
            AuthMethod.PIN_6 -> 6
            else -> prefs.getPinLength()
        }
        val scope = rememberCoroutineScope()

        var enteredPin by remember { mutableStateOf("") }
        var enteredPassword by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var lockoutSeconds by remember { mutableIntStateOf(prefs.getRemainingLockoutSeconds()) }
        var showRecoveryDialog by remember { mutableStateOf(false) }

        val isBiometricSupported = remember {
            BiometricHelper.isBiometricAvailable(this@LockActivity)
        }
        val isBiometricEnabled = prefs.isBiometricEnabled() && isBiometricSupported

        val negativeBtnText = when (authMethod) {
            AuthMethod.PIN_4, AuthMethod.PIN_6 -> "Use PIN"
            AuthMethod.PATTERN -> "Use Pattern"
            AuthMethod.PASSWORD -> "Use Password"
        }

        fun triggerBiometricPrompt() {
            if (isBiometricEnabled && lockoutSeconds == 0) {
                BiometricHelper.showBiometricPrompt(
                    activity = this@LockActivity,
                    title = "Unlock $targetAppName",
                    subtitle = "Touch the fingerprint sensor to unlock",
                    negativeButtonText = negativeBtnText,
                    onSuccess = {
                        prefs.resetFailedAttempts()
                        onUnlockSuccess()
                    },
                    onError = { errorCode, _ ->
                        if (errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                        ) {
                            errorMessage = "Fingerprint sensor locked. Please use ${authMethod.displayName}."
                        }
                    }
                )
            }
        }

        fun recordFailure(label: String) {
            isError = true
            prefs.recordFailedAttempt()
            lockoutSeconds = prefs.getRemainingLockoutSeconds()
            val remaining = prefs.getFailedAttempts()
            errorMessage = if (lockoutSeconds > 0) {
                "Locked out for $lockoutSeconds seconds"
            } else {
                "Incorrect $label. Attempt $remaining of 5"
            }
        }

        LaunchedEffect(Unit) {
            if (isBiometricEnabled && lockoutSeconds == 0) {
                delay(300L)
                triggerBiometricPrompt()
            }
        }

        LaunchedEffect(lockoutSeconds) {
            if (lockoutSeconds > 0) {
                delay(1000L)
                lockoutSeconds = prefs.getRemainingLockoutSeconds()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = NeoPalette.BackgroundBase
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF171306),
                                NeoPalette.BackgroundBase
                            )
                        )
                    )
                    .padding(innerPadding)
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestMode) {
                        NeoStatusBadge(
                            text = "This is a simulated lock screen",
                            color = NeoPalette.PrimaryGold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    if (targetAppIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF2D2309), Color(0xFF141316))
                                    )
                                )
                                .border(1.5.dp, NeoPalette.PrimaryGold.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = targetAppIcon,
                                contentDescription = targetAppName,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    } else {
                        NeoGoldPadlockEmblem(
                            badgeSize = 82.dp,
                            isOpenShackle = false
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when (authMethod) {
                            AuthMethod.PIN_4 -> "Enter 4-Digit PIN"
                            AuthMethod.PIN_6 -> "Enter 6-Digit PIN"
                            AuthMethod.PATTERN -> "Draw Pattern"
                            AuthMethod.PASSWORD -> "Enter Password"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = targetAppName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeoPalette.TextSecondary
                    )

                    if (authMethod == AuthMethod.PIN_4 || authMethod == AuthMethod.PIN_6) {
                        Spacer(modifier = Modifier.height(20.dp))

                        PinDots(
                            pinLength = pinLength,
                            enteredLength = enteredPin.length,
                            isError = isError
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoPalette.ErrorRed,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("lock_error_message")
                        )
                    }
                }

                when (authMethod) {
                    AuthMethod.PIN_4, AuthMethod.PIN_6 -> {
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
                                            val isCorrect = prefs.verifyCredential(newPin)
                                            if (isCorrect) {
                                                prefs.resetFailedAttempts()
                                                onUnlockSuccess()
                                            } else {
                                                recordFailure("PIN")
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
                                    NeoCircularButton(
                                        onClick = { triggerBiometricPrompt() },
                                        size = 70.dp,
                                        isGoldSolid = true,
                                        modifier = Modifier.testTag("biometric_trigger_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Unlock with Fingerprint",
                                            tint = NeoPalette.TextInverse,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                } else if (prefs.hasRecoveryQuestion()) {
                                    NeoCircularButton(
                                        onClick = { showRecoveryDialog = true },
                                        size = 70.dp,
                                        modifier = Modifier.testTag("forgot_pin_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HelpOutline,
                                            contentDescription = "Forgot PIN",
                                            tint = NeoPalette.PrimaryGold,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    AuthMethod.PATTERN -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PatternLockGrid(
                                enabled = lockoutSeconds == 0,
                                isError = isError,
                                gridSize = 240.dp,
                                onPatternStart = {
                                    isError = false
                                    errorMessage = null
                                },
                                onPatternTooShort = {
                                    isError = true
                                    errorMessage = "Connect at least 4 dots"
                                },
                                onPatternComplete = { pattern ->
                                    if (lockoutSeconds > 0) return@PatternLockGrid
                                    if (prefs.verifyCredential(pattern)) {
                                        prefs.resetFailedAttempts()
                                        onUnlockSuccess()
                                    } else {
                                        recordFailure("pattern")
                                    }
                                }
                            )

                            if (isBiometricEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                BiometricActionRow(
                                    onBiometricClick = { triggerBiometricPrompt() },
                                    enabled = lockoutSeconds == 0,
                                    testTag = "biometric_trigger_button"
                                )
                            } else if (prefs.hasRecoveryQuestion()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { showRecoveryDialog = true },
                                    modifier = Modifier.testTag("forgot_pin_button")
                                ) {
                                    Text(
                                        text = "Forgot Pattern?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeoPalette.PrimaryGold
                                    )
                                }
                            }
                        }
                    }

                    AuthMethod.PASSWORD -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PasswordAuthInput(
                                value = enteredPassword,
                                onValueChange = {
                                    enteredPassword = it
                                    isError = false
                                    errorMessage = null
                                },
                                enabled = lockoutSeconds == 0,
                                isError = isError,
                                submitButtonText = "Unlock",
                                onSubmit = {
                                    if (lockoutSeconds > 0) return@PasswordAuthInput
                                    if (enteredPassword.isEmpty()) {
                                        isError = true
                                        errorMessage = "Password cannot be empty"
                                        return@PasswordAuthInput
                                    }
                                    if (prefs.verifyCredential(enteredPassword)) {
                                        prefs.resetFailedAttempts()
                                        onUnlockSuccess()
                                    } else {
                                        recordFailure("password")
                                        enteredPassword = ""
                                    }
                                }
                            )

                            if (isBiometricEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                BiometricActionRow(
                                    onBiometricClick = { triggerBiometricPrompt() },
                                    enabled = lockoutSeconds == 0,
                                    testTag = "biometric_trigger_button"
                                )
                            } else if (prefs.hasRecoveryQuestion()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { showRecoveryDialog = true },
                                    modifier = Modifier.testTag("forgot_pin_button")
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeoPalette.PrimaryGold
                                    )
                                }
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBiometricEnabled && prefs.hasRecoveryQuestion()) {
                        TextButton(
                            onClick = { showRecoveryDialog = true },
                            modifier = Modifier.testTag("forgot_pin_link_button")
                        ) {
                            Text(
                                text = "Forgot ${authMethod.displayName}?",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.PrimaryGold
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
                            color = NeoPalette.TextSecondary
                        )
                    }
                }
            }

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
            containerColor = NeoPalette.SurfaceDark,
            title = {
                Text(
                    text = "Forgot PIN?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Answer your security question to unlock without revealing your existing PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeoPalette.TextSecondary
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
                            color = NeoPalette.TextPrimary
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
                            color = NeoPalette.ErrorRed
                        )
                    }
                }
            },
            confirmButton = {
                NeoButton(
                    text = "Unlock",
                    isPrimaryGlowing = true,
                    shape = NeoShapes.FullPill,
                    onClick = {
                        if (!onVerifyAnswer(answerText)) {
                            error = "Incorrect answer. Please check spelling."
                        }
                    },
                    modifier = Modifier.testTag("recovery_submit_button")
                )
            },
            dismissButton = {
                NeoButton(
                    text = "Cancel",
                    shape = NeoShapes.FullPill,
                    onClick = onDismiss
                )
            }
        )
    }
}
