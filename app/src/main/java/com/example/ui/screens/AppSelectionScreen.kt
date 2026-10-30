package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppInfo
import com.example.data.repository.AppDiscoveryRepository
import com.example.data.security.AppLockPreferences
import com.example.ui.common.AuthChallengeDialog
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoInsetBox
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import kotlinx.coroutines.launch

private enum class FilterTab {
    ALL,
    PROTECTED,
    UNPROTECTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    preferences: AppLockPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToFiles: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isManagementSessionAuthenticated: Boolean = false,
    onManagementSessionAuthorized: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { AppDiscoveryRepository(context) }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(FilterTab.ALL) }
    var pendingCriticalAppToLock by remember { mutableStateOf<AppInfo?>(null) }

    // Session-scoped authentication state:
    // Authenticate once upon entering the protected-app management screen.
    // Remains active while the user stays in this screen, and resets on exit.
    var isSessionAuthenticated by remember(isManagementSessionAuthenticated) {
        mutableStateOf(isManagementSessionAuthenticated || !preferences.isPinConfigured())
    }
    var showSessionAuthDialog by remember {
        mutableStateOf(preferences.isPinConfigured() && !isSessionAuthenticated)
    }
    var pendingPostAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Multi-app selection state for locking multiple apps in a single operation
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedPackagesToLock by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun ensureAuthenticatedThen(action: () -> Unit) {
        if (isSessionAuthenticated || !preferences.isPinConfigured()) {
            action()
        } else {
            pendingPostAuthAction = action
            showSessionAuthDialog = true
        }
    }

    fun applySingleAppToggle(targetApp: AppInfo, toLock: Boolean) {
        if (toLock && targetApp.isCriticalSystemComponent) {
            pendingCriticalAppToLock = targetApp
        } else {
            preferences.setPackageProtected(targetApp.packageName, toLock)
            apps = apps.map {
                if (it.packageName == targetApp.packageName) it.copy(isProtected = toLock)
                else it
            }
        }
    }

    fun applyBatchLockSelectedApps() {
        if (selectedPackagesToLock.isEmpty()) {
            isMultiSelectMode = false
            return
        }
        val toLockSet = selectedPackagesToLock
        preferences.addProtectedPackages(toLockSet)
        apps = apps.map { app ->
            if (toLockSet.contains(app.packageName)) app.copy(isProtected = true)
            else app
        }
        selectedPackagesToLock = emptySet()
        isMultiSelectMode = false
    }

    fun loadApps() {
        scope.launch {
            isLoading = true
            val protectedSet = preferences.getProtectedPackages()
            val list = repository.getInstalledLaunchableApps(protectedSet)
            apps = list
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    val filteredApps = remember(apps, searchQuery, currentFilter, isMultiSelectMode) {
        apps.filter { app ->
            val matchesSearch = app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = if (isMultiSelectMode) {
                !app.isProtected
            } else {
                when (currentFilter) {
                    FilterTab.ALL -> true
                    FilterTab.PROTECTED -> app.isProtected
                    FilterTab.UNPROTECTED -> !app.isProtected
                }
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isMultiSelectMode) {
                            "Select Apps (${selectedPackagesToLock.size})"
                        } else {
                            "App Lock"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isMultiSelectMode) {
                                isMultiSelectMode = false
                                selectedPackagesToLock = emptySet()
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("app_selection_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeoPalette.TextPrimary
                        )
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        val unlockedPackages = remember(filteredApps) {
                            filteredApps.filter { !it.isProtected && !it.isCriticalSystemComponent }
                                .map { it.packageName }
                                .toSet()
                        }
                        val allSelected = unlockedPackages.isNotEmpty() &&
                                unlockedPackages.all { selectedPackagesToLock.contains(it) }

                        IconButton(
                            onClick = {
                                selectedPackagesToLock = if (allSelected) {
                                    selectedPackagesToLock - unlockedPackages
                                } else {
                                    selectedPackagesToLock + unlockedPackages
                                }
                            },
                            modifier = Modifier.testTag("select_all_apps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All",
                                tint = if (allSelected) NeoPalette.PrimaryGold else NeoPalette.TextPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.testTag("app_search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Apps",
                            tint = if (showSearchBar || searchQuery.isNotEmpty()) NeoPalette.PrimaryGold else NeoPalette.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeoPalette.BackgroundBase
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeoPalette.BackgroundBase)
            ) {
                // "+ Add Apps" or "Lock Selected Apps (N)" glowing gold pill CTA matching Panel 4
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMultiSelectMode) {
                        NeoButton(
                            text = "Cancel",
                            onClick = {
                                isMultiSelectMode = false
                                selectedPackagesToLock = emptySet()
                            },
                            isPrimaryGlowing = false,
                            shape = NeoShapes.FullPill,
                            modifier = Modifier
                                .weight(0.38f)
                                .testTag("cancel_multi_select_button")
                        )
                    }

                    NeoButton(
                        text = if (isMultiSelectMode) {
                            if (selectedPackagesToLock.isEmpty()) "Select Apps to Lock"
                            else "Lock Selected Apps (${selectedPackagesToLock.size})"
                        } else {
                            "+ Add Apps"
                        },
                        onClick = {
                            ensureAuthenticatedThen {
                                if (isMultiSelectMode) {
                                    applyBatchLockSelectedApps()
                                } else {
                                    selectedPackagesToLock = emptySet()
                                    isMultiSelectMode = true
                                    currentFilter = FilterTab.UNPROTECTED
                                }
                            }
                        },
                        isPrimaryGlowing = true,
                        shape = NeoShapes.FullPill,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_apps_cta_button")
                    )
                }

                NeoBottomNavigationBar(
                    currentTab = MainBottomTab.APPS,
                    onSelectTab = { tab ->
                        when (tab) {
                            MainBottomTab.HOME -> onNavigateBack()
                            MainBottomTab.APPS -> {}
                            MainBottomTab.FILES -> onNavigateToFiles()
                            MainBottomTab.SETTINGS -> onNavigateToSettings()
                        }
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
        ) {
            // Search Field
            AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
                Column {
                    NeoInsetBox(
                        shape = NeoShapes.FullPill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = NeoPalette.PrimaryGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = NeoPalette.TextPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("app_search_field"),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search installed apps...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NeoPalette.TextSecondary.copy(alpha = 0.65f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = NeoPalette.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Three Pill Filter Buttons matching Panel 4: "All Apps", "Locked", "Unlocked"
            if (!isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tabs = listOf(
                        Triple(FilterTab.ALL, "All Apps", "filter_all"),
                        Triple(FilterTab.PROTECTED, "Locked", "filter_protected"),
                        Triple(FilterTab.UNPROTECTED, "Unlocked", "filter_unprotected")
                    )
                    tabs.forEach { (tab, label, tag) ->
                        val isSelected = currentFilter == tab
                        NeoButton(
                            onClick = { currentFilter = tab },
                            isPrimaryGlowing = isSelected,
                            shape = NeoShapes.FullPill,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeoPalette.TextInverse else NeoPalette.TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                // Multi-select hint bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap multiple apps to lock them in one step",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoPalette.PrimaryGold
                    )
                    NeoStatusBadge(
                        text = "${selectedPackagesToLock.size} selected",
                        color = NeoPalette.PrimaryGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeoPalette.PrimaryGold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading installed applications...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeoPalette.TextSecondary
                        )
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No applications match \"$searchQuery\""
                        else "No applications in this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeoPalette.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelectedForBatch = selectedPackagesToLock.contains(app.packageName)
                        AppListItem(
                            app = app,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelectedForBatch = isSelectedForBatch,
                            onToggleProtection = { isLocked ->
                                ensureAuthenticatedThen {
                                    if (isMultiSelectMode) {
                                        selectedPackagesToLock = if (isSelectedForBatch) {
                                            selectedPackagesToLock - app.packageName
                                        } else {
                                            selectedPackagesToLock + app.packageName
                                        }
                                    } else {
                                        applySingleAppToggle(app, isLocked)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Single Session Authentication Challenge when entering App Lock Management
        if (showSessionAuthDialog) {
            AuthChallengeDialog(
                title = "App Lock Management",
                subtitle = "Touch fingerprint sensor or enter master PIN once to manage locked apps",
                preferences = preferences,
                onAuthorized = {
                    showSessionAuthDialog = false
                    isSessionAuthenticated = true
                    onManagementSessionAuthorized()
                    val action = pendingPostAuthAction
                    pendingPostAuthAction = null
                    action?.invoke()
                },
                onDismiss = {
                    showSessionAuthDialog = false
                    val hadPendingAction = pendingPostAuthAction != null
                    pendingPostAuthAction = null
                    if (!isSessionAuthenticated && !hadPendingAction) {
                        onNavigateBack()
                    }
                }
            )
        }

        // Warning Dialog for Critical System Components
        pendingCriticalAppToLock?.let { criticalApp ->
            AlertDialog(
                onDismissRequest = { pendingCriticalAppToLock = null },
                containerColor = NeoPalette.SurfaceDark,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = NeoPalette.ErrorRed
                    )
                },
                title = {
                    Text(
                        "Critical Component Warning",
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                },
                text = {
                    Text(
                        "Locking ${criticalApp.name} (${criticalApp.packageName}) may interfere with normal device operation or navigation. Are you sure you want to protect it?",
                        color = NeoPalette.TextSecondary
                    )
                },
                confirmButton = {
                    NeoButton(
                        text = "Lock Anyway",
                        isDestructive = true,
                        shape = NeoShapes.FullPill,
                        onClick = {
                            preferences.setPackageProtected(criticalApp.packageName, true)
                            apps = apps.map {
                                if (it.packageName == criticalApp.packageName) it.copy(isProtected = true)
                                else it
                            }
                            pendingCriticalAppToLock = null
                        }
                    )
                },
                dismissButton = {
                    NeoButton(
                        text = "Cancel",
                        shape = NeoShapes.FullPill,
                        onClick = { pendingCriticalAppToLock = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    isMultiSelectMode: Boolean = false,
    isSelectedForBatch: Boolean = false,
    onToggleProtection: (Boolean) -> Unit
) {
    val isHighlighted = if (isMultiSelectMode) isSelectedForBatch else app.isProtected
    NeoCard(
        onClick = { onToggleProtection(!app.isProtected) },
        shape = NeoShapes.MediumCard,
        accentBorder = if (isHighlighted) NeoPalette.PrimaryGold.copy(alpha = 0.45f) else null,
        elevation = if (isHighlighted) 6.dp else 3.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon in rounded square container matching Panel 4
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeoPalette.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (app.iconBitmap != null) {
                    Image(
                        bitmap = app.iconBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = NeoPalette.PrimaryGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // App Name & Subtitle ("Block your app" / "Keep it safe")
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.isCriticalSystemComponent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        NeoStatusBadge(
                            text = "System",
                            color = NeoPalette.ErrorRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isMultiSelectMode && isSelectedForBatch -> "Selected to lock"
                        isMultiSelectMode -> "Tap to select"
                        app.isProtected -> "Keep it safe"
                        else -> "Block your app"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMultiSelectMode && isSelectedForBatch) NeoPalette.PrimaryGold else NeoPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Golden Padlock / Multi-Select Toggle Icon matching Panel 4
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(
                        elevation = if (isHighlighted) 6.dp else 1.dp,
                        shape = CircleShape,
                        ambientColor = if (isHighlighted) NeoPalette.PrimaryGold else Color.Black,
                        spotColor = if (isHighlighted) NeoPalette.PrimaryGold else Color.Black
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isHighlighted) {
                                listOf(Color(0xFF33280B), Color(0xFF171512))
                            } else {
                                listOf(NeoPalette.SurfaceElevated, NeoPalette.SurfaceDark)
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isHighlighted) NeoPalette.PrimaryGold.copy(alpha = 0.6f) else Color(0x28FFFFFF),
                        shape = CircleShape
                    )
                    .clickable { onToggleProtection(!app.isProtected) }
                    .testTag("app_switch_${app.packageName}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isMultiSelectMode && isSelectedForBatch -> Icons.Default.Check
                        isMultiSelectMode -> Icons.Default.LockOpen
                        app.isProtected -> Icons.Default.Lock
                        else -> Icons.Default.LockOpen
                    },
                    contentDescription = when {
                        isMultiSelectMode && isSelectedForBatch -> "Selected"
                        isMultiSelectMode -> "Not Selected"
                        app.isProtected -> "Locked"
                        else -> "Unlocked"
                    },
                    tint = if (isHighlighted) NeoPalette.PrimaryGold else NeoPalette.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
