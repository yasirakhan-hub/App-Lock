package com.example.ui.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.vault.DuplicateVaultItemException
import com.example.data.vault.VaultCategory
import com.example.data.vault.VaultItem
import com.example.data.vault.VaultRepository
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoInsetBox
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit,
    onOpenDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { VaultRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedCategoryName by rememberSaveable { mutableStateOf(VaultCategory.PICTURE.name) }
    val selectedCategory = remember(selectedCategoryName) {
        runCatching { VaultCategory.valueOf(selectedCategoryName) }.getOrDefault(VaultCategory.PICTURE)
    }

    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedItemForDetail by remember { mutableStateOf<VaultItem?>(null) }
    var itemToExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<VaultItem?>(null) }
    var showImportSuccessDialog by remember { mutableStateOf<List<VaultItem>?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isErrorStatus by rememberSaveable { mutableStateOf(false) }

    fun refreshItems() {
        scope.launch {
            isLoading = true
            items = repository.getItems(selectedCategory)
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory) {
        refreshItems()
    }

    BackHandler {
        onNavigateBack()
    }

    fun handleBatchImport(uris: List<Uri>, mediaLabel: String) {
        if (uris.isEmpty()) {
            // User cancelled system picker; remain on VaultScreen without error
            return
        }
        scope.launch {
            isLoading = true
            val importedList = mutableListOf<VaultItem>()
            var duplicateCount = 0
            var failureCount = 0

            uris.forEach { uri ->
                val result = repository.importFromUri(uri)
                result.onSuccess { importedItem ->
                    importedList.add(importedItem)
                }
                result.onFailure { error ->
                    if (error is DuplicateVaultItemException) {
                        duplicateCount++
                    } else {
                        failureCount++
                    }
                }
            }

            // If imported items belong to a single category different from current tab, switch to it
            if (importedList.isNotEmpty() && importedList.none { it.category == selectedCategory }) {
                selectedCategoryName = importedList.first().category.name
            }

            items = repository.getItems(
                runCatching { VaultCategory.valueOf(selectedCategoryName) }.getOrDefault(selectedCategory)
            )
            isLoading = false

            when {
                importedList.isNotEmpty() -> {
                    showImportSuccessDialog = importedList
                    if (failureCount > 0 || duplicateCount > 0) {
                        val parts = mutableListOf<String>()
                        parts.add("Imported ${importedList.size} $mediaLabel")
                        if (duplicateCount > 0) parts.add("$duplicateCount duplicate(s) skipped")
                        if (failureCount > 0) parts.add("$failureCount failed to encrypt")
                        isErrorStatus = failureCount > 0
                        statusMessage = parts.joinToString(" • ")
                    } else {
                        isErrorStatus = false
                        statusMessage = null
                    }
                }
                failureCount > 0 && duplicateCount > 0 -> {
                    isErrorStatus = true
                    statusMessage = "Could not import $failureCount $mediaLabel ($duplicateCount duplicate(s) already in vault)."
                }
                failureCount > 0 -> {
                    isErrorStatus = true
                    statusMessage = "Unable to import selected $mediaLabel. Please verify the file is accessible and not empty."
                }
                duplicateCount > 0 -> {
                    isErrorStatus = false
                    statusMessage = "$duplicateCount $mediaLabel already in your encrypted vault."
                }
            }
        }
    }

    // SAF Photo & Video Picker (zero-permission)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val label = if (selectedCategory == VaultCategory.VIDEO) "video(s)" else "photo(s)"
        handleBatchImport(uris, label)
    }

    // SAF Document Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        handleBatchImport(uris, "document(s)")
    }

    // SAF Export Single Item
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        val exportId = itemToExportId
        val item = exportId?.let { id -> repository.getAllItems().find { it.id == id } }
        if (destUri != null && item != null) {
            scope.launch {
                val res = repository.exportItemToUri(item, destUri)
                res.onSuccess {
                    isErrorStatus = false
                    statusMessage = "Exported '${item.originalFileName}' to device storage."
                }.onFailure {
                    isErrorStatus = true
                    statusMessage = "Export failed: Unable to decrypt or write file to destination."
                }
                itemToExportId = null
            }
        } else {
            itemToExportId = null
        }
    }

    // Backup Save Launcher
    var pendingBackupPassphrase by rememberSaveable { mutableStateOf<String?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        val passphrase = pendingBackupPassphrase
        if (destUri != null && passphrase != null) {
            scope.launch {
                isLoading = true
                val outStream = runCatching { context.contentResolver.openOutputStream(destUri) }.getOrNull()
                if (outStream == null) {
                    isErrorStatus = true
                    statusMessage = "Backup failed: Unable to open destination file."
                } else {
                    outStream.use { out ->
                        val res = repository.exportBackup(passphrase, out)
                        res.onSuccess { count ->
                            isErrorStatus = false
                            statusMessage = "Encrypted backup saved ($count file(s))."
                        }.onFailure {
                            isErrorStatus = true
                            statusMessage = "Backup creation failed. Please try again."
                        }
                    }
                }
                isLoading = false
                pendingBackupPassphrase = null
            }
        } else {
            pendingBackupPassphrase = null
        }
    }

    // Restore File Picker Launcher
    var pendingRestorePassphrase by rememberSaveable { mutableStateOf<String?>(null) }
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { srcUri ->
        val passphrase = pendingRestorePassphrase
        if (srcUri != null && passphrase != null) {
            scope.launch {
                isLoading = true
                val inStream = runCatching { context.contentResolver.openInputStream(srcUri) }.getOrNull()
                if (inStream == null) {
                    isErrorStatus = true
                    statusMessage = "Restore failed: Unable to read selected backup file."
                } else {
                    inStream.use { stream ->
                        val res = repository.restoreBackup(passphrase, stream)
                        res.onSuccess { count ->
                            isErrorStatus = false
                            statusMessage = "Restored $count file(s) into vault."
                            refreshItems()
                        }.onFailure {
                            isErrorStatus = true
                            statusMessage = "Restore failed: Invalid passphrase or corrupted backup."
                        }
                    }
                }
                isLoading = false
                pendingRestorePassphrase = null
            }
        } else {
            pendingRestorePassphrase = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Encrypted File Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AES-256-GCM hardware protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("vault_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("vault_menu_button")) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Portable Backup (.salvault)") },
                            leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showBackupDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore Portable Backup") },
                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showRestoreDialog = true
                            }
                        )
                        if (onOpenDashboard != null) {
                            DropdownMenuItem(
                                text = { Text("Open AppLock Dashboard") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenDashboard()
                                }
                            )
                        }
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
        ) {
            // Neomorphic Segmented Category Filter Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                NeoInsetBox(
                    shape = NeoShapes.MediumCard,
                    contentPadding = PaddingValues(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VaultCategory.entries.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            val count = repository.getItemCount(cat)
                            val label = when (cat) {
                                VaultCategory.PICTURE -> "Pictures"
                                VaultCategory.VIDEO -> "Videos"
                                VaultCategory.DOCUMENT -> "Docs"
                            }
                            NeoButton(
                                onClick = { selectedCategoryName = cat.name },
                                isPrimaryGlowing = isSelected,
                                shape = NeoShapes.SmallControl,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vault_tab_${cat.name.lowercase(Locale.US)}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (count > 0) "$label ($count)" else label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Neomorphic Status Alert Banner
            AnimatedVisibility(visible = statusMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val accentColor = if (isErrorStatus) NeoPalette.ErrorRed else NeoPalette.PrimaryCyan
                    NeoCard(
                        shape = NeoShapes.SmallControl,
                        accentBorder = accentColor.copy(alpha = 0.6f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_status_banner")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NeoStatusBadge(
                                text = if (isErrorStatus) "Alert" else "Status",
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = statusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { statusMessage = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Subheader with Quick Import CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (selectedCategory) {
                            VaultCategory.PICTURE -> "Private Photos"
                            VaultCategory.VIDEO -> "Private Videos"
                            VaultCategory.DOCUMENT -> "Private Documents"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Encrypted in app-private storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                NeoButton(
                    text = when (selectedCategory) {
                        VaultCategory.PICTURE -> "+ Import Photos"
                        VaultCategory.VIDEO -> "+ Import Videos"
                        VaultCategory.DOCUMENT -> "+ Import Docs"
                    },
                    icon = Icons.Default.Add,
                    isPrimaryGlowing = true,
                    onClick = {
                        when (selectedCategory) {
                            VaultCategory.PICTURE -> {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            VaultCategory.VIDEO -> {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                            VaultCategory.DOCUMENT -> {
                                docPickerLauncher.launch(arrayOf("*/*"))
                            }
                        }
                    },
                    shape = NeoShapes.SmallControl,
                    modifier = Modifier.testTag("vault_import_button")
                )
            }

            // Main Vault Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Decrypting vault records...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (items.isEmpty()) {
                    EmptyVaultState(
                        category = selectedCategory,
                        onImportClick = {
                            when (selectedCategory) {
                                VaultCategory.PICTURE -> mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                                VaultCategory.VIDEO -> mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                                VaultCategory.DOCUMENT -> docPickerLauncher.launch(arrayOf("*/*"))
                            }
                        }
                    )
                } else {
                    when (selectedCategory) {
                        VaultCategory.PICTURE -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items, key = { it.id }) { item ->
                                    PictureVaultCard(
                                        item = item,
                                        repository = repository,
                                        onClick = { selectedItemForDetail = item }
                                    )
                                }
                            }
                        }
                        VaultCategory.VIDEO, VaultCategory.DOCUMENT -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items, key = { it.id }) { item ->
                                    FileVaultRow(
                                        item = item,
                                        onClick = { selectedItemForDetail = item },
                                        onExport = {
                                            itemToExportId = item.id
                                            exportLauncher.launch(item.originalFileName)
                                        },
                                        onDelete = { showDeleteConfirmDialog = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Import Success & Original Deletion Dialog
        showImportSuccessDialog?.let { importedItems ->
            AlertDialog(
                onDismissRequest = { showImportSuccessDialog = null },
                icon = { Icon(Icons.Default.Security, contentDescription = null, tint = NeoPalette.SuccessGreen) },
                title = { Text("Vault Ingestion Complete", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "${importedItems.size} item(s) securely encrypted into vault.",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Original files on your public device storage remain untouched. Would you like to keep the originals, or delete them manually from your gallery?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    NeoButton(
                        text = "Keep Originals (Default)",
                        isPrimaryGlowing = true,
                        shape = NeoShapes.SmallControl,
                        onClick = { showImportSuccessDialog = null }
                    )
                },
                dismissButton = {
                    NeoButton(
                        text = "Understood",
                        shape = NeoShapes.SmallControl,
                        onClick = {
                            showImportSuccessDialog = null
                            isErrorStatus = false
                            statusMessage = "You can safely delete the original files in your Gallery or Files app."
                        }
                    )
                }
            )
        }

        // Delete Item Confirmation
        showDeleteConfirmDialog?.let { item ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete From Vault?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("This will permanently destroy the encrypted copy of '${item.originalFileName}'. This action cannot be undone.")
                },
                confirmButton = {
                    NeoButton(
                        text = "Delete Permanently",
                        isDestructive = true,
                        shape = NeoShapes.SmallControl,
                        onClick = {
                            repository.deleteItem(item)
                            showDeleteConfirmDialog = null
                            selectedItemForDetail = null
                            refreshItems()
                            isErrorStatus = false
                            statusMessage = "Deleted '${item.originalFileName}'"
                        }
                    )
                },
                dismissButton = {
                    NeoButton(
                        text = "Cancel",
                        shape = NeoShapes.SmallControl,
                        onClick = { showDeleteConfirmDialog = null }
                    )
                }
            )
        }

        // Item Detail / In-Memory Preview Dialog
        selectedItemForDetail?.let { item ->
            ItemDetailModal(
                item = item,
                repository = repository,
                onDismiss = { selectedItemForDetail = null },
                onExport = {
                    itemToExportId = item.id
                    exportLauncher.launch(item.originalFileName)
                },
                onDelete = {
                    showDeleteConfirmDialog = item
                }
            )
        }

        // Portable Backup Dialog
        if (showBackupDialog) {
            BackupPassphraseDialog(
                title = "Export Encrypted Backup (.salvault)",
                subtitle = "Set a passphrase to encrypt your backup container. If lost, this backup cannot be restored.",
                confirmLabel = "Choose Destination",
                onDismiss = { showBackupDialog = false },
                onConfirm = { pass ->
                    showBackupDialog = false
                    pendingBackupPassphrase = pass
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    backupLauncher.launch("SmartAppLock_Vault_$dateStr.salvault")
                }
            )
        }

        // Portable Restore Dialog
        if (showRestoreDialog) {
            BackupPassphraseDialog(
                title = "Restore Encrypted Backup",
                subtitle = "Enter the passphrase used when creating the .salvault container.",
                confirmLabel = "Select File",
                onDismiss = { showRestoreDialog = false },
                onConfirm = { pass ->
                    showRestoreDialog = false
                    pendingRestorePassphrase = pass
                    restorePickerLauncher.launch(arrayOf("*/*"))
                }
            )
        }
    }
}

@Composable
private fun EmptyVaultState(
    category: VaultCategory,
    onImportClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = if (isDarkTheme) NeoPalette.ShadowDarkEdge else Color(0x330F172A)
                )
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
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (category) {
                    VaultCategory.PICTURE -> Icons.Default.PhotoLibrary
                    VaultCategory.VIDEO -> Icons.Default.VideoLibrary
                    VaultCategory.DOCUMENT -> Icons.Default.Description
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when (category) {
                VaultCategory.PICTURE -> "No Pictures Hidden"
                VaultCategory.VIDEO -> "No Videos Hidden"
                VaultCategory.DOCUMENT -> "No Documents Hidden"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Files placed in the vault are encrypted with AES-256-GCM and completely invisible to gallery apps and file managers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        NeoButton(
            text = "Import First File Now",
            icon = Icons.Default.Add,
            isPrimaryGlowing = true,
            onClick = onImportClick,
            shape = NeoShapes.SmallControl
        )
    }
}

@Composable
private fun PictureVaultCard(
    item: VaultItem,
    repository: VaultRepository,
    onClick: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        bitmap = repository.loadDecryptedBitmap(item)
    }

    NeoCard(
        onClick = onClick,
        shape = NeoShapes.MediumCard,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NeoInsetBox(
                shape = NeoShapes.SmallControl,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = item.originalFileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.originalFileName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = formatFileSize(item.fileSizeRaw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FileVaultRow(
    item: VaultItem,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    NeoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.category == VaultCategory.VIDEO) Icons.Default.VideoLibrary else Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalFileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatFileSize(item.fileSizeRaw),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.importedTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onExport, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ItemDetailModal(
    item: VaultItem,
    repository: VaultRepository,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(item.category == VaultCategory.PICTURE) }

    LaunchedEffect(item.id) {
        if (item.category == VaultCategory.PICTURE) {
            bitmap = repository.loadDecryptedBitmap(item)
            isLoadingBitmap = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        NeoCard(
            shape = NeoShapes.LargeCard,
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Encrypted Vault Item",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // In-Memory Decrypted Picture Preview
            if (item.category == VaultCategory.PICTURE) {
                NeoInsetBox(
                    shape = NeoShapes.MediumCard,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingBitmap) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = item.originalFileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "Preview unavailable",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Metadata Rows inside NeoInsetBox
            NeoInsetBox(
                shape = NeoShapes.SmallControl,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DetailRow(label = "Filename", value = item.originalFileName)
                    DetailRow(label = "Category", value = item.category.name)
                    DetailRow(label = "MIME Type", value = item.mimeType)
                    DetailRow(label = "Raw Size", value = "${formatFileSize(item.fileSizeRaw)} (${item.fileSizeRaw} bytes)")
                    DetailRow(label = "Ciphertext", value = item.encryptedFileName)
                    if (item.contentSha256.isNotEmpty()) {
                        DetailRow(label = "SHA-256", value = item.contentSha256.take(16) + "...")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeoButton(
                    text = "Export to Storage",
                    icon = Icons.Default.FileDownload,
                    onClick = onExport,
                    shape = NeoShapes.SmallControl,
                    modifier = Modifier.weight(1f)
                )

                NeoButton(
                    text = "Delete",
                    icon = Icons.Default.Delete,
                    isDestructive = true,
                    onClick = onDelete,
                    shape = NeoShapes.SmallControl,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val isRestoreMode = confirmLabel == "Select File"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; errorMsg = null },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    isError = errorMsg != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isRestoreMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it; errorMsg = null },
                        label = { Text("Confirm Passphrase") },
                        singleLine = true,
                        isError = errorMsg != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            NeoButton(
                text = confirmLabel,
                isPrimaryGlowing = true,
                shape = NeoShapes.SmallControl,
                onClick = {
                    if (passphrase.length < 4) {
                        errorMsg = "Passphrase must be at least 4 characters"
                        return@NeoButton
                    }
                    if (!isRestoreMode && passphrase != confirmPassphrase) {
                        errorMsg = "Passphrases do not match"
                        return@NeoButton
                    }
                    onConfirm(passphrase)
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

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
}
