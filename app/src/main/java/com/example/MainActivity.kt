package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.AppLockManager
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoPalette
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
    CALCULATOR_GATEWAY,
    DASHBOARD,
    APP_SELECTION,
    SETTINGS,
    TEST_LOCK,
    VAULT
}

class MainNavigationViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private const val KEY_CURRENT_SCREEN = "nav_current_screen"
        private const val KEY_OPENED_CALC_FROM_DASHBOARD = "nav_opened_calc_from_dashboard"
    }

    fun getOrInitScreen(initialProvider: () -> ScreenState): ScreenState {
        val savedName = savedStateHandle.get<String>(KEY_CURRENT_SCREEN)
        if (savedName != null) {
            runCatching { return ScreenState.valueOf(savedName) }
        }
        val initial = initialProvider()
        savedStateHandle[KEY_CURRENT_SCREEN] = initial.name
        return initial
    }

    fun setScreen(screen: ScreenState) {
        savedStateHandle[KEY_CURRENT_SCREEN] = screen.name
    }

    fun getOpenedCalcFromDashboard(): Boolean {
        return savedStateHandle.get<Boolean>(KEY_OPENED_CALC_FROM_DASHBOARD) ?: false
    }

    fun setOpenedCalcFromDashboard(opened: Boolean) {
        savedStateHandle[KEY_OPENED_CALC_FROM_DASHBOARD] = opened
    }
}

class MainActivity : FragmentActivity() {

    private lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLockManager = AppLockManager.getInstance(this)
        val navViewModel = ViewModelProvider(this)[MainNavigationViewModel::class.java]

