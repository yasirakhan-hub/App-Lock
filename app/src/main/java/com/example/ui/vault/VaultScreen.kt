package com.example.ui.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.vault.DuplicateVaultItemException
import com.example.data.vault.VaultCategory
import com.example.data.vault.VaultItem
import com.example.data.vault.VaultRepository
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoInsetBox
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VaultFilterPill {
    PHOTOS,
    VIDEOS,
    DOCUMENTS,
    OTHERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit,
    onOpenDashboard: (() -> Unit)? = null,
    onNavigateToApps: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onSystemPickerLaunched: () -> Unit = {},
    onSystemPickerFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { VaultRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedPillName by rememberSaveable { mutableStateOf(VaultFilterPill.PHOTOS.name) }
    val selectedPill = remember(selectedPillName) {
        runCatching { VaultFilterPill.valueOf(selectedPillName) }.getOrDefault(VaultFilterPill.PHOTOS)
    }

    val selectedCategory = remember(selectedPill) {
        when (selectedPill) {
            VaultFilterPill.PHOTOS -> VaultCategory.PICTURE
            VaultFilterPill.VIDEOS -> VaultCategory.VIDEO
            VaultFilterPill.DOCUMENTS, VaultFilterPill.OTHERS -> VaultCategory.DOCUMENT
        }
    }

    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var allRecentItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Importing progress state (Panel 6)
    var isImporting by remember { mutableStateOf(false) }
    var importProgressPercent by remember { mutableIntStateOf(0) }
    var importStepIndex by remember { mutableIntStateOf(0) }
    var activeImportJob by remember { mutableStateOf<Job?>(null) }

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
            allRecentItems = repository.getAllItems().sortedByDescending { it.importedTimestamp }
            items = when (selectedPill) {
                VaultFilterPill.PHOTOS -> repository.getItems(VaultCategory.PICTURE)
                VaultFilterPill.VIDEOS -> repository.getItems(VaultCategory.VIDEO)
                VaultFilterPill.DOCUMENTS -> repository.getItems(VaultCategory.DOCUMENT)
                VaultFilterPill.OTHERS -> repository.getAllItems()
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedPill) {
        refreshItems()
    }

    // Immediately clear sensitive vault lists and open previews from memory when leaving Files
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            activeImportJob?.cancel()
            selectedItemForDetail = null
            showDeleteConfirmDialog = null
            showImportSuccessDialog = null
            items = emptyList()
            allRecentItems = emptyList()
        }
    }

    BackHandler {
        if (showImportSuccessDialog != null) {
            showImportSuccessDialog = null
        } else if (isImporting) {
            activeImportJob?.cancel()
            isImporting = false
        } else {
            onNavigateBack()
        }
    }

    fun handleBatchImport(uris: List<Uri>, mediaLabel: String) {
        onSystemPickerFinished()
        if (uris.isEmpty()) {
            // User cancelled system picker; remain on VaultScreen without error
            return
        }
        activeImportJob = scope.launch {
            isImporting = true
            importStepIndex = 0
            importProgressPercent = 18
            delay(120L)

            val importedList = mutableListOf<VaultItem>()
            var duplicateCount = 0
            var failureCount = 0

            importStepIndex = 1
            importProgressPercent = 45

            uris.forEachIndexed { idx, uri ->
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
                importStepIndex = 2
                importProgressPercent = 55 + (((idx + 1) * 35) / uris.size.coerceAtLeast(1))
            }

            importStepIndex = 3
            importProgressPercent = 96
            delay(120L)

            if (importedList.isNotEmpty() && importedList.none { it.category == selectedCategory }) {
                selectedPillName = when (importedList.first().category) {
                    VaultCategory.PICTURE -> VaultFilterPill.PHOTOS.name
                    VaultCategory.VIDEO -> VaultFilterPill.VIDEOS.name
                    VaultCategory.DOCUMENT -> VaultFilterPill.DOCUMENTS.name
                }
            }

            allRecentItems = repository.getAllItems().sortedByDescending { it.importedTimestamp }
            items = when (runCatching { VaultFilterPill.valueOf(selectedPillName) }.getOrDefault(selectedPill)) {
                VaultFilterPill.PHOTOS -> repository.getItems(VaultCategory.PICTURE)
                VaultFilterPill.VIDEOS -> repository.getItems(VaultCategory.VIDEO)
                VaultFilterPill.DOCUMENTS -> repository.getItems(VaultCategory.DOCUMENT)
                VaultFilterPill.OTHERS -> repository.getAllItems()
            }
            isImporting = false
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
        onSystemPickerFinished()
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
        onSystemPickerFinished()
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
        onSystemPickerFinished()
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

    fun launchPickerForCurrentCategory() {
        onSystemPickerLaunched()
        when (selectedPill) {
            VaultFilterPill.PHOTOS -> {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            VaultFilterPill.VIDEOS -> {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
            VaultFilterPill.DOCUMENTS, VaultFilterPill.OTHERS -> {
                docPickerLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    // Panel 6: Importing Files Fullscreen View
    if (isImporting) {
        ImportingFilesScreen(
            progressPercent = importProgressPercent,
            currentStepIndex = importStepIndex,
            onCancel = {
                activeImportJob?.cancel()
                isImporting = false
            }
        )
        return
    }

    // Panel 7: Import Successful Fullscreen View
    showImportSuccessDialog?.let { importedItems ->
        ImportSuccessScreen(
            importedCount = importedItems.size,
            onViewFiles = { showImportSuccessDialog = null },
            onDone = { showImportSuccessDialog = null }
        )
        return
    }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hide Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("vault_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeoPalette.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("vault_menu_button")) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = NeoPalette.TextPrimary
                        )
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
                                text = { Text("Lock Vault & Return Home") },
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
                    containerColor = NeoPalette.BackgroundBase
                )
            )
        },
        bottomBar = {
            NeoBottomNavigationBar(
                currentTab = MainBottomTab.FILES,
                onSelectTab = { tab ->
                    when (tab) {
                        MainBottomTab.HOME -> (onOpenDashboard ?: onNavigateBack)()
                        MainBottomTab.APPS -> (onNavigateToApps ?: onNavigateBack)()
                        MainBottomTab.FILES -> {}
                        MainBottomTab.SETTINGS -> (onNavigateToSettings ?: onNavigateBack)()
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
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Category Pill Row matching Panel 5: Photos, Videos, Documents, Others
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pills = listOf(
                    Triple(VaultFilterPill.PHOTOS, "Photos", "vault_tab_picture"),
                    Triple(VaultFilterPill.VIDEOS, "Videos", "vault_tab_video"),
                    Triple(VaultFilterPill.DOCUMENTS, "Documents", "vault_tab_document"),
                    Triple(VaultFilterPill.OTHERS, "Others", "vault_tab_others")
                )
                pills.forEach { (pill, label, tag) ->
                    val isSelected = selectedPill == pill
                    NeoButton(
                        onClick = { selectedPillName = pill.name },
                        isPrimaryGlowing = isSelected,
                        shape = NeoShapes.FullPill,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(tag)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeoPalette.TextInverse else NeoPalette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Status Alert Banner
            AnimatedVisibility(visible = statusMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    val accentColor = if (isErrorStatus) NeoPalette.ErrorRed else NeoPalette.PrimaryGold
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
                                text = if (isErrorStatus) "Alert" else "Vault",
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = statusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoPalette.TextPrimary,
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
                                    tint = NeoPalette.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Column Grid matching Panel 5: "+ Import" square card + media/document tiles
            val gridPreviewItems = items.take(5)
            val totalSlots = 1 + gridPreviewItems.size
            val rowCount = (totalSlots + 2) / 3

            for (rowIndex in 0 until rowCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (colIndex in 0 until 3) {
                        val flatIndex = rowIndex * 3 + colIndex
                        if (flatIndex == 0) {
                            // First tile: "+ Import" card matching Panel 5
                            NeoCard(
                                onClick = { launchPickerForCurrentCategory() },
                                shape = NeoShapes.MediumCard,
                                accentBorder = NeoPalette.PrimaryGold.copy(alpha = 0.45f),
                                elevation = 6.dp,
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .testTag("vault_import_button")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Import",
                                        tint = NeoPalette.PrimaryGold,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Import",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeoPalette.TextPrimary
                                    )
                                }
                            }
                        } else if (flatIndex - 1 < gridPreviewItems.size) {
                            val vaultItem = gridPreviewItems[flatIndex - 1]
                            VaultGridTile(
                                item = vaultItem,
                                repository = repository,
                                onClick = { selectedItemForDetail = vaultItem },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Document Type Showcase Row when vault has fewer than 3 items (or in Documents/Others view)
            if (items.isEmpty() && !isLoading) {
                NeoCard(
                    shape = NeoShapes.MediumCard,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No files in this category yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap '+ Import' above to encrypt photos, videos, PDFs, or documents with hardware-backed AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoPalette.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // "Recent" Section Header matching Panel 5
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = NeoPalette.TextSecondary
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeoPalette.PrimaryGold)
                }
            } else if (allRecentItems.isEmpty()) {
                NeoCard(
                    shape = NeoShapes.MediumCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeoPalette.PrimaryGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Your recent encrypted vault files will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoPalette.TextSecondary
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allRecentItems.forEach { item ->
                        RecentVaultItemRow(
                            item = item,
                            repository = repository,
                            onClick = { selectedItemForDetail = item }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Delete Item Confirmation
        showDeleteConfirmDialog?.let { item ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                containerColor = NeoPalette.SurfaceDark,
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = NeoPalette.ErrorRed) },
                title = { Text("Delete From Vault?", fontWeight = FontWeight.Bold, color = NeoPalette.TextPrimary) },
                text = {
                    Text(
                        "This will permanently destroy the encrypted copy of '${item.originalFileName}'. This action cannot be undone.",
                        color = NeoPalette.TextSecondary
                    )
                },
                confirmButton = {
                    NeoButton(
                        text = "Delete Permanently",
                        isDestructive = true,
                        shape = NeoShapes.FullPill,
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
                        shape = NeoShapes.FullPill,
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
                    onSystemPickerLaunched()
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
                    onSystemPickerLaunched()
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
                    onSystemPickerLaunched()
                    restorePickerLauncher.launch(arrayOf("*/*"))
                }
            )
        }
    }
}

/**
 * Panel 6: "Importing Files" screen with circular gold progress ring and 4-step checklist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportingFilesScreen(
    progressPercent: Int,
    currentStepIndex: Int,
    onCancel: () -> Unit
) {
    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Importing Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))

                // Circular Gold Progress Ring matching Panel 6
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        drawArc(
                            color = Color(0xFF22222C),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    NeoPalette.SecondaryAmber,
                                    NeoPalette.PrimaryGold,
                                    NeoPalette.PrimaryGoldBright
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = (progressPercent.coerceIn(0, 100) / 100f) * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = NeoPalette.PrimaryGold,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${progressPercent.coerceIn(0, 100)}%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeoPalette.TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Encrypting your files...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Please keep the app open",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoPalette.TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Checklist Card matching Panel 6
                NeoCard(
                    shape = NeoShapes.LargeCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val steps = listOf(
                        "Reading files",
                        "Encrypting with AES-256",
                        "Saving to vault",
                        "Finalizing"
                    )
                    steps.forEachIndexed { index, label ->
                        val isCompleted = index <= currentStepIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isCompleted) NeoPalette.PrimaryGold else NeoPalette.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCompleted) NeoPalette.TextPrimary else NeoPalette.TextSecondary
                                )
                            }
                            if (index > 0) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (index == currentStepIndex) NeoPalette.PrimaryGold else NeoPalette.TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            NeoButton(
                text = "Cancel",
                onClick = onCancel,
                shape = NeoShapes.FullPill,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Panel 7: "Import Successful!" screen with glowing gold folder badge, "View Files", and "Done".
 */
@Composable
private fun ImportSuccessScreen(
    importedCount: Int,
    onViewFiles: () -> Unit,
    onDone: () -> Unit
) {
    Scaffold(
        containerColor = NeoPalette.BackgroundBase
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF281F07),
                            NeoPalette.BackgroundBase
                        )
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Circular Folder + Checkmark Badge matching Panel 7
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = NeoPalette.PrimaryGold,
                        spotColor = NeoPalette.PrimaryGoldBright
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF362A0A),
                                Color(0xFF151419)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                NeoPalette.PrimaryGoldBright,
                                NeoPalette.PrimaryGold.copy(alpha = 0.25f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Vault Folder",
                        tint = NeoPalette.PrimaryGold,
                        modifier = Modifier.size(84.dp)
                    )
                    Surface(
                        shape = CircleShape,
                        color = NeoPalette.PrimaryGoldBright,
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF151419)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeoPalette.TextInverse,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = NeoPalette.PrimaryGold, fontWeight = FontWeight.ExtraBold)) {
                        append("Import ")
                    }
                    withStyle(SpanStyle(color = NeoPalette.TextPrimary, fontWeight = FontWeight.ExtraBold)) {
                        append("Successful!")
                    }
                },
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your files ($importedCount) are now safely stored in the secure vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeoPalette.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            NeoButton(
                text = "View Files",
                onClick = onViewFiles,
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_success_view_files_button")
            )

            Spacer(modifier = Modifier.height(14.dp))

            NeoButton(
                text = "Done",
                onClick = onDone,
                shape = NeoShapes.FullPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_success_done_button")
            )
        }
    }
}

@Composable
private fun VaultGridTile(
    item: VaultItem,
    repository: VaultRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        if (item.category == VaultCategory.PICTURE) {
            bitmap = repository.loadDecryptedBitmap(item)
        }
    }

    val ext = item.originalFileName.substringAfterLast('.', "").uppercase(Locale.US).take(4).ifEmpty {
        when (item.category) {
            VaultCategory.PICTURE -> "IMG"
            VaultCategory.VIDEO -> "VID"
            VaultCategory.DOCUMENT -> "DOC"
        }
    }

    val badgeColor = when (ext) {
        "PDF" -> Color(0xFFDC2626)
        "TXT" -> Color(0xFF2563EB)
        "DOC", "DOCX" -> Color(0xFF4F46E5)
        else -> NeoPalette.SecondaryAmber
    }

    NeoCard(
        onClick = onClick,
        shape = NeoShapes.MediumCard,
        contentPadding = PaddingValues(6.dp),
        modifier = modifier
    ) {
        if (item.category == VaultCategory.PICTURE && bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.originalFileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ext,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ext,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeoPalette.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentVaultItemRow(
    item: VaultItem,
    repository: VaultRepository,
    onClick: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        if (item.category == VaultCategory.PICTURE) {
            bitmap = repository.loadDecryptedBitmap(item)
        }
    }

    val ext = item.originalFileName.substringAfterLast('.', "").uppercase(Locale.US).take(4).ifEmpty { "FILE" }
    val badgeColor = when (ext) {
        "PDF" -> Color(0xFFDC2626)
        "TXT" -> Color(0xFF2563EB)
        "DOC", "DOCX" -> Color(0xFF4F46E5)
        else -> NeoPalette.SecondaryAmber
    }

    NeoCard(
        onClick = onClick,
        shape = NeoShapes.MediumCard,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.category == VaultCategory.PICTURE && bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = item.originalFileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.category == VaultCategory.VIDEO) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalFileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.importedTimestamp))
                Text(
                    text = "${formatFileSize(item.fileSizeRaw)} • $dateFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoPalette.TextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Details",
                tint = NeoPalette.TextSecondary
            )
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
                    fontWeight = FontWeight.Bold,
                    color = NeoPalette.TextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = NeoPalette.TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                            CircularProgressIndicator(color = NeoPalette.PrimaryGold)
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
                                color = NeoPalette.TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

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
                    text = "Export",
                    icon = Icons.Default.FileDownload,
                    isPrimaryGlowing = true,
                    onClick = onExport,
                    shape = NeoShapes.FullPill,
                    modifier = Modifier.weight(1f)
                )

                NeoButton(
                    text = "Delete",
                    icon = Icons.Default.Delete,
                    isDestructive = true,
                    onClick = onDelete,
                    shape = NeoShapes.FullPill,
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
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = NeoPalette.TextSecondary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = NeoPalette.TextPrimary,
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
        containerColor = NeoPalette.SurfaceDark,
        title = { Text(title, fontWeight = FontWeight.Bold, color = NeoPalette.TextPrimary) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NeoPalette.TextSecondary)
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
                    Text(errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = NeoPalette.ErrorRed)
                }
            }
        },
        confirmButton = {
            NeoButton(
                text = confirmLabel,
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
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
                shape = NeoShapes.FullPill,
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
