package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.data.repository.AppLockManager
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.ui.screens.AppSelectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TestLockScreen
import com.example.ui.theme.AppLockTheme
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ScreenState {
    AUTH_GATE,
    ONBOARDING,
    DASHBOARD,
    APP_SELECTION,
    SETTINGS,
    TEST_LOCK
}

class MainActivity : FragmentActivity() {

    private lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLockManager = AppLockManager.getInstance(this)

        setContent {
            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            var darkThemeOverride by remember {
                mutableStateOf(appLockManager.preferences.isDarkThemeOverride())
            }
            val isDarkTheme = darkThemeOverride ?: systemInDark

            AppLockTheme(darkTheme = isDarkTheme) {
                MainAppNavHost(
                    appLockManager = appLockManager,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        val next = !isDarkTheme
                        darkThemeOverride = next
                        appLockManager.preferences.setDarkThemeOverride(next)
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppNavHost(
    appLockManager: AppLockManager,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val prefs = appLockManager.preferences
    var currentScreen by remember {
        mutableStateOf(
            if (!prefs.isPinConfigured()) {
                ScreenState.ONBOARDING
            } else {
                ScreenState.AUTH_GATE
            }
        )
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppScreenTransition"
    ) { screen ->
        when (screen) {
            ScreenState.AUTH_GATE -> {
                DashboardAuthGate(
                    appLockManager = appLockManager,
                    onUnlockSuccess = { currentScreen = ScreenState.DASHBOARD }
                )
            }

            ScreenState.ONBOARDING -> {
                OnboardingSetupScreen(
                    preferences = prefs,
                    onSetupComplete = { currentScreen = ScreenState.DASHBOARD }
                )
            }

            ScreenState.DASHBOARD -> {
                DashboardScreen(
                    appLockManager = appLockManager,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToAppSelection = { currentScreen = ScreenState.APP_SELECTION },
                    onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                    onNavigateToTestLock = { currentScreen = ScreenState.TEST_LOCK }
                )
            }

            ScreenState.APP_SELECTION -> {
                BackHandler { currentScreen = ScreenState.DASHBOARD }
                AppSelectionScreen(
                    preferences = prefs,
                    onNavigateBack = { currentScreen = ScreenState.DASHBOARD }
                )
            }

            ScreenState.SETTINGS -> {
                BackHandler { currentScreen = ScreenState.DASHBOARD }
                SettingsScreen(
                    preferences = prefs,
                    onNavigateBack = { currentScreen = ScreenState.DASHBOARD },
                    onResetCompleted = { currentScreen = ScreenState.ONBOARDING }
                )
            }

            ScreenState.TEST_LOCK -> {
                BackHandler { currentScreen = ScreenState.DASHBOARD }
                TestLockScreen(
                    onNavigateBack = { currentScreen = ScreenState.DASHBOARD }
                )
            }
        }
    }
}

/**
 * Gatekeeper requiring master PIN or Biometric to open the Smart AppLock configuration dashboard.
 */
@Composable
fun DashboardAuthGate(
    appLockManager: AppLockManager,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val prefs = appLockManager.preferences
    val pinLength = prefs.getPinLength()
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(prefs.getRemainingLockoutSeconds()) }

    val isBiometricSupported = remember(activity) {
        activity != null && BiometricHelper.isBiometricAvailable(activity)
    }
    val isBiometricEnabled = isBiometricSupported && prefs.isBiometricEnabled()

    fun triggerBiometricPrompt() {
        if (activity != null && isBiometricEnabled && lockoutSeconds == 0) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Unlock Smart AppLock",
                subtitle = "Touch the fingerprint sensor to access dashboard",
                negativeButtonText = "Use PIN",
                onSuccess = {
                    prefs.resetFailedAttempts()
                    onUnlockSuccess()
                },
                onError = { _, _ ->
                    // Graceful fallback to PIN
                }
            )
        }
    }

    // Automatically challenge with biometric authentication when opening the app
    LaunchedEffect(Unit) {
        if (isBiometricEnabled && lockoutSeconds == 0) {
            delay(250L)
            triggerBiometricPrompt()
        }
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = prefs.getRemainingLockoutSeconds()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(36.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Security Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Smart AppLock Security",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isBiometricEnabled) "Use fingerprint or enter master PIN" else "Enter master PIN to manage protected apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                PinDots(
                    pinLength = pinLength,
                    enteredLength = enteredPin.length,
                    isError = isError
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

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
                                if (prefs.verifyPin(next)) {
                                    prefs.resetFailedAttempts()
                                    onUnlockSuccess()
                                } else {
                                    isError = true
                                    prefs.recordFailedAttempt()
                                    lockoutSeconds = prefs.getRemainingLockoutSeconds()
                                    errorMsg = if (lockoutSeconds > 0) {
                                        "Locked out for $lockoutSeconds seconds"
                                    } else {
                                        "Incorrect PIN"
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
                        errorMsg = null
                    }
                },
                lockoutSeconds = lockoutSeconds,
                extraLeftAction = {
                    if (isBiometricEnabled) {
                        IconButton(
                            onClick = { triggerBiometricPrompt() },
                            modifier = Modifier.testTag("dashboard_biometric_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Unlock with Fingerprint",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