        setContent {
            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            var darkThemeOverride by rememberSaveable {
                mutableStateOf(appLockManager.preferences.isDarkThemeOverride())
            }
            val isDarkTheme = darkThemeOverride ?: systemInDark

            AppLockTheme(darkTheme = isDarkTheme) {
                MainAppNavHost(
                    appLockManager = appLockManager,
                    navViewModel = navViewModel,
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
    navViewModel: MainNavigationViewModel? = null,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = appLockManager.preferences

    var openedCalculatorFromDashboard by rememberSaveable {
        mutableStateOf(navViewModel?.getOpenedCalcFromDashboard() ?: false)
    }
    var currentScreen by rememberSaveable {
        mutableStateOf(
            navViewModel?.getOrInitScreen {
                when {
                    !prefs.isPinConfigured() -> ScreenState.ONBOARDING
                    prefs.isCalculatorDisguiseEnabled() -> ScreenState.CALCULATOR_GATEWAY
                    else -> ScreenState.AUTH_GATE
                }
            } ?: when {
                !prefs.isPinConfigured() -> ScreenState.ONBOARDING
                prefs.isCalculatorDisguiseEnabled() -> ScreenState.CALCULATOR_GATEWAY
                else -> ScreenState.AUTH_GATE
            }
        )
    }

    fun navigateTo(screen: ScreenState, calcFromDashboard: Boolean = openedCalculatorFromDashboard) {
        openedCalculatorFromDashboard = calcFromDashboard
        currentScreen = screen
        navViewModel?.setOpenedCalcFromDashboard(calcFromDashboard)
        navViewModel?.setScreen(screen)
    }

    // Relock Smart AppLock & Vault session automatically if screen turns off
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val activity = context as? android.app.Activity
                val isChangingConfigs = activity?.isChangingConfigurations == true
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                val isScreenOff = powerManager != null && !powerManager.isInteractive
                if (!isChangingConfigs && isScreenOff && prefs.isRelockOnScreenOff() && prefs.isPinConfigured()) {
                    val target = if (prefs.isCalculatorDisguiseEnabled()) {
                        ScreenState.CALCULATOR_GATEWAY
                    } else {
                        ScreenState.AUTH_GATE
                    }
                    navigateTo(target, calcFromDashboard = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                    onUnlockSuccess = { navigateTo(ScreenState.DASHBOARD) }
                )
            }

            ScreenState.ONBOARDING -> {
                OnboardingSetupScreen(
                    preferences = prefs,
                    onSetupComplete = { navigateTo(ScreenState.DASHBOARD) }
                )
            }

            ScreenState.CALCULATOR_GATEWAY -> {
                if (openedCalculatorFromDashboard) {
                    BackHandler {
                        navigateTo(ScreenState.DASHBOARD, calcFromDashboard = false)
                    }
                }
                com.example.ui.calculator.CalculatorVaultScreen(
                    preferences = prefs,
                    isStandaloneDisguiseMode = !openedCalculatorFromDashboard,
                    onGatewayUnlocked = {
                        navigateTo(ScreenState.VAULT, calcFromDashboard = false)
                    },
                    onNavigateBack = if (openedCalculatorFromDashboard) {
                        {
                            navigateTo(ScreenState.DASHBOARD, calcFromDashboard = false)
                        }
                    } else null
                )
            }

            ScreenState.DASHBOARD -> {
                if (prefs.isCalculatorDisguiseEnabled()) {
                    BackHandler {
                        navigateTo(ScreenState.CALCULATOR_GATEWAY, calcFromDashboard = false)
                    }
                }
                DashboardScreen(
                    appLockManager = appLockManager,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToAppSelection = { navigateTo(ScreenState.APP_SELECTION) },
                    onNavigateToSettings = { navigateTo(ScreenState.SETTINGS) },
                    onNavigateToTestLock = { navigateTo(ScreenState.TEST_LOCK) },
                    onNavigateToVault = { navigateTo(ScreenState.VAULT) },
                    onNavigateToCalculator = {
                        navigateTo(ScreenState.CALCULATOR_GATEWAY, calcFromDashboard = true)
                    }
                )
            }

            ScreenState.APP_SELECTION -> {
                BackHandler { navigateTo(ScreenState.DASHBOARD) }
                AppSelectionScreen(
                    preferences = prefs,
                    onNavigateBack = { navigateTo(ScreenState.DASHBOARD) }
                )
            }

            ScreenState.SETTINGS -> {
                BackHandler { navigateTo(ScreenState.DASHBOARD) }
                SettingsScreen(
                    preferences = prefs,
                    onNavigateBack = { navigateTo(ScreenState.DASHBOARD) },
                    onResetCompleted = { navigateTo(ScreenState.ONBOARDING, calcFromDashboard = false) }
                )
            }

            ScreenState.TEST_LOCK -> {
                BackHandler { navigateTo(ScreenState.DASHBOARD) }
                TestLockScreen(
                    onNavigateBack = { navigateTo(ScreenState.DASHBOARD) }
                )
            }

            ScreenState.VAULT -> {
                val returnScreen = if (prefs.isCalculatorDisguiseEnabled()) {
                    ScreenState.CALCULATOR_GATEWAY
                } else {
                    ScreenState.DASHBOARD
                }
                BackHandler { navigateTo(returnScreen, calcFromDashboard = false) }
                com.example.ui.vault.VaultScreen(
                    onNavigateBack = { navigateTo(returnScreen, calcFromDashboard = false) },
                    onOpenDashboard = { navigateTo(ScreenState.DASHBOARD, calcFromDashboard = false) }
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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
                onError = { errorCode, _ ->
                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT ||
                        errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                    ) {
                        errorMsg = "Fingerprint sensor locked. Please use PIN."
                    }
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

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(8.dp, CircleShape, spotColor = NeoPalette.PrimaryCyan)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                if (isDarkTheme) {
                                    listOf(NeoPalette.SurfaceElevated, NeoPalette.SurfaceDark)
                                } else {
                                    listOf(Color.White, MaterialTheme.colorScheme.surfaceVariant)
                                }
                            )
                        )
                        .border(1.5.dp, NeoPalette.PrimaryCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Security Shield",
                        tint = NeoPalette.PrimaryCyan,
                        modifier = Modifier.size(38.dp)
                    )
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
                        NeoCircularButton(
                            onClick = { triggerBiometricPrompt() },
                            size = 56.dp,
                            accentGlow = NeoPalette.PrimaryCyan.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("dashboard_biometric_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Unlock with Fingerprint",
                                tint = NeoPalette.PrimaryCyan,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
