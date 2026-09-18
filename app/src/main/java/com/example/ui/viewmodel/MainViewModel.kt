package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ServerEntity
import com.example.data.local.entity.TransferHistoryEntity
import com.example.data.repository.KnownHostRepository
import com.example.data.repository.ServerRepository
import com.example.data.repository.TransferHistoryRepository
import com.example.saf.LocalItem
import com.example.saf.SafManager
import com.example.ssh.HostKeyVerificationRequest
import com.example.ssh.RemoteItem
import com.example.ssh.SshConnectionState
import com.example.ssh.SshManager
import com.example.transfer.ActiveTransferProgress
import com.example.transfer.ConflictResolution
import com.example.transfer.TransferEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    SERVERS,
    ADD_EDIT_SERVER,
    DUAL_BROWSER,
    HISTORY
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val serverRepository = ServerRepository(db.serverDao())
    val knownHostRepository = KnownHostRepository(db.knownHostDao())
    val historyRepository = TransferHistoryRepository(db.transferHistoryDao())

    val safManager = SafManager(application)

    private val prefs = application.getSharedPreferences("sshdrop_prefs", Context.MODE_PRIVATE)

    // Current Screen
    private val _currentScreen = MutableStateFlow(AppScreen.SERVERS)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Host key verification prompt
    private val _hostKeyPrompt = MutableStateFlow<HostKeyVerificationRequest?>(null)
    val hostKeyPrompt: StateFlow<HostKeyVerificationRequest?> = _hostKeyPrompt.asStateFlow()

    // SSH Manager
    val sshManager = SshManager(
        context = application,
        knownHostRepository = knownHostRepository,
        onHostKeyPrompt = { request ->
            _hostKeyPrompt.value = request
        }
    )

    // Transfer Engine
    val transferEngine = TransferEngine(safManager, sshManager, historyRepository)
    val transferProgress: StateFlow<ActiveTransferProgress> = transferEngine.progress

    // Transfers Job
    private var transferJob: Job? = null

    // UI Feedback messages
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Active Server & Connection
    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _activeServer = MutableStateFlow<ServerEntity?>(null)
    val activeServer: StateFlow<ServerEntity?> = _activeServer.asStateFlow()

    // Servers list
    val serversList: StateFlow<List<ServerEntity>> = serverRepository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History list
    val historyList: StateFlow<List<TransferHistoryEntity>> = historyRepository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Editing Server
    private val _editingServer = MutableStateFlow<ServerEntity?>(null)
    val editingServer: StateFlow<ServerEntity?> = _editingServer.asStateFlow()

    // Remote SFTP Browser State
    private val _currentRemotePath = MutableStateFlow("/home")
    val currentRemotePath: StateFlow<String> = _currentRemotePath.asStateFlow()

    private val _remoteItems = MutableStateFlow<List<RemoteItem>>(emptyList())
    val remoteItems: StateFlow<List<RemoteItem>> = _remoteItems.asStateFlow()

    private val _selectedRemoteItems = MutableStateFlow<Set<RemoteItem>>(emptySet())
    val selectedRemoteItems: StateFlow<Set<RemoteItem>> = _selectedRemoteItems.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _isLoadingRemote = MutableStateFlow(false)
    val isLoadingRemote: StateFlow<Boolean> = _isLoadingRemote.asStateFlow()

    // Local SAF Browser State
    private val _grantedTreeUri = MutableStateFlow<Uri?>(null)
    val grantedTreeUri: StateFlow<Uri?> = _grantedTreeUri.asStateFlow()

    private val _currentLocalDirDoc = MutableStateFlow<DocumentFile?>(null)
    val currentLocalDirDoc: StateFlow<DocumentFile?> = _currentLocalDirDoc.asStateFlow()

    private val _localBreadcrumbs = MutableStateFlow<List<DocumentFile>>(emptyList())
    val localBreadcrumbs: StateFlow<List<DocumentFile>> = _localBreadcrumbs.asStateFlow()

    private val _localItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val localItems: StateFlow<List<LocalItem>> = _localItems.asStateFlow()

    private val _selectedLocalItems = MutableStateFlow<Set<LocalItem>>(emptySet())
    val selectedLocalItems: StateFlow<Set<LocalItem>> = _selectedLocalItems.asStateFlow()

    private val _isLoadingLocal = MutableStateFlow(false)
    val isLoadingLocal: StateFlow<Boolean> = _isLoadingLocal.asStateFlow()

    // Exit Warning Dialog (when navigating away during active transfer)
    private val _showExitWarningDialog = MutableStateFlow(false)
    val showExitWarningDialog: StateFlow<Boolean> = _showExitWarningDialog.asStateFlow()

    // Conflict Resolution Selection
    private val _conflictResolution = MutableStateFlow(ConflictResolution.OVERWRITE)
    val conflictResolution: StateFlow<ConflictResolution> = _conflictResolution.asStateFlow()

    init {
        // Restore persistable URI if available
        val savedUriStr = prefs.getString("saved_tree_uri", null)
        if (!savedUriStr.isNullOrEmpty()) {
            val uri = Uri.parse(savedUriStr)
            _grantedTreeUri.value = uri
            viewModelScope.launch {
                val doc = safManager.getDocumentFromUri(uri, isTree = true)
                if (doc != null && doc.canRead()) {
                    _currentLocalDirDoc.value = doc
                    _localBreadcrumbs.value = listOf(doc)
                    loadLocalItems(doc)
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (transferProgress.value.isRunning && screen != AppScreen.DUAL_BROWSER) {
            _showExitWarningDialog.value = true
            return
        }
        _currentScreen.value = screen
    }

    fun setShowExitWarningDialog(show: Boolean) {
        _showExitWarningDialog.value = show
    }

    fun confirmExitAndCancelTransfer() {
        _showExitWarningDialog.value = false
        cancelCurrentTransfer()
        _currentScreen.value = AppScreen.SERVERS
    }

    fun setConflictResolution(resolution: ConflictResolution) {
        _conflictResolution.value = resolution
    }

    // Server Management
    fun startAddServer() {
        _editingServer.value = null
        navigateTo(AppScreen.ADD_EDIT_SERVER)
    }

    fun startEditServer(server: ServerEntity) {
        _editingServer.value = server
        navigateTo(AppScreen.ADD_EDIT_SERVER)
    }

    fun saveServer(
        name: String,
        host: String,
        port: Int,
        username: String,
        authType: String,
        password: String?,
        keyUri: String?,
        keyFileName: String?,
        keyPassphrase: String?,
        initialRemoteDir: String
    ) {
        viewModelScope.launch {
            val current = _editingServer.value
            val entity = if (current != null) {
                current.copy(
                    name = name.ifBlank { host },
                    host = host.trim(),
                    port = port,
                    username = username.trim(),
                    authType = authType,
                    password = password,
                    keyUri = keyUri,
                    keyFileName = keyFileName,
                    keyPassphrase = keyPassphrase,
                    initialRemoteDir = initialRemoteDir.ifBlank { "/home" }
                )
            } else {
                ServerEntity(
                    name = name.ifBlank { host },
                    host = host.trim(),
                    port = port,
                    username = username.trim(),
                    authType = authType,
                    password = password,
                    keyUri = keyUri,
                    keyFileName = keyFileName,
                    keyPassphrase = keyPassphrase,
                    initialRemoteDir = initialRemoteDir.ifBlank { "/home" }
                )
            }

            if (current != null) {
                serverRepository.updateServer(entity)
                _userMessage.emit("Server updated")
            } else {
                serverRepository.insertServer(entity)
                _userMessage.emit("Server added")
            }
            _currentScreen.value = AppScreen.SERVERS
        }
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            serverRepository.deleteServer(server)
            _userMessage.emit("Server '${server.name}' deleted")
        }
    }

    // Connect to Server
    fun connectToServer(server: ServerEntity) {
        _activeServer.value = server
        _currentRemotePath.value = server.initialRemoteDir
        _selectedRemoteItems.value = emptySet()
        _selectedLocalItems.value = emptySet()
        _connectionState.value = SshConnectionState.Connecting("Connecting to ${server.host}...")
        navigateTo(AppScreen.DUAL_BROWSER)

        viewModelScope.launch {
            var keyContent: String? = null
            if (server.authType.equals("KEY", ignoreCase = true) && !server.keyUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(server.keyUri)
                    keyContent = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    _connectionState.value = SshConnectionState.Error("Failed to read private key file: ${e.message}")
                    return@launch
                }
            }

            val result = sshManager.connectAndAuthenticate(
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                keyContent = keyContent,
                passphrase = server.keyPassphrase
            )

            result.onSuccess {
                serverRepository.updateLastConnected(server.id, System.currentTimeMillis())
                _connectionState.value = SshConnectionState.Connected(
                    serverName = server.name,
                    host = server.host,
                    port = server.port,
                    username = server.username,
                    currentRemotePath = _currentRemotePath.value
                )
                refreshRemote()
            }.onFailure { error ->
                _connectionState.value = SshConnectionState.Error(error.message ?: "Connection failed")
                _userMessage.emit("Connection failed: ${error.localizedMessage}")
            }
        }
    }

    fun disconnect() {
        sshManager.disconnect()
        _connectionState.value = SshConnectionState.Disconnected
        _remoteItems.value = emptyList()
        _selectedRemoteItems.value = emptySet()
        _activeServer.value = null
        navigateTo(AppScreen.SERVERS)
    }

    // Host Key Verification Handling
    fun acceptHostKey(request: HostKeyVerificationRequest) {
        request.deferredResult.complete(true)
        _hostKeyPrompt.value = null
    }

    fun rejectHostKey(request: HostKeyVerificationRequest) {
        request.deferredResult.complete(false)
        _hostKeyPrompt.value = null
        _connectionState.value = SshConnectionState.Error("Host key verification rejected")
    }

    // Remote Directory Browsing
    fun refreshRemote() {
        if (!sshManager.isConnected) return
        viewModelScope.launch {
            _isLoadingRemote.value = true
            try {
                val list = sshManager.listRemoteDirectory(_currentRemotePath.value, _showHiddenFiles.value)
                _remoteItems.value = list
                _selectedRemoteItems.value = emptySet()
            } catch (e: Exception) {
                _userMessage.emit("Remote error: ${e.message}")
            } finally {
                _isLoadingRemote.value = false
            }
        }
    }

    fun toggleHiddenFiles() {
        _showHiddenFiles.update { !it }
        refreshRemote()
    }

    fun navigateToRemoteSubdir(item: RemoteItem) {
        if (!item.isDirectory) return
        _currentRemotePath.value = item.path
        refreshRemote()
    }

    fun navigateToRemoteParent() {
        val current = _currentRemotePath.value.trimEnd('/')
        val lastSlash = current.lastIndexOf('/')
        val parent = if (lastSlash > 0) current.substring(0, lastSlash) else "/"
        _currentRemotePath.value = parent
        refreshRemote()
    }

    fun setRemotePathDirectly(path: String) {
        val clean = if (path.startsWith('/')) path else "/$path"
        _currentRemotePath.value = clean
        refreshRemote()
    }

    fun toggleSelectRemoteItem(item: RemoteItem) {
        _selectedRemoteItems.update { current ->
            if (current.contains(item)) current - item else current + item
        }
    }

    fun selectAllRemoteItems() {
        _selectedRemoteItems.value = _remoteItems.value.toSet()
    }

    fun clearRemoteSelection() {
        _selectedRemoteItems.value = emptySet()
    }

    fun createRemoteDirectory(dirName: String) {
        if (dirName.isBlank()) return
        val targetPath = "${_currentRemotePath.value.trimEnd('/')}/$dirName"
        viewModelScope.launch {
            val result = sshManager.createDirectory(targetPath)
            result.onSuccess {
                _userMessage.emit("Folder '$dirName' created")
                refreshRemote()
            }.onFailure {
                _userMessage.emit("Failed to create folder: ${it.message}")
            }
        }
    }

    fun deleteSelectedRemoteItems() {
        val selected = _selectedRemoteItems.value.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            for (item in selected) {
                val res = sshManager.deleteRemoteItem(item.path, item.isDirectory)
                if (res.isSuccess) successCount++
            }
            _userMessage.emit("Deleted $successCount item(s)")
            clearRemoteSelection()
            refreshRemote()
        }
    }

    fun renameRemoteItem(item: RemoteItem, newName: String) {
        if (newName.isBlank() || newName == item.name) return
        val parentDir = item.path.substringBeforeLast('/', "")
        val newPath = if (parentDir.isNotEmpty()) "$parentDir/$newName" else "/$newName"

        viewModelScope.launch {
            val res = sshManager.renameRemoteItem(item.path, newPath)
            res.onSuccess {
                _userMessage.emit("Renamed to '$newName'")
                refreshRemote()
            }.onFailure {
                _userMessage.emit("Rename failed: ${it.message}")
            }
        }
    }

    // Local SAF Management
    fun setGrantedFolderTree(treeUri: Uri) {
        safManager.takePersistablePermission(treeUri)
        _grantedTreeUri.value = treeUri
        prefs.edit().putString("saved_tree_uri", treeUri.toString()).apply()

        viewModelScope.launch {
            val doc = safManager.getDocumentFromUri(treeUri, isTree = true)
            if (doc != null) {
                _currentLocalDirDoc.value = doc
                _localBreadcrumbs.value = listOf(doc)
                loadLocalItems(doc)
            }
        }
    }

    private suspend fun loadLocalItems(dirDoc: DocumentFile) {
        _isLoadingLocal.value = true
        try {
            val items = safManager.listDirectoryDocument(dirDoc)
            _localItems.value = items
            _selectedLocalItems.value = emptySet()
        } catch (e: Exception) {
            _userMessage.emit("Failed to load local folder: ${e.message}")
        } finally {
            _isLoadingLocal.value = false
        }
    }

    fun refreshLocal() {
        val doc = _currentLocalDirDoc.value ?: return
        viewModelScope.launch {
            loadLocalItems(doc)
        }
    }

    fun navigateToLocalSubdir(item: LocalItem) {
        if (!item.isDirectory) return
        viewModelScope.launch {
            val currentDoc = _currentLocalDirDoc.value ?: return@launch
            val subDoc = currentDoc.findFile(item.name)
            if (subDoc != null && subDoc.isDirectory) {
                _currentLocalDirDoc.value = subDoc
                _localBreadcrumbs.update { it + subDoc }
                loadLocalItems(subDoc)
            }
        }
    }

    fun navigateToLocalParent() {
        val crumbs = _localBreadcrumbs.value
        if (crumbs.size <= 1) return
        val newCrumbs = crumbs.dropLast(1)
        val parentDoc = newCrumbs.last()
        _localBreadcrumbs.value = newCrumbs
        _currentLocalDirDoc.value = parentDoc
        viewModelScope.launch {
            loadLocalItems(parentDoc)
        }
    }

    fun toggleSelectLocalItem(item: LocalItem) {
        _selectedLocalItems.update { current ->
            if (current.contains(item)) current - item else current + item
        }
    }

    fun selectAllLocalItems() {
        _selectedLocalItems.value = _localItems.value.toSet()
    }

    fun clearLocalSelection() {
        _selectedLocalItems.value = emptySet()
    }

    // Upload Operations
    fun uploadSelectedLocalItems() {
        val currentLocalDir = _currentLocalDirDoc.value
        val selected = _selectedLocalItems.value
        if (currentLocalDir == null || selected.isEmpty()) {
            viewModelScope.launch { _userMessage.emit("No local items selected") }
            return
        }

        val server = _activeServer.value ?: return
        val remoteTargetDir = _currentRemotePath.value

        viewModelScope.launch {
            val docFiles = selected.mapNotNull { item ->
                currentLocalDir.findFile(item.name)
            }

            if (docFiles.isEmpty()) {
                _userMessage.emit("Could not access selected files")
                return@launch
            }

            transferJob = launch(Dispatchers.IO) {
                val res = transferEngine.uploadItems(
                    localItems = docFiles,
                    targetRemoteDirectory = remoteTargetDir,
                    conflictResolution = _conflictResolution.value,
                    serverHost = "${server.username}@${server.host}:${server.port}"
                )
                withContext(Dispatchers.Main) {
                    res.onSuccess {
                        _userMessage.emit("Upload completed successfully")
                        clearLocalSelection()
                        refreshRemote()
                    }.onFailure { e ->
                        if (e !is kotlinx.coroutines.CancellationException) {
                            _userMessage.emit("Upload error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun uploadSingleExternalFile(fileUri: Uri) {
        val server = _activeServer.value ?: return
        val remoteTargetDir = _currentRemotePath.value

        viewModelScope.launch {
            val docFile = safManager.getDocumentFromUri(fileUri, isTree = false)
            if (docFile == null) {
                _userMessage.emit("Unable to open picked file")
                return@launch
            }

            transferJob = launch(Dispatchers.IO) {
                val res = transferEngine.uploadItems(
                    localItems = listOf(docFile),
                    targetRemoteDirectory = remoteTargetDir,
                    conflictResolution = _conflictResolution.value,
                    serverHost = "${server.username}@${server.host}:${server.port}"
                )
                withContext(Dispatchers.Main) {
                    res.onSuccess {
                        _userMessage.emit("File upload completed")
                        refreshRemote()
                    }.onFailure { e ->
                        if (e !is kotlinx.coroutines.CancellationException) {
                            _userMessage.emit("Upload failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    // Download Operations
    fun downloadSelectedRemoteItems() {
        val currentLocalDir = _currentLocalDirDoc.value
        if (currentLocalDir == null) {
            viewModelScope.launch { _userMessage.emit("Please select a local folder first") }
            return
        }

        val selected = _selectedRemoteItems.value.toList()
        if (selected.isEmpty()) {
            viewModelScope.launch { _userMessage.emit("No remote items selected") }
            return
        }

        val server = _activeServer.value ?: return

        transferJob = viewModelScope.launch(Dispatchers.IO) {
            val res = transferEngine.downloadItems(
                remoteItems = selected,
                targetLocalDirectory = currentLocalDir,
                conflictResolution = _conflictResolution.value,
                serverHost = "${server.username}@${server.host}:${server.port}"
            )
            withContext(Dispatchers.Main) {
                res.onSuccess {
                    _userMessage.emit("Download completed successfully")
                    clearRemoteSelection()
                    refreshLocal()
                }.onFailure { e ->
                    if (e !is kotlinx.coroutines.CancellationException) {
                        _userMessage.emit("Download error: ${e.message}")
                    }
                }
            }
        }
    }

    fun cancelCurrentTransfer() {
        transferJob?.cancel()
        transferJob = null
        viewModelScope.launch {
            _userMessage.emit("Transfer cancelled")
        }
    }

    // History Operations
    fun clearTransferHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
            _userMessage.emit("History cleared")
        }
    }

    override fun onCleared() {
        super.onCleared()
        transferJob?.cancel()
        sshManager.disconnect()
    }
}
