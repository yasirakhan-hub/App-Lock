package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { AppDiscoveryRepository(context) }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var currentFilter by remember { mutableStateOf(FilterTab.ALL) }
    var pendingCriticalAppToLock by remember { mutableStateOf<AppInfo?>(null) }
    var pendingAuthAppToggle by remember { mutableStateOf<Pair<AppInfo, Boolean>?>(null) }

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

    val filteredApps = remember(apps, searchQuery, currentFilter) {
        apps.filter { app ->
            val matchesSearch = app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (currentFilter) {
                FilterTab.ALL -> true
                FilterTab.PROTECTED -> app.isProtected
                FilterTab.UNPROTECTED -> !app.isProtected
            }
            matchesSearch && matchesFilter
        }
    }

    val protectedCount = remember(apps) { apps.count { it.isProtected } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Protect Applications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$protectedCount of ${apps.size} protected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("app_selection_back_button")) {
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
                .padding(horizontal = 16.dp)
        ) {
            // Search Field (NeoInsetBox owns its internal padding)
            NeoInsetBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_search_field"),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search installed apps...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Neomorphic Segmented Filter Bar
            NeoInsetBox(
                shape = NeoShapes.MediumCard,
                contentPadding = PaddingValues(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        Triple(FilterTab.ALL, "All (${apps.size})", "filter_all"),
                        Triple(FilterTab.PROTECTED, "Locked ($protectedCount)", "filter_protected"),
                        Triple(FilterTab.UNPROTECTED, "Unlocked (${apps.size - protectedCount})", "filter_unprotected")
                    )
                    tabs.forEach { (tab, label, tag) ->
                        val isSelected = currentFilter == tab
                        NeoButton(
                            onClick = { currentFilter = tab },
                            isPrimaryGlowing = isSelected,
                            shape = NeoShapes.SmallControl,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading installed applications...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            onToggleProtection = { isLocked ->
                                pendingAuthAppToggle = Pair(app, isLocked)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // Authentication Challenge before modifying protection
        pendingAuthAppToggle?.let { (targetApp, toLock) ->
            AuthChallengeDialog(
                title = if (toLock) "Protect ${targetApp.name}" else "Unprotect ${targetApp.name}",
                subtitle = if (toLock) "Touch fingerprint sensor or enter master PIN to protect" else "Touch fingerprint sensor or enter master PIN to remove protection",
                preferences = preferences,
                onAuthorized = {
                    pendingAuthAppToggle = null
                    if (toLock && targetApp.isCriticalSystemComponent) {
                        pendingCriticalAppToLock = targetApp
                    } else {
                        preferences.setPackageProtected(targetApp.packageName, toLock)
                        apps = apps.map {
                            if (it.packageName == targetApp.packageName) it.copy(isProtected = toLock)
                            else it
                        }
                    }
                },
                onDismiss = {
                    pendingAuthAppToggle = null
                }
            )
        }

        // Warning Dialog for Critical System Components
        pendingCriticalAppToLock?.let { criticalApp ->
            AlertDialog(
                onDismissRequest = { pendingCriticalAppToLock = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Critical Component Warning", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Locking ${criticalApp.name} (${criticalApp.packageName}) may interfere with normal device operation or navigation. Are you sure you want to protect it?"
                    )
                },
                confirmButton = {
                    NeoButton(
                        text = "Lock Anyway",
                        isDestructive = true,
                        shape = NeoShapes.SmallControl,
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
                        shape = NeoShapes.SmallControl,
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
    onToggleProtection: (Boolean) -> Unit
) {
    NeoCard(
        shape = NeoShapes.MediumCard,
        accentBorder = if (app.isProtected) NeoPalette.PrimaryCyan.copy(alpha = 0.55f) else null,
        elevation = if (app.isProtected) 5.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (app.iconBitmap != null) {
                    Image(
                        bitmap = app.iconBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // App Names & Badges
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = app.isProtected,
                onCheckedChange = onToggleProtection,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("app_switch_${app.packageName}")
            )
        }
    }
}
