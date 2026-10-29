package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AppLockManager
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.util.DeviceAdminHelper
import com.example.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appLockManager: AppLockManager,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onNavigateToAppSelection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTestLock: () -> Unit,
    onNavigateToVault: () -> Unit = {},
    onNavigateToCalculator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = appLockManager.preferences

    var isProtectionEnabled by remember { mutableStateOf(prefs.isProtectionEnabled()) }
    var protectedCount by remember { mutableStateOf(prefs.getProtectedPackages().size) }
    var isAccessibilityActive by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isOverlayGranted by remember { mutableStateOf(PermissionHelper.isOverlayPermissionGranted(context)) }
    var isUsageGranted by remember { mutableStateOf(PermissionHelper.isUsageAccessGranted(context)) }
    var isDeviceAdminActive by remember { mutableStateOf(DeviceAdminHelper.isDeviceAdminActive(context)) }
    var isSettingsAppProtected by remember { mutableStateOf(prefs.isPackageProtected("com.android.settings")) }

    // Refresh status when returning to dashboard using Lifecycle events
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = PermissionHelper.isAccessibilityServiceEnabled(context)
                isOverlayGranted = PermissionHelper.isOverlayPermissionGranted(context)
                isUsageGranted = PermissionHelper.isUsageAccessGranted(context)
                isDeviceAdminActive = DeviceAdminHelper.isDeviceAdminActive(context)
                isSettingsAppProtected = prefs.isPackageProtected("com.android.settings")
                protectedCount = prefs.getProtectedPackages().size
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "AppLock",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Smart AppLock",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("dashboard_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = if (isDarkTheme) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
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
            // Master Protection Toggle Card
            NeoCard(
                shape = NeoShapes.LargeCard,
                backgroundColor = if (isProtectionEnabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                accentBorder = if (isProtectionEnabled) NeoPalette.PrimaryCyan.copy(alpha = 0.6f) else null,
                elevation = if (isProtectionEnabled) 6.dp else 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_protection_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isProtectionEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProtectionEnabled) "Protection Active" else "Protection Paused",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isProtectionEnabled) {
                                "$protectedCount apps locked and monitored"
                            } else {
                                "AppLock is temporarily disabled"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isProtectionEnabled,
                        onCheckedChange = { checked ->
                            isProtectionEnabled = checked
                            prefs.setProtectionEnabled(checked)
                            if (!checked) {
                                appLockManager.lockAll()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("master_protection_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Protected Apps",
                    value = "$protectedCount",
                    icon = Icons.Default.Apps,
                    onClick = onNavigateToAppSelection,
                    modifier = Modifier.weight(1f).testTag("protected_apps_stat_card")
                )
                StatCard(
                    title = "Lock Simulator",
                    value = "Test Now",
                    icon = Icons.Default.PlayCircle,
                    onClick = onNavigateToTestLock,
                    modifier = Modifier.weight(1f).testTag("simulator_stat_card")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // System Permissions & Service Health Section
            Text(
                text = "Service & Permission Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Accessibility Service Card
            PermissionStatusCard(
                title = "Accessibility Monitoring Service",
                description = "Required to detect when protected apps are brought to the foreground.",
                isGranted = isAccessibilityActive,
                actionLabel = "Enable in Settings",
                onAction = {
                    PermissionHelper.openAccessibilitySettings(context)
                },
                testTag = "perm_accessibility_card"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Overlay Permission Card
            PermissionStatusCard(
                title = "Display Over Other Apps",
                description = "Required on Android 10+ to display the PIN lock screen instantly over protected apps.",
                isGranted = isOverlayGranted,
                actionLabel = "Grant Permission",
                onAction = {
                    PermissionHelper.openOverlaySettings(context)
                },
                testTag = "perm_overlay_card"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Device Admin Protection Card
            PermissionStatusCard(
                title = "Device Admin (Anti-Force Stop & Uninstall)",
                description = "Grants Device Administrator privileges to prevent unauthorized uninstallation or tampering without your PIN.",
                isGranted = isDeviceAdminActive,
                actionLabel = "Activate Admin",
                onAction = {
                    try {
                        context.startActivity(DeviceAdminHelper.createAddDeviceAdminIntent(context))
                    } catch (_: Exception) {
                    }
                },
                testTag = "perm_device_admin_card"
            )

            if (!isSettingsAppProtected) {
                Spacer(modifier = Modifier.height(12.dp))
                NeoCard(
                    shape = NeoShapes.MediumCard,
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    accentBorder = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().testTag("protect_settings_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Block Force-Stop Bypass",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "To completely prevent intruders from tapping 'Force Stop', lock the Android Settings app. They will not be able to open App Info without your master PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoButton(
                        onClick = {
                            prefs.setPackageProtected("com.android.settings", true)
                            isSettingsAppProtected = true
                            protectedCount = prefs.getProtectedPackages().size
                        },
                        shape = NeoShapes.SmallControl,
                        isPrimaryGlowing = false,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Lock Android Settings Now",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Quick Links
            Text(
                text = "Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            ActionNavRow(
                title = "Select Apps to Protect",
                subtitle = "Choose which apps require PIN verification",
                icon = Icons.Default.Apps,
                onClick = onNavigateToAppSelection,
                testTag = "nav_select_apps"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionNavRow(
                title = "Encrypted File Vault",
                subtitle = "Privately hide photos, videos & sensitive documents",
                icon = Icons.Default.Lock,
                onClick = onNavigateToVault,
                testTag = "nav_vault"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionNavRow(
                title = "Calculator Disguise & Vault",
                subtitle = "Open tactile calculator or test secret vault gateway",
                icon = Icons.Default.Calculate,
                onClick = onNavigateToCalculator,
                testTag = "nav_calculator"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionNavRow(
                title = "Interactive Lock Test",
                subtitle = "Verify PIN entry behavior in safe simulation mode",
                icon = Icons.Default.PlayCircle,
                onClick = onNavigateToTestLock,
                testTag = "nav_test_lock"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionNavRow(
                title = "Security & PIN Options",
                subtitle = "Change PIN, recovery questions, relock policies",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings,
                testTag = "nav_security_settings"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Relock all sessions button
            NeoButton(
                onClick = {
                    appLockManager.lockAll()
                },
                shape = NeoShapes.MediumCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("relock_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Relock All Sessions Now",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        onClick = onClick,
        shape = NeoShapes.LargeCard,
        backgroundColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    NeoCard(
        shape = NeoShapes.MediumCard,
        backgroundColor = MaterialTheme.colorScheme.surface,
        accentBorder = if (!isGranted) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
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
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            NeoStatusBadge(
                text = if (isGranted) "Enabled" else "Required",
                isActive = isGranted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            NeoButton(
                onClick = onAction,
                isPrimaryGlowing = true,
                shape = NeoShapes.SmallControl,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ActionNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    NeoCard(
        onClick = onClick,
        shape = NeoShapes.MediumCard,
        backgroundColor = MaterialTheme.colorScheme.surface,
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
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
