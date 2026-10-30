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
import com.example.data.security.AppLockPreferences
import com.example.ui.common.AuthChallengeDialog
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoIconBadge
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
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

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangeQuestionDialog by remember { mutableStateOf(false) }
    var showSecurityLimitationsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showConfigureCalcSecretDialog by remember { mutableStateOf(false) }
    var enableDisguiseAfterSecretSetup by remember { mutableStateOf(false) }

    var showAuthForChangePin by remember { mutableStateOf(false) }
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
                subtitle = "Update your security PIN",
                icon = Icons.Default.Person,
                onClick = { showAuthForChangePin = true },
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

        // Change PIN Dialog
        if (showChangePinDialog) {
            ChangePinDialog(
                preferences = preferences,
                onDismiss = { showChangePinDialog = false },
                onSuccess = { showChangePinDialog = false }
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

        // Auth challenge for Change PIN
        if (showAuthForChangePin) {
            AuthChallengeDialog(
                title = "Change Master PIN",
                subtitle = "Touch fingerprint sensor or enter PIN to change master PIN",
                preferences = preferences,
                onAuthorized = {
                    showAuthForChangePin = false
                    showChangePinDialog = true
                },
                onDismiss = { showAuthForChangePin = false }
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

@Composable
private fun ChangePinDialog(
    preferences: AppLockPreferences,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableIntStateOf(2) }
    val pinLength = preferences.getPinLength()
    var currentInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeoPalette.SurfaceDark,
        title = {
            Text(
                text = when (step) {
                    1 -> "Enter Current PIN"
                    2 -> "Enter New PIN"
                    else -> "Confirm New PIN"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.TextPrimary
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val inputLength = when (step) {
                    1 -> currentInput.length
                    2 -> newPin.length
                    else -> confirmPin.length
                }

                PinDots(
                    pinLength = pinLength,
                    enteredLength = inputLength,
                    isError = errorMsg != null
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = NeoPalette.ErrorRed)
                }

                Spacer(modifier = Modifier.height(16.dp))

                PinKeypad(
                    buttonSize = 62.dp,
                    onDigitClick = { digit ->
                        when (step) {
                            1 -> {
                                if (currentInput.length < pinLength) {
                                    val next = currentInput + digit
                                    currentInput = next
                                    errorMsg = null
                                    if (next.length == pinLength) {
                                        if (preferences.verifyPin(next)) {
                                            step = 2
                                        } else {
                                            errorMsg = "Incorrect current PIN"
                                            currentInput = ""
                                        }
                                    }
                                }
                            }
                            2 -> {
                                if (newPin.length < pinLength) {
                                    val next = newPin + digit
                                    newPin = next
                                    errorMsg = null
                                    if (next.length == pinLength) {
                                        step = 3
                                    }
                                }
                            }
                            3 -> {
                                if (confirmPin.length < pinLength) {
                                    val next = confirmPin + digit
                                    confirmPin = next
                                    errorMsg = null
                                    if (next.length == pinLength) {
                                        if (next == newPin) {
                                            preferences.savePin(newPin)
                                            onSuccess()
                                        } else {
                                            errorMsg = "PINs do not match"
                                            confirmPin = ""
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        when (step) {
                            1 -> if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
                            2 -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                            3 -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        }
                        errorMsg = null
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            NeoButton(
                text = "Cancel",
                shape = NeoShapes.FullPill,
                onClick = onDismiss
            )
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
