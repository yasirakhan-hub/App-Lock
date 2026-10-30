package com.example.ui.vault

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VaultFilterPill {
    PHOTOS,
    VIDEOS,
    DOCUMENTS,
    OTHERS
}

data class SafPickerRequest(
    val primaryMimeType: String,
    val extraMimeTypes: List<String> = emptyList()
)

/**
 * Singleton Android Storage Access Framework (SAF) contract for selecting one or multiple files.
 * Being a singleton `object` ensures `rememberLauncherForActivityResult`'s internal `DisposableEffect`
 * never sees a new contract instance on recomposition (which would otherwise unregister the launcher
 * and drop pending activity results).
 */
object SafOpenMultipleDocumentsContract : ActivityResultContract<SafPickerRequest, List<Uri>>() {
    override fun createIntent(context: Context, input: SafPickerRequest): Intent {
        val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.primaryMimeType
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            if (input.extraMimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, input.extraMimeTypes.toTypedArray())
            }
        }
        if (context.packageManager.resolveActivity(openDocIntent, 0) != null) {
            return openDocIntent
        }

        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.primaryMimeType
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (input.extraMimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, input.extraMimeTypes.toTypedArray())
            }
        }
        if (context.packageManager.resolveActivity(getContentIntent, 0) != null) {
            return getContentIntent
        }

        return openDocIntent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return emptyList()
        }
        val uris = LinkedHashSet<Uri>()
        intent.data?.let { uris.add(it) }
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        }
        return uris.toList()
    }
}

object SafCreateDocumentContract : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, input)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

object SafOpenSingleDocumentContract : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        if (context.packageManager.resolveActivity(openDocIntent, 0) != null) {
            return openDocIntent
        }

        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context.packageManager.resolveActivity(getContentIntent, 0) != null) {
            return getContentIntent
        }

        return openDocIntent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

/**
 * ViewModel retaining the VaultRepository instance, active import job, and Vault UI state across
 * configuration changes and system file picker Activity result transitions.
 */
class VaultViewModel(application: Application) : AndroidViewModel(application) {
    val repository: VaultRepository by lazy { VaultRepository(application.applicationContext) }

    var selectedPillName by mutableStateOf(VaultFilterPill.PHOTOS.name)
    var items by mutableStateOf<List<VaultItem>>(emptyList())
    var allRecentItems by mutableStateOf<List<VaultItem>>(emptyList())
    var isLoading by mutableStateOf(true)

    var isImporting by mutableStateOf(false)
    var importProgressPercent by mutableIntStateOf(0)
    var importStepIndex by mutableIntStateOf(0)
    var activeImportJob: Job? = null

    var showImportSuccessDialog by mutableStateOf<List<VaultItem>?>(null)
    var statusMessage by mutableStateOf<String?>(null)
    var isErrorStatus by mutableStateOf(false)
    var lastPickerReturnTimestampMs: Long = 0L

    fun refreshItems() {
        val pill = runCatching { VaultFilterPill.valueOf(selectedPillName) }.getOrDefault(VaultFilterPill.PHOTOS)
        viewModelScope.launch {
            isLoading = true
            val (recent, filtered) = withContext(Dispatchers.IO) {
                val all = repository.getAllItems().sortedByDescending { it.importedTimestamp }
                val catItems = when (pill) {
                    VaultFilterPill.PHOTOS -> repository.getItems(VaultCategory.PICTURE)
                    VaultFilterPill.VIDEOS -> repository.getItems(VaultCategory.VIDEO)
                    VaultFilterPill.DOCUMENTS -> repository.getItems(VaultCategory.DOCUMENT)
                    VaultFilterPill.OTHERS -> all
                }
                all to catItems
            }
            allRecentItems = recent
            items = filtered
            isLoading = false
        }
    }

    fun cancelActiveImport(onImportInFlightChanged: (Boolean) -> Unit = {}) {
        activeImportJob?.cancel()
        activeImportJob = null
        isImporting = false
        onImportInFlightChanged(false)
    }

    fun clearSessionOnExit(onImportInFlightChanged: (Boolean) -> Unit = {}) {
        cancelActiveImport(onImportInFlightChanged)
        showImportSuccessDialog = null
        items = emptyList()
        allRecentItems = emptyList()
    }

