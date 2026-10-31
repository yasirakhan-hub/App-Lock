package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.repository.AppLockManager
import com.example.data.security.AppLockPreferences.AuthMethod
import com.example.ui.calculator.CalculatorVaultScreen
import com.example.ui.common.BiometricActionRow
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoGoldPadlockEmblem
import com.example.ui.common.NeoPalette
import com.example.ui.common.PasswordAuthInput
import com.example.ui.common.PatternLockGrid
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.ui.screens.AppSelectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TestLockScreen
import com.example.ui.theme.AppLockTheme
import com.example.ui.vault.VaultScreen
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
    VAULT_AUTH_GATE,
    VAULT
}

/**
 * ViewModel preserving navigation state across configuration changes and system file picker launches,
 * while strictly enforcing mandatory Vault authentication on every entry to Files (VAULT).
 */
class MainNavigationViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private const val KEY_CURRENT_SCREEN = "nav_current_screen"
        private const val KEY_OPENED_CALC_FROM_DASHBOARD = "nav_opened_calc_from_dashboard"
        private const val KEY_VAULT_RETURN_SCREEN = "nav_vault_return_screen"
        private const val KEY_VAULT_AUTHORIZED = "nav_vault_authorized"
        private const val KEY_APP_MGMT_AUTHORIZED = "nav_app_mgmt_authorized"
        private const val KEY_SYSTEM_PICKER_IN_FLIGHT = "nav_system_picker_in_flight"
        private const val KEY_VAULT_IMPORT_IN_FLIGHT = "nav_vault_import_in_flight"
        private const val KEY_LAST_PICKER_EVENT_TIME = "nav_last_picker_event_time"
        private const val PICKER_RETURN_GRACE_MS = 3000L
    }

    private var isSystemPickerInFlight: Boolean = false

    fun getOrInitScreen(initialProvider: () -> ScreenState): ScreenState {
        val savedName = savedStateHandle.get<String>(KEY_CURRENT_SCREEN)
        if (savedName != null) {
            val restored = runCatching { ScreenState.valueOf(savedName) }.getOrNull()
            if (restored != null) {
                if (restored == ScreenState.VAULT && !isVaultAuthorized()) {
                    savedStateHandle[KEY_CURRENT_SCREEN] = ScreenState.VAULT_AUTH_GATE.name
                    return ScreenState.VAULT_AUTH_GATE
                }
                return restored
            }
        }
        val initial = initialProvider()
        val safeInitial = if (initial == ScreenState.VAULT && !isVaultAuthorized()) {
            ScreenState.VAULT_AUTH_GATE
        } else {
            initial
        }
        savedStateHandle[KEY_CURRENT_SCREEN] = safeInitial.name
        return safeInitial
    }

    fun setScreen(screen: ScreenState) {
        if (screen == ScreenState.VAULT) {
            savedStateHandle[KEY_VAULT_AUTHORIZED] = true
        } else {
            // Leaving VAULT immediately locks the vault session
            savedStateHandle[KEY_VAULT_AUTHORIZED] = false
            isSystemPickerInFlight = false
            savedStateHandle[KEY_SYSTEM_PICKER_IN_FLIGHT] = false
            savedStateHandle[KEY_VAULT_IMPORT_IN_FLIGHT] = false
            savedStateHandle[KEY_LAST_PICKER_EVENT_TIME] = 0L
        }
        if (screen != ScreenState.APP_SELECTION) {
            // Leaving APP_SELECTION immediately ends the authenticated app-management session
            savedStateHandle[KEY_APP_MGMT_AUTHORIZED] = false
        }
        savedStateHandle[KEY_CURRENT_SCREEN] = screen.name
    }

    /**
     * Requests access to the Files (Vault) screen from any entry point.
     * Always routes through [ScreenState.VAULT_AUTH_GATE] first.
     */
    fun requestVaultAccess(returnScreen: ScreenState = ScreenState.DASHBOARD): ScreenState {
        savedStateHandle[KEY_VAULT_AUTHORIZED] = false
        savedStateHandle[KEY_APP_MGMT_AUTHORIZED] = false
        isSystemPickerInFlight = false
        savedStateHandle[KEY_SYSTEM_PICKER_IN_FLIGHT] = false
        savedStateHandle[KEY_VAULT_IMPORT_IN_FLIGHT] = false
        savedStateHandle[KEY_LAST_PICKER_EVENT_TIME] = 0L
        val safeReturn = if (returnScreen == ScreenState.VAULT || returnScreen == ScreenState.VAULT_AUTH_GATE) {
            ScreenState.DASHBOARD
        } else {
            returnScreen
        }
        savedStateHandle[KEY_VAULT_RETURN_SCREEN] = safeReturn.name
        savedStateHandle[KEY_CURRENT_SCREEN] = ScreenState.VAULT_AUTH_GATE.name
        return ScreenState.VAULT_AUTH_GATE
    }

    /**
     * Grants single-visit authorization to open [ScreenState.VAULT] after successful PIN or Biometric verification.
     */
    fun authorizeVaultEntry(): ScreenState {
        savedStateHandle[KEY_VAULT_AUTHORIZED] = true
        savedStateHandle[KEY_CURRENT_SCREEN] = ScreenState.VAULT.name
        return ScreenState.VAULT
    }

    /**
     * Immediately locks the vault session (called on leaving Files, pressing Back, or app backgrounding).
     */
    fun lockVaultSession() {
        savedStateHandle[KEY_VAULT_AUTHORIZED] = false
        isSystemPickerInFlight = false
        savedStateHandle[KEY_SYSTEM_PICKER_IN_FLIGHT] = false
        savedStateHandle[KEY_VAULT_IMPORT_IN_FLIGHT] = false
        savedStateHandle[KEY_LAST_PICKER_EVENT_TIME] = 0L
        if (savedStateHandle.get<String>(KEY_CURRENT_SCREEN) == ScreenState.VAULT.name) {
            savedStateHandle[KEY_CURRENT_SCREEN] = ScreenState.VAULT_AUTH_GATE.name
        }
    }

    fun isVaultAuthorized(): Boolean =
        (savedStateHandle.get<Boolean>(KEY_VAULT_AUTHORIZED) == true) ||
                isSystemPickerOrImportActive()

    /**
     * Session-scoped authorization for the Protected-App Management flow ([ScreenState.APP_SELECTION]).
     * Active while the user remains in the management screen; revoked on exit or backgrounding.
     */
    fun authorizeAppManagementSession() {
        savedStateHandle[KEY_APP_MGMT_AUTHORIZED] = true
    }

    fun lockAppManagementSession() {
        savedStateHandle[KEY_APP_MGMT_AUTHORIZED] = false
    }

    fun isAppManagementAuthorized(): Boolean =
        savedStateHandle.get<Boolean>(KEY_APP_MGMT_AUTHORIZED) == true

    fun setSystemPickerActive(active: Boolean) {
        isSystemPickerInFlight = active
        savedStateHandle[KEY_SYSTEM_PICKER_IN_FLIGHT] = active
        savedStateHandle[KEY_LAST_PICKER_EVENT_TIME] = System.currentTimeMillis()
        if (active) {
            savedStateHandle[KEY_VAULT_AUTHORIZED] = true
            savedStateHandle[KEY_CURRENT_SCREEN] = ScreenState.VAULT.name
        } else if (savedStateHandle.get<String>(KEY_CURRENT_SCREEN) == ScreenState.VAULT.name) {
            // Returning from system picker to Vault: keep Vault session authorized
            savedStateHandle[KEY_VAULT_AUTHORIZED] = true
        }
    }

    fun isSystemPickerActive(): Boolean =
        isSystemPickerInFlight || (savedStateHandle.get<Boolean>(KEY_SYSTEM_PICKER_IN_FLIGHT) == true)

    fun setVaultImportInFlight(inFlight: Boolean) {
        savedStateHandle[KEY_VAULT_IMPORT_IN_FLIGHT] = inFlight
        if (inFlight) {
            savedStateHandle[KEY_VAULT_AUTHORIZED] = true
            savedStateHandle[KEY_CURRENT_SCREEN] = ScreenState.VAULT.name
        }
    }

    fun isVaultImportInFlight(): Boolean =
        savedStateHandle.get<Boolean>(KEY_VAULT_IMPORT_IN_FLIGHT) == true

    fun isWithinPickerGraceWindow(): Boolean {
        val lastTime = savedStateHandle.get<Long>(KEY_LAST_PICKER_EVENT_TIME) ?: 0L
        if (lastTime <= 0L) return false
        val elapsed = System.currentTimeMillis() - lastTime
        return elapsed in 0L..PICKER_RETURN_GRACE_MS
    }

    fun isSystemPickerOrImportActive(): Boolean =
        isSystemPickerActive() || isVaultImportInFlight() || isWithinPickerGraceWindow()

    fun getVaultReturnScreen(): ScreenState {
        val name = savedStateHandle.get<String>(KEY_VAULT_RETURN_SCREEN)
        return name?.let { runCatching { ScreenState.valueOf(it) }.getOrNull() } ?: ScreenState.DASHBOARD
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
        val prefs = appLockManager.preferences
        navViewModel.getOrInitScreen {
            when {
                !prefs.isPinConfigured() -> ScreenState.ONBOARDING
                prefs.isCalculatorDisguiseEnabled() -> ScreenState.CALCULATOR_GATEWAY
                else -> ScreenState.AUTH_GATE
            }
        }

        setContent {
            var darkThemeOverride by rememberSaveable {
                mutableStateOf(appLockManager.preferences.isDarkThemeOverride())
            }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkTheme = darkThemeOverride ?: systemDark

            AppLockTheme(darkTheme = isDarkTheme) {
                MainAppNavHost(
                    appLockManager = appLockManager,
                    navViewModel = navViewModel,
                    isDarkTheme = isDarkTheme,
                    darkThemeOverride = darkThemeOverride,
                    onSelectThemeOverride = { mode ->
                        darkThemeOverride = mode
                        appLockManager.preferences.setDarkThemeOverride(mode)
                    },
                    onToggleTheme = {
                        val next = !isDarkTheme
                        darkThemeOverride = next
                        appLockManager.preferences.setDarkThemeOverride(next)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.util.DeviceAdminHelper.notifyAdminStatusChanged(this)
    }
}

@Composable
fun MainAppNavHost(
    appLockManager: AppLockManager,
    navViewModel: MainNavigationViewModel? = null,
    isDarkTheme: Boolean = true,
    darkThemeOverride: Boolean? = null,
    onSelectThemeOverride: (Boolean?) -> Unit = {},
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = appLockManager.preferences

    var isVaultAuthorizedLocal by rememberSaveable {
        mutableStateOf(navViewModel?.isVaultAuthorized() ?: false)
    }
    var isAppMgmtAuthorizedLocal by rememberSaveable {
        mutableStateOf(navViewModel?.isAppManagementAuthorized() ?: false)
    }
    var isSystemPickerActiveLocal by rememberSaveable {
        mutableStateOf(navViewModel?.isSystemPickerActive() ?: false)
    }
    var vaultReturnScreen by rememberSaveable {
        mutableStateOf(navViewModel?.getVaultReturnScreen() ?: ScreenState.DASHBOARD)
    }
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
        navViewModel?.setOpenedCalcFromDashboard(calcFromDashboard)

        if (screen != ScreenState.APP_SELECTION) {
            isAppMgmtAuthorizedLocal = false
            navViewModel?.lockAppManagementSession()
        }

        if (screen == ScreenState.VAULT) {
            // Direct navigation to VAULT without authorization is strictly redirected to VAULT_AUTH_GATE
            val isAuthorized = (navViewModel?.isVaultAuthorized() == true) || isVaultAuthorizedLocal
            if (!isAuthorized) {
                val targetGate = navViewModel?.requestVaultAccess(currentScreen) ?: ScreenState.VAULT_AUTH_GATE
                vaultReturnScreen = navViewModel?.getVaultReturnScreen() ?: currentScreen
                currentScreen = targetGate
                return
            }
        } else {
            // Leaving VAULT immediately locks the vault session
            isVaultAuthorizedLocal = false
            isSystemPickerActiveLocal = false
            appLockManager.setSystemPickerInFlight(false)
            navViewModel?.lockVaultSession()
        }

        currentScreen = screen
        navViewModel?.setScreen(screen)
    }

    fun requestOpenVault(fromScreen: ScreenState = currentScreen) {
        isVaultAuthorizedLocal = false
        isAppMgmtAuthorizedLocal = false
        isSystemPickerActiveLocal = false
        appLockManager.setSystemPickerInFlight(false)
        navViewModel?.lockAppManagementSession()
        val safeReturn = if (fromScreen == ScreenState.VAULT || fromScreen == ScreenState.VAULT_AUTH_GATE) {
            ScreenState.DASHBOARD
        } else {
            fromScreen
        }
        vaultReturnScreen = safeReturn
        val target = navViewModel?.requestVaultAccess(safeReturn) ?: ScreenState.VAULT_AUTH_GATE
        currentScreen = target
    }

    fun completeVaultAuthentication() {
        isVaultAuthorizedLocal = true
        val target = navViewModel?.authorizeVaultEntry() ?: ScreenState.VAULT
        currentScreen = target
    }

    // Lifecycle observer:
    // 1. Locks the Files Vault and App Management session whenever the app goes into the background.
    // 2. Relocks the entire app if the screen turns off and relock-on-screen-off is enabled.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? android.app.Activity
                val isChangingConfigs = activity?.isChangingConfigurations == true
                val pickerOrImportInFlight = (navViewModel?.isSystemPickerOrImportActive() == true) ||
                        isSystemPickerActiveLocal ||
                        appLockManager.isSystemPickerBypassActive()

                if (!isChangingConfigs && !pickerOrImportInFlight) {
                    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    val isScreenOff = powerManager != null && !powerManager.isInteractive

                    // End active App Management session on backgrounding
                    isAppMgmtAuthorizedLocal = false
                    navViewModel?.lockAppManagementSession()

                    // Always lock the Vault when the app goes to background
                    if (currentScreen == ScreenState.VAULT) {
                        isVaultAuthorizedLocal = false
                        navViewModel?.lockVaultSession()
                        currentScreen = ScreenState.VAULT_AUTH_GATE
                        navViewModel?.setScreen(ScreenState.VAULT_AUTH_GATE)
                    }

                    if (isScreenOff && prefs.isRelockOnScreenOff() && prefs.isPinConfigured()) {
                        val target = if (prefs.isCalculatorDisguiseEnabled()) {
                            ScreenState.CALCULATOR_GATEWAY
                        } else {
                            ScreenState.AUTH_GATE
                        }
                        navigateTo(target, calcFromDashboard = false)
                    }
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
                    title = "Enter PIN",
                    subtitle = "Unlock your secure space",
                    currentBottomTab = MainBottomTab.HOME,
                    onSelectBottomTab = null,
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
                CalculatorVaultScreen(
                    preferences = prefs,
                    isStandaloneDisguiseMode = !openedCalculatorFromDashboard,
                    onGatewayUnlocked = {
                        // CalculatorVaultScreen already verified Master PIN / Biometric in AuthChallengeDialog
                        completeVaultAuthentication()
                    },
                    onNavigateBack = if (openedCalculatorFromDashboard) {
                        {
                            navigateTo(ScreenState.DASHBOARD, calcFromDashboard = false)
                        }
                    } else null,
                    onNavigateToApps = { navigateTo(ScreenState.APP_SELECTION, calcFromDashboard = false) },
                    onNavigateToFiles = { requestOpenVault(ScreenState.DASHBOARD) },
                    onNavigateToSettings = { navigateTo(ScreenState.SETTINGS, calcFromDashboard = false) }
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
                    darkThemeOverride = darkThemeOverride,
                    onSelectThemeOverride = onSelectThemeOverride,
                    onToggleTheme = onToggleTheme,
                    onNavigateToAppSelection = { navigateTo(ScreenState.APP_SELECTION) },
                    onNavigateToSettings = { navigateTo(ScreenState.SETTINGS) },
                    onNavigateToTestLock = { navigateTo(ScreenState.TEST_LOCK) },
                    onNavigateToVault = { requestOpenVault(ScreenState.DASHBOARD) },
                    onNavigateToCalculator = {
                        navigateTo(ScreenState.CALCULATOR_GATEWAY, calcFromDashboard = true)
                    }
                )
            }

            ScreenState.APP_SELECTION -> {
                BackHandler { navigateTo(ScreenState.DASHBOARD) }
                AppSelectionScreen(
                    preferences = prefs,
                    onNavigateBack = { navigateTo(ScreenState.DASHBOARD) },
                    onNavigateToFiles = { requestOpenVault(ScreenState.APP_SELECTION) },
                    onNavigateToSettings = { navigateTo(ScreenState.SETTINGS) },
                    isManagementSessionAuthenticated = isAppMgmtAuthorizedLocal || (navViewModel?.isAppManagementAuthorized() == true),
                    onManagementSessionAuthorized = {
                        isAppMgmtAuthorizedLocal = true
                        navViewModel?.authorizeAppManagementSession()
                    }
                )
            }

            ScreenState.SETTINGS -> {
                BackHandler { navigateTo(ScreenState.DASHBOARD) }
                SettingsScreen(
                    preferences = prefs,
                    onNavigateBack = { navigateTo(ScreenState.DASHBOARD) },
                    onResetCompleted = { navigateTo(ScreenState.ONBOARDING, calcFromDashboard = false) },
                    onNavigateToApps = { navigateTo(ScreenState.APP_SELECTION) },
                    onNavigateToFiles = { requestOpenVault(ScreenState.SETTINGS) }
                )
            }

            ScreenState.TEST_LOCK -> {
                BackHandler { navigateTo(ScreenState.DASHBOARD) }
                TestLockScreen(
                    onNavigateBack = { navigateTo(ScreenState.DASHBOARD) },
                    onNavigateToApps = { navigateTo(ScreenState.APP_SELECTION) },
                    onNavigateToFiles = { requestOpenVault(ScreenState.TEST_LOCK) },
                    onNavigateToSettings = { navigateTo(ScreenState.SETTINGS) }
                )
            }

            ScreenState.VAULT_AUTH_GATE -> {
                val cancelTarget = vaultReturnScreen.takeIf {
                    it != ScreenState.VAULT && it != ScreenState.VAULT_AUTH_GATE
                } ?: ScreenState.DASHBOARD

                BackHandler {
                    navigateTo(cancelTarget, calcFromDashboard = false)
                }

                DashboardAuthGate(
                    appLockManager = appLockManager,
                    title = "Enter PIN",
                    subtitle = "Unlock your secure space",
                    currentBottomTab = MainBottomTab.FILES,
                    onSelectBottomTab = { tab ->
                        when (tab) {
                            MainBottomTab.HOME -> navigateTo(ScreenState.DASHBOARD, calcFromDashboard = false)
                            MainBottomTab.APPS -> navigateTo(ScreenState.APP_SELECTION, calcFromDashboard = false)
                            MainBottomTab.FILES -> {}
                            MainBottomTab.SETTINGS -> navigateTo(ScreenState.SETTINGS, calcFromDashboard = false)
                        }
                    },
                    onUnlockSuccess = {
                        completeVaultAuthentication()
                    }
                )
            }

            ScreenState.VAULT -> {
                val isAuthorized = (navViewModel?.isVaultAuthorized() == true) ||
                        isVaultAuthorizedLocal ||
                        isSystemPickerActiveLocal ||
                        appLockManager.isSystemPickerBypassActive()

                if (currentScreen == ScreenState.VAULT && !isAuthorized) {
                    // Fail-safe guard: only redirect if VAULT is the active target screen and unauthorized.
                    // Never redirect during exit transitions when navigating from Files to Home/Apps/Settings.
                    LaunchedEffect(currentScreen, isAuthorized) {
                        if (currentScreen == ScreenState.VAULT && !isAuthorized) {
                            requestOpenVault(ScreenState.DASHBOARD)
                        }
                    }
                } else if (currentScreen == ScreenState.VAULT) {
                    val returnScreen = vaultReturnScreen.takeIf {
                        it != ScreenState.VAULT && it != ScreenState.VAULT_AUTH_GATE
                    } ?: ScreenState.DASHBOARD
                    VaultScreen(
                        onNavigateBack = { navigateTo(returnScreen, calcFromDashboard = false) },
                        onOpenDashboard = { navigateTo(ScreenState.DASHBOARD, calcFromDashboard = false) },
                        onNavigateToApps = { navigateTo(ScreenState.APP_SELECTION, calcFromDashboard = false) },
                        onNavigateToSettings = { navigateTo(ScreenState.SETTINGS, calcFromDashboard = false) },
                        onSystemPickerLaunched = {
                            isVaultAuthorizedLocal = true
                            isSystemPickerActiveLocal = true
                            appLockManager.setSystemPickerInFlight(true)
                            navViewModel?.setSystemPickerActive(true)
                        },
                        onSystemPickerFinished = {
                            isVaultAuthorizedLocal = true
                            isSystemPickerActiveLocal = false
                            appLockManager.setSystemPickerInFlight(false)
                            navViewModel?.setSystemPickerActive(false)
                        },
                        onImportInFlightChanged = { inFlight ->
                            if (inFlight) {
                                isVaultAuthorizedLocal = true
                            }
                            navViewModel?.setVaultImportInFlight(inFlight)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Internal Splash / Opening Screen matching Panel 1 of the reference design and using the exact attached S-lock logo.
 */
@Composable
fun OpeningSplashScreen(
    onSplashComplete: () -> Unit = {}
) {
    Scaffold(
        containerColor = Color(0xFF0B0B0E),
        modifier = Modifier
            .fillMaxSize()
            .testTag("opening_splash_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1405),
                            Color(0xFF0B0B0E),
                            Color(0xFF140F04)
                        )
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NeoGoldPadlockEmblem(
                badgeSize = 148.dp,
                isOpenShackle = false,
                modifier = Modifier.testTag("splash_app_logo")
            )

            Spacer(modifier = Modifier.height(28.dp))

            com.example.ui.common.NeoBrandTitle(fontSize = 30.sp)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Your Privacy\nOur Priority",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF9CA3AF),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Developed by Yasir A Khan",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.PrimaryGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_developer_credit")
            )
        }
    }
}

/**
 * Gatekeeper requiring master credential (4-Digit PIN, 6-Digit PIN, Pattern Lock, or Password)
 * or Biometric to open Smart AppLock or the Private File Vault.
 * Matches Panel 2 of the Black & Gold Neomorphism reference image.
 */
@Composable
fun DashboardAuthGate(
    appLockManager: AppLockManager,
    title: String = "Enter PIN",
    subtitle: String = "Unlock your secure space",
    currentBottomTab: MainBottomTab? = MainBottomTab.HOME,
    onSelectBottomTab: ((MainBottomTab) -> Unit)? = null,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val prefs = appLockManager.preferences
    val authMethod = remember(prefs) { prefs.getAuthMethod() }
    val pinLength = when (authMethod) {
        AuthMethod.PIN_6 -> 6
        else -> prefs.getPinLength()
    }
    val scope = rememberCoroutineScope()

    val resolvedTitle = remember(title, authMethod) {
        if (title == "Enter PIN") {
            when (authMethod) {
                AuthMethod.PIN_4 -> "Enter 4-Digit PIN"
                AuthMethod.PIN_6 -> "Enter 6-Digit PIN"
                AuthMethod.PATTERN -> "Draw Pattern"
                AuthMethod.PASSWORD -> "Enter Password"
            }
        } else {
            title
        }
    }

    var enteredPin by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(prefs.getRemainingLockoutSeconds()) }

    val isBiometricSupported = remember(activity) {
        activity != null && BiometricHelper.isBiometricAvailable(activity)
    }
    val isBiometricEnabled = isBiometricSupported && prefs.isBiometricEnabled()

    val negativeButtonLabel = when (authMethod) {
        AuthMethod.PIN_4, AuthMethod.PIN_6 -> "Use PIN"
        AuthMethod.PATTERN -> "Use Pattern"
        AuthMethod.PASSWORD -> "Use Password"
    }

    val isUnlocked = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun performUnlock() {
        if (isUnlocked.compareAndSet(false, true)) {
            BiometricHelper.cancelCurrentPrompt()
            prefs.resetFailedAttempts()
            onUnlockSuccess()
        }
    }

    fun triggerBiometricPrompt() {
        if (activity != null && isBiometricEnabled && lockoutSeconds == 0 && !isUnlocked.get()) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = resolvedTitle,
                subtitle = subtitle,
                negativeButtonText = negativeButtonLabel,
                onSuccess = {
                    performUnlock()
                },
                onError = { errorCode, _ ->
                    if (!isUnlocked.get()) {
                        if (errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                        ) {
                            errorMsg = "Fingerprint sensor locked. Please use ${authMethod.displayName}."
                        }
                    }
                }
            )
        }
    }

    fun recordFailedAttemptWithMessage(defaultMessage: String) {
        if (isUnlocked.get()) return
        isError = true
        prefs.recordFailedAttempt()
        lockoutSeconds = prefs.getRemainingLockoutSeconds()
        errorMsg = if (lockoutSeconds > 0) {
            "Locked out for $lockoutSeconds seconds"
        } else {
            defaultMessage
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            BiometricHelper.cancelCurrentPrompt()
        }
    }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && lockoutSeconds == 0 && !isUnlocked.get()) {
            delay(250L)
            if (!isUnlocked.get()) {
                triggerBiometricPrompt()
            }
        }
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = prefs.getRemainingLockoutSeconds()
        }
    }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        bottomBar = {
            NeoBottomNavigationBar(
                currentTab = currentBottomTab,
                onSelectTab = { tab ->
                    onSelectBottomTab?.invoke(tab)
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("auth_gate_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (NeoPalette.isDarkMode) {
                            listOf(
                                Color(0xFF181305),
                                NeoPalette.BackgroundBase,
                                Color(0xFF120E04)
                            )
                        } else {
                            listOf(
                                NeoPalette.BackgroundBase,
                                NeoPalette.SurfaceElevated,
                                NeoPalette.BackgroundBase
                            )
                        }
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(8.dp))

                NeoGoldPadlockEmblem(
                    badgeSize = 86.dp,
                    isOpenShackle = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Developed by Yasir A Khan",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NeoPalette.PrimaryGold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary,
                    modifier = Modifier.testTag("auth_gate_title")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoPalette.TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (authMethod == AuthMethod.PIN_4 || authMethod == AuthMethod.PIN_6) {
                    Spacer(modifier = Modifier.height(18.dp))

                    PinDots(
                        pinLength = pinLength,
                        enteredLength = enteredPin.length,
                        isError = isError
                    )
                }

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoPalette.ErrorRed,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("auth_gate_error_text")
                    )
                }
            }

            when (authMethod) {
                AuthMethod.PIN_4, AuthMethod.PIN_6 -> {
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
                                        if (prefs.verifyCredential(next)) {
                                            performUnlock()
                                        } else {
                                            recordFailedAttemptWithMessage("Incorrect PIN")
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
                            NeoCircularButton(
                                onClick = { triggerBiometricPrompt() },
                                size = 70.dp,
                                isGoldSolid = true,
                                modifier = Modifier.testTag("dashboard_biometric_trigger_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Unlock with Fingerprint",
                                    tint = NeoPalette.TextInverse,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    )
                }

                AuthMethod.PATTERN -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PatternLockGrid(
                            enabled = lockoutSeconds == 0,
                            isError = isError,
                            gridSize = 236.dp,
                            onPatternStart = {
                                isError = false
                                errorMsg = null
                            },
                            onPatternTooShort = {
                                isError = true
                                errorMsg = "Connect at least 4 dots"
                            },
                            onPatternComplete = { drawnPattern ->
                                if (lockoutSeconds > 0) return@PatternLockGrid
                                if (prefs.verifyCredential(drawnPattern)) {
                                    performUnlock()
                                } else {
                                    recordFailedAttemptWithMessage("Incorrect pattern")
                                }
                            }
                        )

                        if (isBiometricEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            BiometricActionRow(
                                onBiometricClick = { triggerBiometricPrompt() },
                                enabled = lockoutSeconds == 0,
                                testTag = "dashboard_biometric_trigger_button"
                            )
                        }
                    }
                }

                AuthMethod.PASSWORD -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PasswordAuthInput(
                            value = enteredPassword,
                            onValueChange = {
                                enteredPassword = it
                                isError = false
                                errorMsg = null
                            },
                            enabled = lockoutSeconds == 0,
                            isError = isError,
                            submitButtonText = "Unlock",
                            onSubmit = {
                                if (lockoutSeconds > 0) return@PasswordAuthInput
                                if (enteredPassword.isEmpty()) {
                                    isError = true
                                    errorMsg = "Password cannot be empty"
                                    return@PasswordAuthInput
                                }
                                if (prefs.verifyCredential(enteredPassword)) {
                                    performUnlock()
                                } else {
                                    recordFailedAttemptWithMessage("Incorrect password")
                                    enteredPassword = ""
                                }
                            }
                        )

                        if (isBiometricEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            BiometricActionRow(
                                onBiometricClick = { triggerBiometricPrompt() },
                                enabled = lockoutSeconds == 0,
                                testTag = "dashboard_biometric_trigger_button"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
