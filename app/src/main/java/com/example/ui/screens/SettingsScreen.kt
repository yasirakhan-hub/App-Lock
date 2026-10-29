package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AppLockPreferences
import com.example.ui.common.AuthChallengeDialog
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.util.DeviceAdminHelper
import com.example.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppLockPreferences,
    onNavigateBack: () -> Unit,
    onResetCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security & Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Section 1: Authentication Settings
            SettingsSectionHeader(title = "Authentication Credentials")

            SettingsRow(
                title = "Change Master PIN",
                subtitle = "Update your current ${preferences.getPinLength()}-digit security PIN",
                icon = Icons.Default.Key,
                onClick = { showAuthForChangePin = true },
                testTag = "settings_change_pin_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                title = "Recovery Security Question",
                subtitle = preferences.getSecurityQuestion() ?: "Not configured",
                icon = Icons.Default.HelpOutline,
                onClick = { showAuthForChangeQuestion = true },
                testTag = "settings_change_question_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

            NeoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_biometric_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkTheme) NeoPalette.SurfaceLight else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDarkTheme) NeoPalette.SpecularBorder else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Fingerprint Unlock",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Use fingerprint authentication as an alternative to PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { checked ->
                            isBiometricEnabled = checked
                            preferences.setBiometricEnabled(checked)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("settings_biometric_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1B: Calculator Disguise & Vault Gateway
            SettingsSectionHeader(title = "Calculator Disguise & Vault Gateway")

            NeoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_calc_disguise_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkTheme) NeoPalette.SurfaceLight else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDarkTheme) NeoPalette.SpecularBorder else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Calculator Disguise",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                NeoStatusBadge(
                                    text = if (isCalcDisguiseEnabled) "Configured & Active" else "Disabled",
                                    color = if (isCalcDisguiseEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open a real calculator when launching Smart AppLock. Enter your secret code + '=' then verify master PIN/fingerprint to open Vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isCalcDisguiseEnabled,
                        onCheckedChange = { checked ->
                            pendingCalcDisguiseToggle = checked
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("settings_calc_disguise_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                title = if (isCalcSecretConfigured) "Change Secret Sequence" else "Set Secret Sequence",
                subtitle = if (isCalcSecretConfigured) {
                    "Configured (${preferences.getCalculatorSecretLength()} digits + '='). Stored as salted hash."
                } else {
                    "Configure a 4–8 digit sequence followed by '=' to trigger the Vault gateway"
                },
                icon = Icons.Default.Key,
                trailingStatus = if (isCalcSecretConfigured) "Configured" else "Not Set",
                trailingColor = if (isCalcSecretConfigured) Color(0xFF10B981) else Color(0xFFF59E0B),
                onClick = {
                    enableDisguiseAfterSecretSetup = false
                    showAuthForCalcSecret = true
                },
                testTag = "settings_calc_secret_row"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Relock Behavior
            SettingsSectionHeader(title = "Relock Policy")

            NeoCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "When should an unlocked application be locked again?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Immediately on app exit (High Security)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Prompts for PIN each time the app is reopened", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("When screen turns off (Convenience)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Keeps apps unlocked until you lock your phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Android Permissions & Service Health
            SettingsSectionHeader(title = "System Permissions")

            SettingsRow(
                title = "Accessibility Monitoring Service",
                subtitle = if (isAccessibilityGranted) "Active and monitoring window transitions" else "Disabled — Tap to open Accessibility Settings",
                icon = Icons.Default.Security,
                trailingStatus = if (isAccessibilityGranted) "Active" else "Disabled",
                trailingColor = if (isAccessibilityGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                onClick = {
                    PermissionHelper.openAccessibilitySettings(context)
                },
                testTag = "settings_accessibility_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                title = "Display Over Other Apps",
                subtitle = if (isOverlayGranted) "Granted — enables instant lock overlay" else "Denied — Tap to open Overlay Settings",
                icon = Icons.Default.LockClock,
                trailingStatus = if (isOverlayGranted) "Granted" else "Action Needed",
                trailingColor = if (isOverlayGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                onClick = {
                    PermissionHelper.openOverlaySettings(context)
                },
                testTag = "settings_overlay_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                title = "Device Administrator (Anti-Force Stop & Uninstall)",
                subtitle = if (isDeviceAdminActive) "Active — Protects against unauthorized uninstallation and tampering" else "Inactive — Tap to activate Device Admin protection",
                icon = Icons.Default.Security,
                trailingStatus = if (isDeviceAdminActive) "Protected" else "Action Needed",
                trailingColor = if (isDeviceAdminActive) Color(0xFF10B981) else Color(0xFFF59E0B),
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

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Security Architecture & Limitations
            SettingsSectionHeader(title = "Security Architecture & Transparency")

            SettingsRow(
                title = "How AppLock Works & Android Limitations",
                subtitle = "Read why AccessibilityService is used, privacy details, and OS limits",
                icon = Icons.Default.Info,
                onClick = { showSecurityLimitationsDialog = true },
                testTag = "settings_limitations_row"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 5: Reset / Clear
            SettingsSectionHeader(title = "Danger Zone")

            NeoButton(
                text = "Reset All AppLock Data & PIN",
                onClick = { showResetConfirmDialog = true },
                icon = Icons.Default.DeleteForever,
                isDestructive = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_reset_data_button")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Section 6: About
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ABOUT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24),
                    letterSpacing = 2.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Developed by Yasir A Khan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
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
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Reset AppLock?", fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently clear your master PIN, security question, and all protected package selections. You will return to first-time setup.") },
                confirmButton = {
                    NeoButton(
                        text = "Reset Everything",
                        isDestructive = true,
                        shape = NeoShapes.SmallControl,
                        onClick = {
                            showResetConfirmDialog = false
                            showAuthForReset = true
                        }
                    )
                },
                dismissButton = {
                    NeoButton(
                        text = "Cancel",
                        shape = NeoShapes.SmallControl,
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailingStatus: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    testTag: String
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    NeoCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDarkTheme) NeoPalette.SurfaceLight else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDarkTheme) NeoPalette.SpecularBorder else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (trailingStatus != null) {
                NeoStatusBadge(
                    text = trailingStatus,
                    color = trailingColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
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
    var step by remember { mutableIntStateOf(2) } // 2: enter new, 3: confirm new
    val pinLength = preferences.getPinLength()
    var currentInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    1 -> "Enter Current PIN"
                    2 -> "Enter New PIN"
                    else -> "Confirm New PIN"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
                    Text(errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))

                PinKeypad(
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
                shape = NeoShapes.SmallControl,
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
        title = { Text("Security Recovery Question") },
        text = {
            Column {
                Text(
                    "Used to recover access without exposing existing PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        if (errorMsg != null) Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
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
                shape = NeoShapes.SmallControl,
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
                shape = NeoShapes.SmallControl,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun SecurityLimitationsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Technical Architecture & Limits") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("How AppLock Works:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Uses Android AccessibilityService (TYPE_WINDOW_STATE_CHANGED) to detect which app window transitions to the foreground.\n" +
                    "• Configured with canRetrieveWindowContent=\"false\" to strictly guarantee zero access to on-screen text or private data.\n" +
                    "• When a protected package opens, displays the full-screen LockActivity before normal use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Why Not Usage Access Alone?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Usage Access polls usage statistics asynchronously. In modern Android, background polling has severe battery limits and 1 to 5-second delays, allowing users to view protected app content before the lock triggers. AccessibilityService provides event-driven, immediate detection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Honest Android System Boundaries:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Standard Android does not allow third-party apps to prevent:\n" +
                    "1. Device Safe Mode: Boots without third-party services.\n" +
                    "2. Force Stop: If an intruder has your device lock and access to Android Settings, they can Force Stop apps unless device owner (MDM) profile is installed.\n" +
                    "3. Aggressive OEM Battery Killers: Xiaomi, Samsung, and Huawei require setting AppLock to 'No battery restrictions' and 'Autostart' to avoid the OS killing background services.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            NeoButton(
                text = "Understood",
                isPrimaryGlowing = true,
                shape = NeoShapes.SmallControl,
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
        icon = {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Calculator Secret Sequence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a 4 to 8 digit secret sequence. In the calculator, typing these digits followed by '=' will trigger your Master PIN / Fingerprint prompt to unlock the Vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    suffix = { Text("=", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
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
                    suffix = { Text("=", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = {
                        if (errorMsg != null) {
                            Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
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
                shape = NeoShapes.SmallControl,
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
                shape = NeoShapes.SmallControl,
                onClick = onDismiss
            )
        }
    )
}