    fun startBatchImport(
        uris: List<Uri>,
        mediaLabel: String,
        fallbackCategory: VaultCategory?,
        selectedCategory: VaultCategory,
        onImportInFlightChanged: (Boolean) -> Unit
    ) {
        if (uris.isEmpty()) return

        activeImportJob?.cancel()
        activeImportJob = viewModelScope.launch {
            onImportInFlightChanged(true)
            isImporting = true
            importStepIndex = 0
            importProgressPercent = 18
            try {
                delay(80L)

                val importedList = mutableListOf<VaultItem>()
                var duplicateCount = 0
                var failureCount = 0

                importStepIndex = 1
                importProgressPercent = 45

                uris.forEachIndexed { idx, uri ->
                    val result = repository.importFromUri(
                        uri = uri,
                        fallbackCategory = fallbackCategory
                    )
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
                delay(80L)

                if (importedList.isNotEmpty() && importedList.none { it.category == selectedCategory }) {
                    selectedPillName = when (importedList.first().category) {
                        VaultCategory.PICTURE -> VaultFilterPill.PHOTOS.name
                        VaultCategory.VIDEO -> VaultFilterPill.VIDEOS.name
                        VaultCategory.DOCUMENT -> VaultFilterPill.DOCUMENTS.name
                    }
                }

                val activePill = runCatching { VaultFilterPill.valueOf(selectedPillName) }
                    .getOrDefault(VaultFilterPill.PHOTOS)

                val (recent, filtered) = withContext(Dispatchers.IO) {
                    val all = repository.getAllItems().sortedByDescending { it.importedTimestamp }
                    val catItems = when (activePill) {
                        VaultFilterPill.PHOTOS -> repository.getItems(VaultCategory.PICTURE)
                        VaultFilterPill.VIDEOS -> repository.getItems(VaultCategory.VIDEO)
                        VaultFilterPill.DOCUMENTS -> repository.getItems(VaultCategory.DOCUMENT)
                        VaultFilterPill.OTHERS -> all
                    }
                    all to catItems
                }
                allRecentItems = recent
                items = filtered
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
            } finally {
                isImporting = false
                onImportInFlightChanged(false)
            }
        }
    }
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
    onImportInFlightChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModelStoreOwner = context as? ViewModelStoreOwner
    val vaultViewModel = remember(viewModelStoreOwner) {
        if (viewModelStoreOwner != null) {
            ViewModelProvider(
                viewModelStoreOwner,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[VaultViewModel::class.java]
        } else {
            VaultViewModel(app)
        }
    }
    val repository = vaultViewModel.repository
    val scope = rememberCoroutineScope()

    var selectedPillName by vaultViewModel::selectedPillName
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

    var items by vaultViewModel::items
    var allRecentItems by vaultViewModel::allRecentItems
    var isLoading by vaultViewModel::isLoading

    // Importing progress state (Panel 6)
    var isImporting by vaultViewModel::isImporting
    val importProgressPercent by vaultViewModel::importProgressPercent
    val importStepIndex by vaultViewModel::importStepIndex

    var selectedItemForDetail by remember { mutableStateOf<VaultItem?>(null) }
    var itemToExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<VaultItem?>(null) }
    var showImportSuccessDialog by vaultViewModel::showImportSuccessDialog
    var showMenu by remember { mutableStateOf(false) }

    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var statusMessage by vaultViewModel::statusMessage
    var isErrorStatus by vaultViewModel::isErrorStatus

    fun refreshItems() {
        vaultViewModel.refreshItems()
    }

    LaunchedEffect(selectedPill) {
        if (!isImporting) {
            refreshItems()
        }
    }

    // Clear transient detail/confirm dialogs when composition disposes,
    // without cancelling an in-flight ViewModel import job across configuration changes.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            selectedItemForDetail = null
            showDeleteConfirmDialog = null
        }
    }

    BackHandler {
        val elapsedSincePickerReturn = System.currentTimeMillis() - vaultViewModel.lastPickerReturnTimestampMs
        if (elapsedSincePickerReturn in 0L..350L) {
            // Ignore stray back events immediately following system picker activity dismissal
            return@BackHandler
        }
        if (showDeleteConfirmDialog != null) {
            showDeleteConfirmDialog = null
        } else if (selectedItemForDetail != null) {
            selectedItemForDetail = null
        } else if (showImportSuccessDialog != null) {
            showImportSuccessDialog = null
        } else if (isImporting) {
            vaultViewModel.cancelActiveImport(onImportInFlightChanged)
        } else {
            vaultViewModel.clearSessionOnExit(onImportInFlightChanged)
            onNavigateBack()
        }
    }

    fun handleBatchImport(
        uris: List<Uri>,
        mediaLabel: String,
        fallbackCategory: VaultCategory? = selectedCategory
    ) {
        vaultViewModel.lastPickerReturnTimestampMs = System.currentTimeMillis()
        onSystemPickerFinished()
        if (uris.isEmpty()) {
            // User cancelled system picker; remain on VaultScreen without error
            return
        }
        vaultViewModel.startBatchImport(
            uris = uris,
            mediaLabel = mediaLabel,
            fallbackCategory = fallbackCategory,
            selectedCategory = selectedCategory,
            onImportInFlightChanged = onImportInFlightChanged
        )
    }

    // Android Storage Access Framework (SAF) Multi-File Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = SafOpenMultipleDocumentsContract
    ) { uris ->
        val label = when (selectedPill) {
            VaultFilterPill.PHOTOS -> "photo(s)"
            VaultFilterPill.VIDEOS -> "video(s)"
            VaultFilterPill.DOCUMENTS, VaultFilterPill.OTHERS -> "document(s)"
        }
        handleBatchImport(uris, label, selectedCategory)
    }

    // SAF Export Single Item
    val exportLauncher = rememberLauncherForActivityResult(
        contract = SafCreateDocumentContract
    ) { destUri ->
        vaultViewModel.lastPickerReturnTimestampMs = System.currentTimeMillis()
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
        contract = SafCreateDocumentContract
    ) { destUri ->
        vaultViewModel.lastPickerReturnTimestampMs = System.currentTimeMillis()
        onSystemPickerFinished()
        val passphrase = pendingBackupPassphrase
        if (destUri != null && passphrase != null) {
            scope.launch {
                isLoading = true
                val outStream = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(destUri) }.getOrNull()
                }
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
        contract = SafOpenSingleDocumentContract
    ) { srcUri ->
        vaultViewModel.lastPickerReturnTimestampMs = System.currentTimeMillis()
        onSystemPickerFinished()
        val passphrase = pendingRestorePassphrase
        if (srcUri != null && passphrase != null) {
            scope.launch {
                isLoading = true
                val inStream = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(srcUri) }.getOrNull()
                }
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
        val mimeType = when (selectedPill) {
            VaultFilterPill.PHOTOS -> "image/*"
            VaultFilterPill.VIDEOS -> "video/*"
            VaultFilterPill.DOCUMENTS, VaultFilterPill.OTHERS -> "*/*"
        }
        onSystemPickerLaunched()
        docPickerLauncher.launch(SafPickerRequest(primaryMimeType = mimeType))
    }

    // Panel 6: Importing Files Fullscreen View
    if (isImporting) {
        ImportingFilesScreen(
            progressPercent = importProgressPercent,
            currentStepIndex = importStepIndex,
            onCancel = {
                vaultViewModel.cancelActiveImport(onImportInFlightChanged)
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
                    IconButton(
                        onClick = {
                            vaultViewModel.clearSessionOnExit(onImportInFlightChanged)
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("vault_back_button")
                    ) {
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
                                    vaultViewModel.clearSessionOnExit(onImportInFlightChanged)
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
                        MainBottomTab.HOME -> {
                            vaultViewModel.clearSessionOnExit(onImportInFlightChanged)
                            (onOpenDashboard ?: onNavigateBack)()
                        }
                        MainBottomTab.APPS -> {
                            vaultViewModel.clearSessionOnExit(onImportInFlightChanged)
                            (onNavigateToApps ?: onNavigateBack)()
                        }
                        MainBottomTab.FILES -> {}
                        MainBottomTab.SETTINGS -> {
                            vaultViewModel.clearSessionOnExit(onImportInFlightChanged)
                            (onNavigateToSettings ?: onNavigateBack)()
                        }
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
                            val targetItem = item
                            showDeleteConfirmDialog = null
                            selectedItemForDetail = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repository.deleteItem(targetItem)
                                }
                                refreshItems()
                                isErrorStatus = false
                                statusMessage = "Deleted '${targetItem.originalFileName}'"
                            }
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
                    restorePickerLauncher.launch("*/*")
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
            bitmap = repository.loadDecryptedBitmap(item, maxDimension = 384)
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
            bitmap = repository.loadDecryptedBitmap(item, maxDimension = 256)
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
            bitmap = repository.loadDecryptedBitmap(item, maxDimension = 1024)
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
