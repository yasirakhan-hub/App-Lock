package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.repository.AppDiscoveryRepository
import com.example.data.repository.AppLockManager
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoBrandTitle
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoGoldPadlockEmblem
import com.example.ui.common.NeoIconBadge
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.util.DeviceAdminHelper
import com.example.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appLockManager: AppLockManager,
    isDarkTheme: Boolean = true,
    darkThemeOverride: Boolean? = null,
    onSelectThemeOverride: (Boolean?) -> Unit = {},
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
    val appDiscoveryRepository = remember { AppDiscoveryRepository(context) }

    var isProtectionEnabled by remember { mutableStateOf(prefs.isProtectionEnabled()) }
    var protectedCount by remember { mutableIntStateOf(prefs.getProtectedPackages().size) }
    var totalAppsCount by remember { mutableIntStateOf(52) }
    var isAccessibilityActive by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isOverlayGranted by remember { mutableStateOf(PermissionHelper.isOverlayPermissionGranted(context)) }
    var isDeviceAdminActive by remember { mutableStateOf(DeviceAdminHelper.isDeviceAdminActive(context)) }
    var isSettingsAppProtected by remember { mutableStateOf(prefs.isPackageProtected("com.android.settings")) }
    var showThemeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val protectedSet = prefs.getProtectedPackages()
        protectedCount = protectedSet.size
        val installed = runCatching { appDiscoveryRepository.getInstalledLaunchableApps(protectedSet) }.getOrNull()
        if (!installed.isNullOrEmpty()) {
            totalAppsCount = installed.size
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = PermissionHelper.isAccessibilityServiceEnabled(context)
                isOverlayGranted = PermissionHelper.isOverlayPermissionGranted(context)
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

    val currentThemeIcon = when (darkThemeOverride) {
        true -> Icons.Default.DarkMode
        false -> Icons.Default.LightMode
        null -> Icons.Default.BrightnessAuto
    }
    val currentThemeLabel = when (darkThemeOverride) {
        true -> "Dark"
        false -> "Light"
        null -> "Auto"
    }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeoGoldPadlockEmblem(
                            badgeSize = 36.dp,
                            isOpenShackle = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        NeoBrandTitle(fontSize = 22.sp)
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 10.dp)) {
                        NeoButton(
                            onClick = { showThemeMenu = true },
                            shape = NeoShapes.FullPill,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("home_theme_switcher_button")
                        ) {
                            Icon(
                                imageVector = currentThemeIcon,
                                contentDescription = "Theme Switcher",
                                tint = NeoPalette.PrimaryGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentThemeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeoPalette.TextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false },
                            containerColor = NeoPalette.SurfaceDark
                        ) {
                            val themeOptions: List<Triple<Boolean?, String, ImageVector>> = listOf(
                                Triple(true, "Dark", Icons.Default.DarkMode),
                                Triple(false, "Light", Icons.Default.LightMode),
                                Triple(null, "System Default (Auto)", Icons.Default.BrightnessAuto)
                            )
                            themeOptions.forEach { (overrideValue, optionTitle, optionIcon) ->
                                val isSelected = darkThemeOverride == overrideValue
                                val tag = when (overrideValue) {
                                    true -> "theme_option_dark"
                                    false -> "theme_option_light"
                                    null -> "theme_option_system"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = optionTitle,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) NeoPalette.PrimaryGold else NeoPalette.TextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = optionIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) NeoPalette.PrimaryGold else NeoPalette.TextSecondary
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = NeoPalette.PrimaryGold
                                            )
                                        }
                                    },
                                    onClick = {
                                        showThemeMenu = false
                                        prefs.setDarkThemeOverride(overrideValue)
                                        onSelectThemeOverride(overrideValue)
                                    },
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeoPalette.BackgroundBase
                )
            )
        },
        bottomBar = {
            NeoBottomNavigationBar(
                currentTab = MainBottomTab.HOME,
                onSelectTab = { tab ->
                    when (tab) {
                        MainBottomTab.HOME -> {}
                        MainBottomTab.APPS -> onNavigateToAppSelection()
                        MainBottomTab.FILES -> onNavigateToVault()
                        MainBottomTab.SETTINGS -> onNavigateToSettings()
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (NeoPalette.isDarkMode) {
                            listOf(
                                NeoPalette.BackgroundBase,
                                Color(0xFF110F0A),
                                NeoPalette.BackgroundBase
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Top Summary Card: Apps Protected (12 / 52) matching Panel 3 of the reference image
            NeoCard(
                onClick = onNavigateToAppSelection,
                shape = NeoShapes.LargeCard,
                accentBorder = NeoPalette.PrimaryGold.copy(alpha = 0.35f),
                elevation = 8.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("protected_apps_stat_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoIconBadge(
                        icon = Icons.Default.Security,
                        size = 62.dp,
                        iconSize = 32.dp,
                        isGlowing = true
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apps Protected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoPalette.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = NeoPalette.TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp
                                    )
                                ) {
                                    append("$protectedCount")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = NeoPalette.TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 18.sp
                                    )
                                ) {
                                    append(" / ${maxOf(totalAppsCount, protectedCount)}")
                                }
                            }
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Apps",
                        tint = NeoPalette.TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2x2 Primary Feature Grid matching Panel 3 of the reference image
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeFeatureGridCard(
                    title = "App Lock",
                    subtitle = "Lock your apps",
                    icon = Icons.Default.Apps,
                    onClick = onNavigateToAppSelection,
                    testTag = "nav_select_apps",
                    modifier = Modifier.weight(1f)
                )
                HomeFeatureGridCard(
                    title = "Hide Files",
                    subtitle = "Keep files private",
                    icon = Icons.Default.Folder,
                    onClick = onNavigateToVault,
                    testTag = "nav_vault",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeFeatureGridCard(
                    title = "Calculator Vault",
                    subtitle = "Disguise & Protect",
                    icon = Icons.Default.Calculate,
                    onClick = onNavigateToCalculator,
                    testTag = "nav_calculator",
                    modifier = Modifier.weight(1f)
                )
                HomeFeatureGridCard(
                    title = "Settings",
                    subtitle = "Customize your app",
                    icon = Icons.Default.Settings,
                    onClick = onNavigateToSettings,
                    testTag = "nav_security_settings",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Master Protection Toggle Card
            NeoCard(
                shape = NeoShapes.LargeCard,
                accentBorder = if (isProtectionEnabled) NeoPalette.PrimaryGold.copy(alpha = 0.5f) else null,
                elevation = if (isProtectionEnabled) 6.dp else 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_protection_card")
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
                            icon = if (isProtectionEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            size = 44.dp,
                            iconSize = 22.dp,
                            isGlowing = isProtectionEnabled
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isProtectionEnabled) "Protection Active" else "Protection Paused",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeoPalette.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isProtectionEnabled) {
                                    "$protectedCount apps locked and monitored"
                                } else {
                                    "AppLock is temporarily disabled"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.TextSecondary
                            )
                        }
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
                            checkedThumbColor = NeoPalette.TextInverse,
                            checkedTrackColor = NeoPalette.PrimaryGold,
                            uncheckedThumbColor = NeoPalette.TextSecondary,
                            uncheckedTrackColor = NeoPalette.SurfaceSunken
                        ),
                        modifier = Modifier.testTag("master_protection_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lock Screen Simulator Card
            NeoCard(
                onClick = onNavigateToTestLock,
                shape = NeoShapes.MediumCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nav_test_lock")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulator_stat_card"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoIconBadge(
                        icon = Icons.Default.PlayCircle,
                        size = 44.dp,
                        iconSize = 22.dp,
                        isGlowing = true
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock Screen Simulator",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeoPalette.TextPrimary
                        )
                        Text(
                            text = "Preview & test PIN/fingerprint lock screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoPalette.TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = NeoPalette.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // System Permissions & Service Health Section
            Text(
                text = "Service & Permission Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.PrimaryGold
            )

            Spacer(modifier = Modifier.height(10.dp))

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

            PermissionStatusCard(
                title = "Device Admin (Anti-Force Stop & Uninstall)",
                description = "Grants Device Administrator privileges to prevent unauthorized uninstallation or tampering.",
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
                Spacer(modifier = Modifier.height(10.dp))
                NeoCard(
                    shape = NeoShapes.MediumCard,
                    accentBorder = NeoPalette.PrimaryGold.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protect_settings_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeoPalette.PrimaryGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Block Force-Stop Bypass",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeoPalette.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Lock the Android Settings app so intruders cannot open App Info or tap Force Stop without your PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoPalette.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoButton(
                        text = "Lock Android Settings Now",
                        onClick = {
                            prefs.setPackageProtected("com.android.settings", true)
                            isSettingsAppProtected = true
                            protectedCount = prefs.getProtectedPackages().size
                        },
                        shape = NeoShapes.FullPill,
                        isPrimaryGlowing = true,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Relock all sessions button
            NeoButton(
                onClick = {
                    appLockManager.lockAll()
                },
                shape = NeoShapes.FullPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("relock_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = NeoPalette.PrimaryGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Relock All Sessions Now",
                    color = NeoPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeFeatureGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    NeoCard(
        onClick = onClick,
        shape = NeoShapes.LargeCard,
        elevation = 6.dp,
        contentPadding = PaddingValues(16.dp),
        modifier = modifier
            .aspectRatio(1.04f)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NeoIconBadge(
                icon = icon,
                size = 58.dp,
                iconSize = 28.dp,
                isGlowing = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NeoPalette.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
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
        accentBorder = if (!isGranted) NeoPalette.WarningAmber.copy(alpha = 0.45f) else null,
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
                    tint = if (isGranted) NeoPalette.SuccessGreen else NeoPalette.PrimaryGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary
                )
            }

            NeoStatusBadge(
                text = if (isGranted) "Enabled" else "Required",
                isActive = isGranted,
                activeColor = NeoPalette.SuccessGreen,
                inactiveColor = NeoPalette.PrimaryGold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = NeoPalette.TextSecondary
        )

        if (!isGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            NeoButton(
                text = actionLabel,
                onClick = onAction,
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
