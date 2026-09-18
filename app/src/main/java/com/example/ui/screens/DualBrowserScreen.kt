package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ssh.RemoteItem
import com.example.ssh.SshConnectionState
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.ConflictDialog
import com.example.ui.components.ExitWarningDialog
import com.example.ui.components.HostKeyDialog
import com.example.ui.components.LocalItemRow
import com.example.ui.components.RemoteItemRow
import com.example.ui.components.TransferBottomSheet
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualBrowserScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()

    val currentRemotePath by viewModel.currentRemotePath.collectAsStateWithLifecycle()
    val remoteItems by viewModel.remoteItems.collectAsStateWithLifecycle()
    val selectedRemoteItems by viewModel.selectedRemoteItems.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val isLoadingRemote by viewModel.isLoadingRemote.collectAsStateWithLifecycle()

    val grantedTreeUri by viewModel.grantedTreeUri.collectAsStateWithLifecycle()
    val currentLocalDirDoc by viewModel.currentLocalDirDoc.collectAsStateWithLifecycle()
    val localBreadcrumbs by viewModel.localBreadcrumbs.collectAsStateWithLifecycle()
    val localItems by viewModel.localItems.collectAsStateWithLifecycle()
    val selectedLocalItems by viewModel.selectedLocalItems.collectAsStateWithLifecycle()
    val isLoadingLocal by viewModel.isLoadingLocal.collectAsStateWithLifecycle()

    val hostKeyPrompt by viewModel.hostKeyPrompt.collectAsStateWithLifecycle()
    val showExitWarningDialog by viewModel.showExitWarningDialog.collectAsStateWithLifecycle()
    val conflictResolution by viewModel.conflictResolution.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Local SAF, 1: Remote Linux SFTP
    var showConflictDialog by remember { mutableStateOf(false) }
    var showCreateDirDialog by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<RemoteItem?>(null) }
    var newRenameName by remember { mutableStateOf("") }
    var topMenuExpanded by remember { mutableStateOf(false) }

    // Intercept Back Press during active transfer
    BackHandler {
        if (transferProgress.isRunning) {
            viewModel.setShowExitWarningDialog(true)
        } else if (selectedTab == 0 && localBreadcrumbs.size > 1) {
            viewModel.navigateToLocalParent()
        } else {
            viewModel.navigateTo(AppScreen.SERVERS)
        }
    }

    // SAF Launchers
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setGrantedFolderTree(uri)
        }
    }

    val singleFileUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadSingleExternalFile(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Connection status indicator dot
                            val statusDotColor = when (connectionState) {
                                is SshConnectionState.Connected -> Color(0xFF10B981)
                                is SshConnectionState.Connecting -> Color(0xFFF59E0B)
                                is SshConnectionState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeServer?.name ?: "Dual Browser",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val subtitleText = when (val state = connectionState) {
                            is SshConnectionState.Connected -> "${state.username}@${state.host}:${state.port}"
                            is SshConnectionState.Connecting -> state.message
                            is SshConnectionState.Error -> "Connection error"
                            else -> "Disconnected"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (transferProgress.isRunning) {
                                viewModel.setShowExitWarningDialog(true)
                            } else {
                                viewModel.navigateTo(AppScreen.SERVERS)
                            }
                        },
                        modifier = Modifier.testTag("back_from_browser_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Conflict Policy icon
                    IconButton(
                        onClick = { showConflictDialog = true },
                        modifier = Modifier.testTag("conflict_policy_button")
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = "Conflict Policy")
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) viewModel.refreshLocal() else viewModel.refreshRemote()
                        },
                        modifier = Modifier.testTag("refresh_browser_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    // Overflow menu
                    Box {
                        IconButton(onClick = { topMenuExpanded = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pick File to Upload (SAF)") },
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                                onClick = {
                                    topMenuExpanded = false
                                    singleFileUploadLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change Local Folder (SAF)") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    topMenuExpanded = false
                                    folderPickerLauncher.launch(null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Disconnect Server") },
                                leadingIcon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    topMenuExpanded = false
                                    viewModel.disconnect()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Transfer Action Bottom Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Upload single file button
                        OutlinedButton(
                            onClick = { singleFileUploadLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(0.35f)
                                .testTag("quick_upload_single_button"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload File", fontSize = 12.sp, maxLines = 1)
                        }

                        // Upload Selected (Local -> Remote)
                        Button(
                            onClick = { viewModel.uploadSelectedLocalItems() },
                            enabled = selectedLocalItems.isNotEmpty() && !transferProgress.isRunning && connectionState is SshConnectionState.Connected,
                            modifier = Modifier
                                .weight(0.35f)
                                .testTag("upload_selected_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload (${selectedLocalItems.size})", fontSize = 12.sp, maxLines = 1)
                        }

                        // Download Selected (Remote -> Local)
                        Button(
                            onClick = { viewModel.downloadSelectedRemoteItems() },
                            enabled = selectedRemoteItems.isNotEmpty() && !transferProgress.isRunning && currentLocalDirDoc != null,
                            modifier = Modifier
                                .weight(0.35f)
                                .testTag("download_selected_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download (${selectedRemoteItems.size})", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    // In-app real-time transfer bottom sheet overlay
                    if (transferProgress.isRunning || transferProgress.queueItems.isNotEmpty()) {
                        TransferBottomSheet(
                            progress = transferProgress,
                            onCancelTransfer = { viewModel.cancelCurrentTransfer() },
                            onDismiss = { viewModel.transferEngine.dismissTransferSheet() },
                            onToggleExpand = {
                                if (transferProgress.isExpanded) viewModel.transferEngine.dismissTransferSheet()
                                else viewModel.transferEngine.expandTransferSheet()
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Dual Navigation Tabs: Local SAF vs Remote Linux
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Local (SAF)")
                            if (selectedLocalItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge { Text("${selectedLocalItems.size}") }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_local_saf")
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Remote (Linux)")
                            if (selectedRemoteItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge { Text("${selectedRemoteItems.size}") }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_remote_sftp")
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    // Local SAF Browser
                    LocalBrowserPane(
                        currentDoc = currentLocalDirDoc,
                        localItems = localItems,
                        breadcrumbs = localBreadcrumbs,
                        selectedItems = selectedLocalItems,
                        isLoading = isLoadingLocal,
                        onGrantFolder = { folderPickerLauncher.launch(null) },
                        onNavigateToSubdir = { viewModel.navigateToLocalSubdir(it) },
                        onNavigateUp = { viewModel.navigateToLocalParent() },
                        onToggleSelect = { viewModel.toggleSelectLocalItem(it) },
                        onSelectAll = { viewModel.selectAllLocalItems() },
                        onClearSelection = { viewModel.clearLocalSelection() }
                    )
                } else {
                    // Remote Linux SFTP Browser
                    RemoteBrowserPane(
                        connectionState = connectionState,
                        currentPath = currentRemotePath,
                        remoteItems = remoteItems,
                        selectedItems = selectedRemoteItems,
                        showHiddenFiles = showHiddenFiles,
                        isLoading = isLoadingRemote,
                        onNavigateToSubdir = { viewModel.navigateToRemoteSubdir(it) },
                        onNavigateUp = { viewModel.navigateToRemoteParent() },
                        onPathClick = { viewModel.setRemotePathDirectly(it) },
                        onToggleSelect = { viewModel.toggleSelectRemoteItem(it) },
                        onSelectAll = { viewModel.selectAllRemoteItems() },
                        onClearSelection = { viewModel.clearRemoteSelection() },
                        onToggleHidden = { viewModel.toggleHiddenFiles() },
                        onCreateFolder = { showCreateDirDialog = true },
                        onDeleteSelected = { viewModel.deleteSelectedRemoteItems() },
                        onRename = { item ->
                            itemToRename = item
                            newRenameName = item.name
                        },
                        onRetryConnect = {
                            activeServer?.let { viewModel.connectToServer(it) }
                        }
                    )
                }
            }
        }
    }

    // Host Key Verification Dialog
    hostKeyPrompt?.let { prompt ->
        HostKeyDialog(
            request = prompt,
            onTrust = { viewModel.acceptHostKey(it) },
            onReject = { viewModel.rejectHostKey(it) }
        )
    }

    // Exit Warning Dialog during transfer
    if (showExitWarningDialog) {
        ExitWarningDialog(
            onKeepAppOpen = { viewModel.setShowExitWarningDialog(false) },
            onCancelAndLeave = { viewModel.confirmExitAndCancelTransfer() }
        )
    }

    // Conflict Policy Settings Dialog
    if (showConflictDialog) {
        ConflictDialog(
            currentResolution = conflictResolution,
            onResolutionSelected = { viewModel.setConflictResolution(it) },
            onDismiss = { showConflictDialog = false }
        )
    }

    // Create Remote Directory Dialog
    if (showCreateDirDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("Create Linux Directory") },
            text = {
                OutlinedTextField(
                    value = newDirName,
                    onValueChange = { newDirName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_dir_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createRemoteDirectory(newDirName)
                        newDirName = ""
                        showCreateDirDialog = false
                    },
                    enabled = newDirName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_dir_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Remote Item Dialog
    itemToRename?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename ${if (item.isDirectory) "Directory" else "File"}") },
            text = {
                OutlinedTextField(
                    value = newRenameName,
                    onValueChange = { newRenameName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_item_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameRemoteItem(item, newRenameName)
                        itemToRename = null
                    },
                    enabled = newRenameName.isNotBlank() && newRenameName != item.name,
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LocalBrowserPane(
    currentDoc: androidx.documentfile.provider.DocumentFile?,
    localItems: List<com.example.saf.LocalItem>,
    breadcrumbs: List<androidx.documentfile.provider.DocumentFile>,
    selectedItems: Set<com.example.saf.LocalItem>,
    isLoading: Boolean,
    onGrantFolder: () -> Unit,
    onNavigateToSubdir: (com.example.saf.LocalItem) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleSelect: (com.example.saf.LocalItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    if (currentDoc == null) {
        // Prominent SAF Grant screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Local Storage (SAF)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SSHDrop uses standard Storage Access Framework (SAF) exclusively. No broad device media or storage permissions requested.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGrantFolder,
                modifier = Modifier.testTag("grant_folder_access_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Folder Access")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Local Breadcrumbs / Path bar
            val pathString = breadcrumbs.joinToString("/") { it.name ?: "Root" }
            BreadcrumbBar(
                path = pathString,
                onNavigateUp = onNavigateUp,
                canNavigateUp = breadcrumbs.size > 1,
                onPathSegmentClick = { /* Can drill back */ },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Local Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${localItems.size} items (${selectedItems.size} selected)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (selectedItems.isNotEmpty()) {
                        TextButton(onClick = onClearSelection) {
                            Text("Deselect All", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onSelectAll) {
                        Text("Select All", fontSize = 12.sp)
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (localItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Folder is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(localItems, key = { it.uri.toString() }) { item ->
                        LocalItemRow(
                            item = item,
                            isSelected = selectedItems.contains(item),
                            onToggleSelect = { onToggleSelect(item) },
                            onClick = {
                                if (item.isDirectory) onNavigateToSubdir(item)
                                else onToggleSelect(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteBrowserPane(
    connectionState: SshConnectionState,
    currentPath: String,
    remoteItems: List<RemoteItem>,
    selectedItems: Set<RemoteItem>,
    showHiddenFiles: Boolean,
    isLoading: Boolean,
    onNavigateToSubdir: (RemoteItem) -> Unit,
    onNavigateUp: () -> Unit,
    onPathClick: (String) -> Unit,
    onToggleSelect: (RemoteItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleHidden: () -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRename: (RemoteItem) -> Unit,
    onRetryConnect: () -> Unit
) {
    when (connectionState) {
        is SshConnectionState.Connecting -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is SshConnectionState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connection Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onRetryConnect) {
                    Text("Reconnect")
                }
            }
        }
        is SshConnectionState.Connected -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Remote Path Breadcrumbs
                BreadcrumbBar(
                    path = currentPath,
                    onNavigateUp = onNavigateUp,
                    canNavigateUp = currentPath != "/" && currentPath.isNotEmpty(),
                    onPathSegmentClick = onPathClick,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Remote Action Bar: Create dir, delete, hidden files toggle, selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCreateFolder, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (selectedItems.isNotEmpty()) {
                            IconButton(onClick = onDeleteSelected, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (selectedItems.size == 1) {
                                IconButton(onClick = { onRename(selectedItems.first()) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onToggleHidden, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Hidden Files",
                                tint = if (showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row {
                        if (selectedItems.isNotEmpty()) {
                            TextButton(onClick = onClearSelection) {
                                Text("Deselect", fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = onSelectAll) {
                            Text("All", fontSize = 12.sp)
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (remoteItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Directory is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        items(remoteItems, key = { it.path }) { item ->
                            RemoteItemRow(
                                item = item,
                                isSelected = selectedItems.contains(item),
                                onToggleSelect = { onToggleSelect(item) },
                                onClick = {
                                    if (item.isDirectory) onNavigateToSubdir(item)
                                    else onToggleSelect(item)
                                }
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Not connected to Linux server")
            }
        }
    }
}
