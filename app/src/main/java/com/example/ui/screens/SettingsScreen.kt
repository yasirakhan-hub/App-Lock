package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pin
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import com.example.data.security.AppLockPreferences
import com.example.data.security.AppLockPreferences.AuthMethod
import com.example.ui.common.AuthChallengeDialog
import com.example.ui.common.BiometricActionRow
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoIconBadge
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.ui.common.PasswordAuthInput
import com.example.ui.common.PatternLockGrid
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.util.BiometricHelper
import com.example.util.DeviceAdminHelper
import com.example.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppLockPreferences,
    onNavigateBack: () -> Unit,
    onResetCompleted: () -> Unit,
    onNavigateToApps: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isAccessibilityGranted by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isOverlayGranted by remember { mutableStateOf(PermissionHelper.isOverlayPermissionGranted(context)) }
    var isDeviceAdminActive by remember { mutableStateOf(DeviceAdminHelper.isDeviceAdminActive(context)) }
    var relockOnScreenOff by remember { mutableStateOf(preferences.isRelockOnScreenOff()) }
    var isBiometricEnabled by remember { mutableStateOf(preferences.isBiometricEnabled()) }
    var isCalcSecretConfigured by remember { mutableStateOf(preferences.isCalculatorSecretConfigured()) }
    var isCalcDisguiseEnabled by remember { mutableStateOf(preferences.isCalculatorDisguiseEnabled()) }
    var activeAuthMethod by remember { mutableStateOf(preferences.getAuthMethod()) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangeQuestionDialog by remember { mutableStateOf(false) }
    var showSecurityLimitationsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showConfigureCalcSecretDialog by remember { mutableStateOf(false) }
    var enableDisguiseAfterSecretSetup by remember { mutableStateOf(false) }

    var showAuthForChangeQuestion by remember { mutableStateOf(false) }
    var showAuthForDeactivateAdmin by remember { mutableStateOf(false) }
    var showAuthForReset by remember { mutableStateOf(false) }
    var showAuthForCalcSecret by remember { mutableStateOf(false) }
    var pendingCalcDisguiseToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingRelockPolicy by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isAccessibilityGranted = PermissionHelper.isAccessibilityServiceEnabled(context)
        isOverlayGranted = PermissionHelper.isOverlayPermissionGranted(context)
        isDeviceAdminActive = DeviceAdminHelper.isDeviceAdminActive(context)
        activeAuthMethod = preferences.getAuthMethod()
    }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeoPalette.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeoPalette.BackgroundBase
                )
            )
        },
        bottomBar = {
            NeoBottomNavigationBar(
                currentTab = MainBottomTab.SETTINGS,
                onSelectTab = { tab ->
                    when (tab) {
                        MainBottomTab.HOME -> onNavigateBack()
                        MainBottomTab.APPS -> onNavigateToApps()
                        MainBottomTab.FILES -> onNavigateToFiles()
                        MainBottomTab.SETTINGS -> {}
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Change PIN (matching Panel 9)
            SettingsRow(
                title = "Change PIN",
                subtitle = "Current: ${activeAuthMethod.displayName} • Tap to change",
                icon = Icons.Default.Person,
                trailingStatus = activeAuthMethod.displayName,
                onClick = { showChangePinDialog = true },
                testTag = "settings_change_pin_row"
            )

            // 2. Biometric Authentication (matching Panel 9)
            NeoCard(
                shape = NeoShapes.MediumCard,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_biometric_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        NeoIconBadge(
                            icon = Icons.Default.Fingerprint,
                            size = 44.dp,
                            iconSize = 22.dp,
                            isGlowing = true
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Biometric Authentication",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeoPalette.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Fingerprint / Face Unlock",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { checked ->
                            isBiometricEnabled = checked
                            preferences.setBiometricEnabled(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeoPalette.TextInverse,
                            checkedTrackColor = NeoPalette.PrimaryGold,
                            uncheckedThumbColor = NeoPalette.TextSecondary,
                            uncheckedTrackColor = NeoPalette.SurfaceSunken
                        ),
                        modifier = Modifier.testTag("settings_biometric_switch")
                    )
                }
            }

            // 3. Recovery Question (matching Panel 9)
            SettingsRow(
                title = "Recovery Question",
                subtitle = preferences.getSecurityQuestion() ?: "Set your recovery details",
                icon = Icons.Default.Key,
                onClick = { showAuthForChangeQuestion = true },
                testTag = "settings_change_question_row"
            )

            // 4. App Lock Settings (matching Panel 9)
            SettingsRow(
                title = "App Lock Settings",
                subtitle = if (relockOnScreenOff) "Customize lock behavior (Screen Off)" else "Customize lock behavior (Immediate)",
                icon = Icons.Default.Lock,
                onClick = {
                    pendingRelockPolicy = !relockOnScreenOff
                },
                testTag = "settings_app_lock_behavior_row"
            )

            // 5. Hide Files Settings (matching Panel 9)
            SettingsRow(
                title = "Hide Files Settings",
                subtitle = "Import & vault options",
                icon = Icons.Default.Folder,
                onClick = onNavigateToFiles,
                testTag = "settings_hide_files_row"
            )

            // 6. Calculator Settings (matching Panel 9)
            SettingsRow(
                title = "Calculator Settings",
                subtitle = if (isCalcSecretConfigured) {
                    "Disguise options (${preferences.getCalculatorSecretLength()} digits + '=')"
                } else {
                    "Disguise options"
                },
                icon = Icons.Default.Calculate,
                trailingStatus = if (isCalcSecretConfigured) "Configured" else null,
                trailingColor = NeoPalette.PrimaryGold,
                onClick = {
                    enableDisguiseAfterSecretSetup = false
                    showAuthForCalcSecret = true
                },
                testTag = "settings_calc_secret_row"
            )

            // 7. Security Architecture & Limitations row
            SettingsRow(
                title = "Security Architecture",
                subtitle = "App version 1.0.0 • AES-256-GCM & Accessibility details",
                icon = Icons.Default.Info,
                onClick = { showSecurityLimitationsDialog = true },
                testTag = "settings_limitations_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calculator Disguise Mode Toggle Card (preserving settings_calc_disguise_card & switch)
            SettingsSectionHeader(title = "Calculator Disguise Gateway")

            NeoCard(
                shape = NeoShapes.MediumCard,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_calc_disguise_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        NeoIconBadge(
                            icon = Icons.Default.Calculate,
                            size = 42.dp,
                            iconSize = 20.dp,
                            isGlowing = isCalcDisguiseEnabled
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Launch in Calculator Disguise",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeoPalette.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Open tactile calculator on launch; enter secret + '=' to access Vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = isCalcDisguiseEnabled,
                        onCheckedChange = { checked ->
                            pendingCalcDisguiseToggle = checked
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeoPalette.TextInverse,
                            checkedTrackColor = NeoPalette.PrimaryGold,
                            uncheckedThumbColor = NeoPalette.TextSecondary,
                            uncheckedTrackColor = NeoPalette.SurfaceSunken
                        ),
                        modifier = Modifier.testTag("settings_calc_disguise_switch")
                    )
                }
            }

            // Relock Policy Radio Card
            SettingsSectionHeader(title = "Relock Policy")

            NeoCard(
                shape = NeoShapes.MediumCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !relockOnScreenOff,
                            onClick = {
                                if (relockOnScreenOff) {
                                    pendingRelockPolicy = false
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = NeoPalette.PrimaryGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Immediately on app exit (High Security)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NeoPalette.TextPrimary
                            )
                            Text(
                                "Prompts for PIN each time the app is reopened",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = relockOnScreenOff,
                            onClick = {
                                if (!relockOnScreenOff) {
                                    pendingRelockPolicy = true
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = NeoPalette.PrimaryGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "When screen turns off (Convenience)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NeoPalette.TextPrimary
                            )
                            Text(
                                "Keeps apps unlocked until you lock your phone",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.TextSecondary
                            )
                        }
                    }
                }
            }

            // System Permissions
            SettingsSectionHeader(title = "System Permissions")

            SettingsRow(
                title = "Accessibility Monitoring Service",
                subtitle = if (isAccessibilityGranted) "Active and monitoring window transitions" else "Disabled — Tap to open Accessibility Settings",
                icon = Icons.Default.Security,
                trailingStatus = if (isAccessibilityGranted) "Active" else "Disabled",
                trailingColor = if (isAccessibilityGranted) NeoPalette.SuccessGreen else NeoPalette.PrimaryGold,
                onClick = {
                    PermissionHelper.openAccessibilitySettings(context)
                },
                testTag = "settings_accessibility_row"
            )

            SettingsRow(
                title = "Display Over Other Apps",
                subtitle = if (isOverlayGranted) "Granted — enables instant lock overlay" else "Denied — Tap to open Overlay Settings",
                icon = Icons.Default.LockClock,
                trailingStatus = if (isOverlayGranted) "Granted" else "Action Needed",
                trailingColor = if (isOverlayGranted) NeoPalette.SuccessGreen else NeoPalette.PrimaryGold,
                onClick = {
                    PermissionHelper.openOverlaySettings(context)
                },
                testTag = "settings_overlay_row"
            )

            SettingsRow(
                title = "Device Administrator",
                subtitle = if (isDeviceAdminActive) "Active — Protects against unauthorized uninstallation" else "Inactive — Tap to activate Device Admin protection",
                icon = Icons.Default.Security,
                trailingStatus = if (isDeviceAdminActive) "Protected" else "Action Needed",
                trailingColor = if (isDeviceAdminActive) NeoPalette.SuccessGreen else NeoPalette.PrimaryGold,
                onClick = {
                    if (isDeviceAdminActive) {
                        showAuthForDeactivateAdmin = true
                    } else {
                        try {
                            context.startActivity(DeviceAdminHelper.createAddDeviceAdminIntent(context))
                        } catch (_: Exception) {
                        }
                    }
                },
                testTag = "settings_device_admin_row"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // DANGER ZONE Section
            SettingsSectionHeader(title = "DANGER ZONE")

            NeoCard(
                shape = NeoShapes.MediumCard,
                accentBorder = NeoPalette.ErrorRed.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_danger_zone_card")
            ) {
                Text(
                    text = "Factory Reset AppLock",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.ErrorRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Permanently clears your master PIN, recovery answer, and protected app list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                NeoButton(
                    text = "Reset All AppLock Data & PIN",
                    onClick = { showResetConfirmDialog = true },
                    icon = Icons.Default.DeleteForever,
                    isDestructive = true,
                    shape = NeoShapes.FullPill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_data_button")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered Heading ABOUT immediately below DANGER ZONE
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = NeoPalette.PrimaryGold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
                    .testTag("settings_about_heading")
            )

            NeoCard(
                onClick = { showSecurityLimitationsDialog = true },
                shape = NeoShapes.MediumCard,
                accentBorder = NeoPalette.PrimaryGold.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_about_card")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Smart AppLock • Version 1.0.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hardware-backed AES-256-GCM Vault & Real-Time App Protection",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoPalette.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Developed by Yasir A Khan",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeoPalette.PrimaryCyan,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.testTag("settings_developer_credit")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Change PIN / Authentication Method Dialog
        if (showChangePinDialog) {
            ChangePinDialog(
                preferences = preferences,
                onDismiss = {
                    activeAuthMethod = preferences.getAuthMethod()
                    showChangePinDialog = false
                },
                onSuccess = {
                    activeAuthMethod = preferences.getAuthMethod()
                    showChangePinDialog = false
                }
            )
        }

        // Change Security Question Dialog
        if (showChangeQuestionDialog) {
            ChangeQuestionDialog(
                preferences = preferences,
                onDismiss = { showChangeQuestionDialog = false },
                onSuccess = { showChangeQuestionDialog = false }
            )
        }

        // Limitations Dialog
        if (showSecurityLimitationsDialog) {
            SecurityLimitationsDialog(onDismiss = { showSecurityLimitationsDialog = false })
        }

        // Reset Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                containerColor = NeoPalette.SurfaceDark,
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = NeoPalette.ErrorRed) },
                title = { Text("Reset AppLock?", fontWeight = FontWeight.Bold, color = NeoPalette.TextPrimary) },
                text = {
                    Text(
                        "This will permanently clear your master PIN, security question, and all protected package selections. You will return to first-time setup.",
                        color = NeoPalette.TextSecondary
                    )
                },
                confirmButton = {
                    NeoButton(
                        text = "Reset Everything",
                        isDestructive = true,
                        shape = NeoShapes.FullPill,
                        onClick = {
                            showResetConfirmDialog = false
                            showAuthForReset = true
                        }
                    )
                },
                dismissButton = {
                    NeoButton(
                        text = "Cancel",
                        shape = NeoShapes.FullPill,
                        onClick = { showResetConfirmDialog = false }
                    )
                }
            )
        }

        // Auth challenge for Recovery Question
        if (showAuthForChangeQuestion) {
            AuthChallengeDialog(
                title = "Security Recovery Question",
                subtitle = "Touch fingerprint sensor or enter PIN to modify question",
                preferences = preferences,
                onAuthorized = {
                    showAuthForChangeQuestion = false
                    showChangeQuestionDialog = true
                },
                onDismiss = { showAuthForChangeQuestion = false }
            )
        }

        // Auth challenge for Deactivating Device Admin
        if (showAuthForDeactivateAdmin) {
            AuthChallengeDialog(
                title = "Authorize Admin Deactivation",
                subtitle = "Touch fingerprint sensor or enter PIN to remove Admin privileges",
                preferences = preferences,
                onAuthorized = {
                    DeviceAdminHelper.deactivateDeviceAdmin(context)
                    isDeviceAdminActive = false
                    showAuthForDeactivateAdmin = false
                },
                onDismiss = { showAuthForDeactivateAdmin = false }
            )
        }

        // Auth challenge for Relock Policy
        pendingRelockPolicy?.let { chosenPolicy ->
            AuthChallengeDialog(
                title = "Update Relock Policy",
                subtitle = "Touch fingerprint sensor or enter PIN to update policy",
                preferences = preferences,
                onAuthorized = {
                    preferences.setRelockOnScreenOff(chosenPolicy)
                    relockOnScreenOff = chosenPolicy
                    pendingRelockPolicy = null
                },
                onDismiss = { pendingRelockPolicy = null }
            )
        }

        // Auth challenge for Reset All AppLock Data & PIN
        if (showAuthForReset) {
            AuthChallengeDialog(
                title = "Authorize Master Reset",
                subtitle = "Touch fingerprint sensor or enter PIN to permanently reset",
                preferences = preferences,
                onAuthorized = {
                    showAuthForReset = false
                    preferences.clearAllData()
                    onResetCompleted()
                },
                onDismiss = { showAuthForReset = false }
            )
        }

        // Auth challenge for Calculator Disguise toggle
        pendingCalcDisguiseToggle?.let { targetEnabled ->
            AuthChallengeDialog(
                title = if (targetEnabled) "Enable Calculator Disguise" else "Disable Calculator Disguise",
                subtitle = "Touch fingerprint sensor or enter PIN to update Calculator Disguise",
                preferences = preferences,
                onAuthorized = {
                    pendingCalcDisguiseToggle = null
                    if (targetEnabled && !preferences.isCalculatorSecretConfigured()) {
                        enableDisguiseAfterSecretSetup = true
                        showConfigureCalcSecretDialog = true
                    } else {
                        preferences.setCalculatorDisguiseEnabled(targetEnabled)
                        isCalcDisguiseEnabled = preferences.isCalculatorDisguiseEnabled()
                    }
                },
                onDismiss = { pendingCalcDisguiseToggle = null }
            )
        }

        // Auth challenge for configuring Calculator Secret Sequence
        if (showAuthForCalcSecret) {
            AuthChallengeDialog(
                title = "Configure Secret Sequence",
                subtitle = "Touch fingerprint sensor or enter PIN to set your calculator secret sequence",
                preferences = preferences,
                onAuthorized = {
                    showAuthForCalcSecret = false
                    showConfigureCalcSecretDialog = true
                },
                onDismiss = {
                    showAuthForCalcSecret = false
                    enableDisguiseAfterSecretSetup = false
                }
            )
        }

        // Configure Calculator Secret Sequence Dialog
        if (showConfigureCalcSecretDialog) {
            ConfigureSecretSequenceDialog(
                onDismiss = {
                    showConfigureCalcSecretDialog = false
                    enableDisguiseAfterSecretSetup = false
                },
                onSaveSequence = { digits ->
                    preferences.saveCalculatorSecretSequence(digits)
                    isCalcSecretConfigured = true
                    if (enableDisguiseAfterSecretSetup) {
                        preferences.setCalculatorDisguiseEnabled(true)
                        enableDisguiseAfterSecretSetup = false
                    }
                    isCalcDisguiseEnabled = preferences.isCalculatorDisguiseEnabled()
                    showConfigureCalcSecretDialog = false
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = NeoPalette.PrimaryGold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailingStatus: String? = null,
    trailingColor: Color = NeoPalette.PrimaryGold,
    onClick: () -> Unit,
    testTag: String
) {
    NeoCard(
        onClick = onClick,
        shape = NeoShapes.MediumCard,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoIconBadge(
                icon = icon,
                size = 44.dp,
                iconSize = 22.dp,
                isGlowing = true
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary
                )
            }

            if (trailingStatus != null) {
                NeoStatusBadge(
                    text = trailingStatus,
                    color = trailingColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = NeoPalette.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private enum class ChangeAuthStep {
    SELECT_METHOD,
    VERIFY_EXISTING,
    CREATE_NEW,
    CONFIRM_NEW,
    SUCCESS
}

@Composable
internal fun ChangePinDialog(
    preferences: AppLockPreferences,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var step by remember { mutableStateOf(ChangeAuthStep.SELECT_METHOD) }
    val currentConfiguredMethod = remember(preferences) { preferences.getAuthMethod() }
    var targetMethod by remember { mutableStateOf(currentConfiguredMethod) }

    var currentInput by remember { mutableStateOf("") }
    var newCredential by remember { mutableStateOf("") }
    var confirmCredential by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(preferences.getRemainingLockoutSeconds()) }

    val isBiometricSupported = remember(activity) {
        activity != null && BiometricHelper.isBiometricAvailable(activity)
    }
    val isBiometricEnabled = isBiometricSupported && preferences.isBiometricEnabled()

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            lockoutSeconds = preferences.getRemainingLockoutSeconds()
        }
    }

    val isBiometricConsumed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun triggerExistingBiometricVerification() {
        if (activity != null && isBiometricEnabled && lockoutSeconds == 0) {
            isBiometricConsumed.set(false)
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Verify Current ${currentConfiguredMethod.displayName}",
                subtitle = "Confirm your identity to set up ${targetMethod.displayName}",
                negativeButtonText = "Use ${currentConfiguredMethod.displayName}",
                onSuccess = {
                    if (isBiometricConsumed.compareAndSet(false, true)) {
                        BiometricHelper.cancelCurrentPrompt()
                        preferences.resetFailedAttempts()
                        errorMsg = null
                        currentInput = ""
                        step = ChangeAuthStep.CREATE_NEW
                    }
                },
                onError = { _, _ -> }
            )
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            BiometricHelper.cancelCurrentPrompt()
        }
    }

    fun handleFailedExistingVerification(message: String) {
        preferences.recordFailedAttempt()
        lockoutSeconds = preferences.getRemainingLockoutSeconds()
        errorMsg = if (lockoutSeconds > 0) {
            "Locked out for $lockoutSeconds seconds"
        } else {
            message
        }
    }

    val dialogTitle = when (step) {
        ChangeAuthStep.SELECT_METHOD -> "Select Authentication Method"
        ChangeAuthStep.VERIFY_EXISTING -> "Verify Current ${currentConfiguredMethod.displayName}"
        ChangeAuthStep.CREATE_NEW -> "Create ${targetMethod.displayName}"
        ChangeAuthStep.CONFIRM_NEW -> "Confirm ${targetMethod.displayName}"
        ChangeAuthStep.SUCCESS -> "Authentication Updated"
    }

    val dialogSubtitle = when (step) {
        ChangeAuthStep.SELECT_METHOD -> "Choose how you want to unlock Smart AppLock and protected areas"
        ChangeAuthStep.VERIFY_EXISTING -> "Enter your current ${currentConfiguredMethod.displayName} to continue"
        ChangeAuthStep.CREATE_NEW -> when (targetMethod) {
            AuthMethod.PIN_4 -> "Enter a new 4-digit numeric PIN"
            AuthMethod.PIN_6 -> "Enter a new 6-digit numeric PIN"
            AuthMethod.PATTERN -> "Connect at least 4 dots on the 3×3 pattern grid"
            AuthMethod.PASSWORD -> "Enter a new password (1–32 characters)"
        }
        ChangeAuthStep.CONFIRM_NEW -> when (targetMethod) {
            AuthMethod.PIN_4 -> "Re-enter your new 4-digit PIN to confirm"
            AuthMethod.PIN_6 -> "Re-enter your new 6-digit PIN to confirm"
            AuthMethod.PATTERN -> "Draw the same pattern again to confirm"
            AuthMethod.PASSWORD -> "Re-enter your new password to confirm"
        }
        ChangeAuthStep.SUCCESS -> "${targetMethod.displayName} is now active across Smart AppLock"
    }

    AlertDialog(
        onDismissRequest = {
            if (step == ChangeAuthStep.SUCCESS) onSuccess() else onDismiss()
        },
        containerColor = NeoPalette.SurfaceDark,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dialogSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    ChangeAuthStep.SELECT_METHOD -> {
                        val methodOptions = listOf(
                            Triple(AuthMethod.PIN_4, "4-Digit PIN", Icons.Default.Pin),
                            Triple(AuthMethod.PIN_6, "6-Digit PIN", Icons.Default.Dialpad),
                            Triple(AuthMethod.PATTERN, "Pattern Lock", Icons.Default.GridOn),
                            Triple(AuthMethod.PASSWORD, "Password", Icons.Default.Password)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            methodOptions.forEach { (method, label, icon) ->
                                val isCurrent = method == currentConfiguredMethod
                                NeoCard(
                                    onClick = {
                                        targetMethod = method
                                        currentInput = ""
                                        newCredential = ""
                                        confirmCredential = ""
                                        errorMsg = null
                                        step = if (preferences.isCredentialConfigured()) {
                                            ChangeAuthStep.VERIFY_EXISTING
                                        } else {
                                            ChangeAuthStep.CREATE_NEW
                                        }
                                    },
                                    shape = NeoShapes.MediumCard,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_method_option_${method.storageKey}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        NeoIconBadge(
                                            icon = icon,
                                            size = 40.dp,
                                            iconSize = 20.dp,
                                            isGlowing = isCurrent
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoPalette.TextPrimary
                                            )
                                            Text(
                                                text = when (method) {
                                                    AuthMethod.PIN_4 -> "4 numeric digits"
                                                    AuthMethod.PIN_6 -> "6 numeric digits"
                                                    AuthMethod.PATTERN -> "3×3 grid (min 4 dots)"
                                                    AuthMethod.PASSWORD -> "Text password (up to 32 chars)"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NeoPalette.TextSecondary
                                            )
                                        }
                                        if (isCurrent) {
                                            NeoStatusBadge(
                                                text = "ACTIVE",
                                                color = NeoPalette.PrimaryGold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = NeoPalette.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ChangeAuthStep.VERIFY_EXISTING -> {
                        if (errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.ErrorRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("change_auth_error_text")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        when (currentConfiguredMethod) {
                            AuthMethod.PIN_4, AuthMethod.PIN_6 -> {
                                val existingPinLen = if (currentConfiguredMethod == AuthMethod.PIN_6) 6 else preferences.getPinLength()
                                PinDots(
                                    pinLength = existingPinLen,
                                    enteredLength = currentInput.length,
                                    isError = errorMsg != null
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                PinKeypad(
                                    buttonSize = 60.dp,
                                    lockoutSeconds = lockoutSeconds,
                                    onDigitClick = { digit ->
                                        if (lockoutSeconds > 0) return@PinKeypad
                                        if (currentInput.length < existingPinLen) {
                                            val next = currentInput + digit
                                            currentInput = next
                                            errorMsg = null
                                            if (next.length == existingPinLen) {
                                                if (preferences.verifyCredential(next)) {
                                                    preferences.resetFailedAttempts()
                                                    currentInput = ""
                                                    errorMsg = null
                                                    step = ChangeAuthStep.CREATE_NEW
                                                } else {
                                                    handleFailedExistingVerification("Incorrect current PIN")
                                                    currentInput = ""
                                                }
                                            }
                                        }
                                    },
                                    onDeleteClick = {
                                        if (lockoutSeconds > 0) return@PinKeypad
                                        if (currentInput.isNotEmpty()) {
                                            currentInput = currentInput.dropLast(1)
                                            errorMsg = null
                                        }
                                    },
                                    extraLeftAction = {
                                        if (isBiometricEnabled) {
                                            NeoCircularButton(
                                                onClick = { triggerExistingBiometricVerification() },
                                                size = 56.dp,
                                                isGoldSolid = true,
                                                modifier = Modifier.testTag("change_auth_biometric_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Verify with Fingerprint",
                                                    tint = NeoPalette.TextInverse,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            AuthMethod.PATTERN -> {
                                PatternLockGrid(
                                    enabled = lockoutSeconds == 0,
                                    isError = errorMsg != null,
                                    gridSize = 216.dp,
                                    onPatternStart = { errorMsg = null },
                                    onPatternTooShort = {
                                        errorMsg = "Connect at least 4 dots"
                                    },
                                    onPatternComplete = { drawnPattern ->
                                        if (lockoutSeconds > 0) return@PatternLockGrid
                                        if (preferences.verifyCredential(drawnPattern)) {
                                            preferences.resetFailedAttempts()
                                            errorMsg = null
                                            step = ChangeAuthStep.CREATE_NEW
                                        } else {
                                            handleFailedExistingVerification("Incorrect current pattern")
                                        }
                                    }
                                )
                                if (isBiometricEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BiometricActionRow(
                                        onBiometricClick = { triggerExistingBiometricVerification() },
                                        enabled = lockoutSeconds == 0,
                                        testTag = "change_auth_biometric_button"
                                    )
                                }
                            }

                            AuthMethod.PASSWORD -> {
                                PasswordAuthInput(
                                    value = currentInput,
                                    onValueChange = {
                                        currentInput = it
                                        errorMsg = null
                                    },
                                    enabled = lockoutSeconds == 0,
                                    isError = errorMsg != null,
                                    label = "Current Password",
                                    submitButtonText = "Verify Current Password",
                                    onSubmit = {
                                        if (lockoutSeconds > 0) return@PasswordAuthInput
                                        if (currentInput.isEmpty()) {
                                            errorMsg = "Password cannot be empty"
                                            return@PasswordAuthInput
                                        }
                                        if (preferences.verifyCredential(currentInput)) {
                                            preferences.resetFailedAttempts()
                                            currentInput = ""
                                            errorMsg = null
                                            step = ChangeAuthStep.CREATE_NEW
                                        } else {
                                            handleFailedExistingVerification("Incorrect current password")
                                            currentInput = ""
                                        }
                                    }
                                )
                                if (isBiometricEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BiometricActionRow(
                                        onBiometricClick = { triggerExistingBiometricVerification() },
                                        enabled = lockoutSeconds == 0,
                                        testTag = "change_auth_biometric_button"
                                    )
                                }
                            }
                        }
                    }

                    ChangeAuthStep.CREATE_NEW, ChangeAuthStep.CONFIRM_NEW -> {
                        val isCreate = step == ChangeAuthStep.CREATE_NEW
                        if (errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.ErrorRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("change_auth_error_text")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        when (targetMethod) {
                            AuthMethod.PIN_4, AuthMethod.PIN_6 -> {
                                val requiredPinLen = if (targetMethod == AuthMethod.PIN_6) 6 else 4
                                val activePinValue = if (isCreate) newCredential else confirmCredential

                                PinDots(
                                    pinLength = requiredPinLen,
                                    enteredLength = activePinValue.length,
                                    isError = errorMsg != null
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                PinKeypad(
                                    buttonSize = 60.dp,
                                    onDigitClick = { digit ->
                                        if (isCreate) {
                                            if (newCredential.length < requiredPinLen) {
                                                val next = newCredential + digit
                                                newCredential = next
                                                errorMsg = null
                                                if (next.length == requiredPinLen) {
                                                    confirmCredential = ""
                                                    step = ChangeAuthStep.CONFIRM_NEW
                                                }
                                            }
                                        } else {
                                            if (confirmCredential.length < requiredPinLen) {
                                                val next = confirmCredential + digit
                                                confirmCredential = next
                                                errorMsg = null
                                                if (next.length == requiredPinLen) {
                                                    if (next == newCredential) {
                                                        preferences.saveAuthCredential(targetMethod, newCredential)
                                                        step = ChangeAuthStep.SUCCESS
                                                    } else {
                                                        errorMsg = "PINs do not match. Please try again."
                                                        confirmCredential = ""
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDeleteClick = {
                                        if (isCreate) {
                                            if (newCredential.isNotEmpty()) newCredential = newCredential.dropLast(1)
                                        } else {
                                            if (confirmCredential.isNotEmpty()) confirmCredential = confirmCredential.dropLast(1)
                                        }
                                        errorMsg = null
                                    }
                                )
                            }

                            AuthMethod.PATTERN -> {
                                androidx.compose.runtime.key(step) {
                                    PatternLockGrid(
                                        isError = errorMsg != null,
                                        gridSize = 216.dp,
                                        onPatternStart = { errorMsg = null },
                                        onPatternTooShort = {
                                            errorMsg = "Connect at least 4 dots"
                                        },
                                        onPatternComplete = { drawnPattern ->
                                            val normalizedDrawn = AppLockPreferences.normalizePatternString(drawnPattern)
                                            if (!AppLockPreferences.isValidCredential(AuthMethod.PATTERN, normalizedDrawn)) {
                                                errorMsg = "Connect at least 4 dots"
                                                return@PatternLockGrid
                                            }
                                            when (step) {
                                                ChangeAuthStep.CREATE_NEW -> {
                                                    newCredential = normalizedDrawn
                                                    confirmCredential = ""
                                                    errorMsg = null
                                                    step = ChangeAuthStep.CONFIRM_NEW
                                                }
                                                ChangeAuthStep.CONFIRM_NEW -> {
                                                    val normalizedFirst = AppLockPreferences.normalizePatternString(newCredential)
                                                    if (normalizedDrawn == normalizedFirst && normalizedFirst.isNotEmpty()) {
                                                        preferences.saveAuthCredential(AuthMethod.PATTERN, normalizedFirst)
                                                        newCredential = ""
                                                        confirmCredential = ""
                                                        errorMsg = null
                                                        step = ChangeAuthStep.SUCCESS
                                                    } else {
                                                        errorMsg = "Patterns do not match. Try again."
                                                        confirmCredential = ""
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        }
                                    )
                                }
                            }

                            AuthMethod.PASSWORD -> {
                                val currentPwdVal = if (isCreate) newCredential else confirmCredential
                                PasswordAuthInput(
                                    value = currentPwdVal,
                                    onValueChange = { updated ->
                                        if (isCreate) {
                                            newCredential = updated
                                        } else {
                                            confirmCredential = updated
                                        }
                                        errorMsg = null
                                    },
                                    isError = errorMsg != null,
                                    label = if (isCreate) "New Password" else "Confirm New Password",
                                    placeholder = "1 to 32 characters",
                                    submitButtonText = if (isCreate) "Continue" else "Save Password",
                                    onSubmit = {
                                        if (isCreate) {
                                            if (!AppLockPreferences.isValidCredential(AuthMethod.PASSWORD, newCredential)) {
                                                errorMsg = "Password must be between 1 and 32 characters"
                                                return@PasswordAuthInput
                                            }
                                            confirmCredential = ""
                                            errorMsg = null
                                            step = ChangeAuthStep.CONFIRM_NEW
                                        } else {
                                            if (confirmCredential.isEmpty()) {
                                                errorMsg = "Please re-enter your password to confirm"
                                                return@PasswordAuthInput
                                            }
                                            if (confirmCredential == newCredential) {
                                                preferences.saveAuthCredential(AuthMethod.PASSWORD, newCredential)
                                                step = ChangeAuthStep.SUCCESS
                                            } else {
                                                errorMsg = "Passwords do not match. Please try again."
                                                confirmCredential = ""
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    ChangeAuthStep.SUCCESS -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = NeoPalette.SuccessGreen,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${targetMethod.displayName} saved successfully!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeoPalette.PrimaryGold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("change_auth_success_text")
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == ChangeAuthStep.SUCCESS) {
                NeoButton(
                    text = "Done",
                    isPrimaryGlowing = true,
                    shape = NeoShapes.FullPill,
                    onClick = onSuccess,
                    modifier = Modifier.testTag("change_auth_done_button")
                )
            }
        },
        dismissButton = {
            if (step != ChangeAuthStep.SUCCESS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step != ChangeAuthStep.SELECT_METHOD) {
                        NeoButton(
                            text = "Back",
                            shape = NeoShapes.FullPill,
                            onClick = {
                                errorMsg = null
                                currentInput = ""
                                newCredential = ""
                                confirmCredential = ""
                                step = ChangeAuthStep.SELECT_METHOD
                            },
                            modifier = Modifier.testTag("change_auth_back_button")
                        )
                    }
                    NeoButton(
                        text = "Cancel",
                        shape = NeoShapes.FullPill,
                        onClick = onDismiss,
                        modifier = Modifier.testTag("change_auth_cancel_button")
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeQuestionDialog(
    preferences: AppLockPreferences,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val defaultQuestions = listOf(
        "What was the name of your first pet?",
        "What city were you born in?",
        "What was the model of your first car?",
        "What is your mother's maiden name?",
        "What was the name of your elementary school?"
    )
    var selectedQuestion by remember { mutableStateOf(preferences.getSecurityQuestion() ?: defaultQuestions[0]) }
    var isExpanded by remember { mutableStateOf(false) }
    var answerText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeoPalette.SurfaceDark,
        title = { Text("Security Recovery Question", color = NeoPalette.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Used to recover access without exposing existing PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedQuestion,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Question") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        defaultQuestions.forEach { q ->
                            DropdownMenuItem(text = { Text(q) }, onClick = { selectedQuestion = q; isExpanded = false })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it; errorMsg = null },
                    label = { Text("New Answer") },
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = {
                        if (errorMsg != null) Text(errorMsg ?: "", color = NeoPalette.ErrorRed)
                        else Text("Case-insensitive, stored as cryptographic hash.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            NeoButton(
                text = "Save",
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
                onClick = {
                    if (answerText.trim().length < 2) {
                        errorMsg = "Answer must be at least 2 characters"
                        return@NeoButton
                    }
                    preferences.saveRecoveryQuestion(selectedQuestion, answerText)
                    onSuccess()
                }
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

@Composable
private fun SecurityLimitationsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeoPalette.SurfaceDark,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = NeoPalette.PrimaryGold) },
        title = { Text("Smart AppLock Architecture", color = NeoPalette.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Developed by Yasir A Khan • Version 1.0.0",
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.PrimaryGold,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("How AppLock Works:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = NeoPalette.TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Uses Android AccessibilityService (TYPE_WINDOW_STATE_CHANGED) to detect which app window transitions to the foreground.\n" +
                    "• Configured with canRetrieveWindowContent=\"false\" to strictly guarantee zero access to on-screen text or private data.\n" +
                    "• Files inside Hide Files are encrypted with AES-256-GCM backed by Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary
                )
            }
        },
        confirmButton = {
            NeoButton(
                text = "Understood",
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun ConfigureSecretSequenceDialog(
    onDismiss: () -> Unit,
    onSaveSequence: (String) -> Unit
) {
    var firstSequence by remember { mutableStateOf("") }
    var confirmSequence by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeoPalette.SurfaceDark,
        icon = {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = null,
                tint = NeoPalette.PrimaryGold
            )
        },
        title = {
            Text(
                text = "Calculator Secret Sequence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a 4 to 8 digit secret sequence. In the calculator, typing these digits followed by '=' will trigger your Master PIN / Fingerprint prompt to unlock the Vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = firstSequence,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }.take(8)
                        firstSequence = digitsOnly
                        errorMsg = null
                    },
                    label = { Text("Secret Digits (4–8 digits)") },
                    suffix = { Text("=", fontWeight = FontWeight.Bold, color = NeoPalette.PrimaryGold) },
                    singleLine = true,
                    isError = errorMsg != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_secret_first_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmSequence,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }.take(8)
                        confirmSequence = digitsOnly
                        errorMsg = null
                    },
                    label = { Text("Confirm Secret Digits") },
                    suffix = { Text("=", fontWeight = FontWeight.Bold, color = NeoPalette.PrimaryGold) },
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = {
                        if (errorMsg != null) {
                            Text(errorMsg ?: "", color = NeoPalette.ErrorRed)
                        } else {
                            Text("Trigger preview: ${if (firstSequence.isNotEmpty()) "${firstSequence}=" else "e.g. 1984="}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_secret_confirm_input")
                )
            }
        },
        confirmButton = {
            NeoButton(
                text = "Save Sequence",
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
                onClick = {
                    if (firstSequence.length < 4) {
                        errorMsg = "Secret sequence must be at least 4 digits"
                        return@NeoButton
                    }
                    if (firstSequence != confirmSequence) {
                        errorMsg = "Sequences do not match"
                        return@NeoButton
                    }
                    onSaveSequence(firstSequence)
                },
                modifier = Modifier.testTag("calc_secret_save_button")
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
