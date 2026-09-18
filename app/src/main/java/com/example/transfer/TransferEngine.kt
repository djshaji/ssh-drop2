package com.example.transfer

import androidx.documentfile.provider.DocumentFile
import com.example.data.local.entity.TransferHistoryEntity
import com.example.data.repository.TransferHistoryRepository
import com.example.saf.LocalFileEntry
import com.example.saf.SafManager
import com.example.ssh.RemoteItem
import com.example.ssh.SshManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.SFTPClient
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class TransferEngine(
    private val safManager: SafManager,
    private val sshManager: SshManager,
    private val historyRepository: TransferHistoryRepository
) {

    private val _progress = MutableStateFlow(ActiveTransferProgress())
    val progress: StateFlow<ActiveTransferProgress> = _progress.asStateFlow()

    fun dismissTransferSheet() {
        _progress.update { it.copy(isExpanded = false) }
    }

    fun expandTransferSheet() {
        _progress.update { it.copy(isExpanded = true) }
    }

    fun resetProgress() {
        _progress.value = ActiveTransferProgress()
    }

    suspend fun uploadItems(
        localItems: List<DocumentFile>,
        targetRemoteDirectory: String,
        conflictResolution: ConflictResolution = ConflictResolution.OVERWRITE,
        serverHost: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val allEntriesToUpload = mutableListOf<LocalFileEntry>()

        for (item in localItems) {
            allEntriesToUpload.addAll(safManager.collectFilesRecursively(item))
        }

        // Filter out directories for file upload, but create the directories on Linux
        val dirEntries = allEntriesToUpload.filter { it.isDirectory }
        val fileEntries = allEntriesToUpload.filter { !it.isDirectory }

        // Create remote directories first
        for (dir in dirEntries) {
            val remoteDirPath = cleanPath("$targetRemoteDirectory/${dir.relativePath}")
            sshManager.createDirectoriesRecursively(remoteDirPath)
        }

        val queueItems = fileEntries.map { entry ->
            TransferQueueItem(
                id = UUID.randomUUID().toString(),
                name = entry.name,
                direction = TransferDirection.UPLOAD,
                totalBytes = entry.size,
                status = TransferItemStatus.QUEUED
            )
        }

        val totalBytes = fileEntries.sumOf { it.size }

        _progress.value = ActiveTransferProgress(
            isRunning = true,
            currentFileName = if (fileEntries.isNotEmpty()) fileEntries.first().name else "",
            direction = TransferDirection.UPLOAD,
            currentFileIndex = 0,
            totalFiles = fileEntries.size,
            overallBytesTransferred = 0L,
            overallTotalBytes = totalBytes,
            currentFileBytesTransferred = 0L,
            currentFileTotalBytes = 0L,
            speedBytesPerSec = 0.0,
            etaSeconds = 0L,
            queueItems = queueItems,
            isExpanded = true
        )

        var overallBytesDone = 0L
        var lastSpeedSampleTime = System.currentTimeMillis()
        var bytesSinceLastSample = 0L
        var smoothedSpeed = 0.0

        try {
            fileEntries.forEachIndexed { index, entry ->
                currentCoroutineContext().ensureActive()
                val queueItem = queueItems[index]

                var remoteFilePath = cleanPath("$targetRemoteDirectory/${entry.relativePath}")

                // Conflict Resolution
                if (sshManager.remoteItemExists(remoteFilePath)) {
                    when (conflictResolution) {
                        ConflictResolution.SKIP -> {
                            updateQueueStatus(queueItem.id, TransferItemStatus.SKIPPED)
                            overallBytesDone += entry.size
                            _progress.update {
                                it.copy(
                                    currentFileIndex = index + 1,
                                    overallBytesTransferred = overallBytesDone
                                )
                            }
                            return@forEachIndexed
                        }
                        ConflictResolution.KEEP_BOTH -> {
                            remoteFilePath = generateUniqueRemotePath(remoteFilePath)
                        }
                        ConflictResolution.OVERWRITE -> {
                            // Proceed to overwrite
                        }
                    }
                }

                updateQueueStatus(queueItem.id, TransferItemStatus.TRANSFERRING)
                _progress.update {
                    it.copy(
                        currentFileName = entry.name,
                        currentFileIndex = index + 1,
                        currentFileBytesTransferred = 0L,
                        currentFileTotalBytes = entry.size
                    )
                }

                val fileStartTime = System.currentTimeMillis()
                var fileBytesDone = 0L
                var inStream: InputStream? = null
                var outStream: OutputStream? = null

                try {
                    inStream = safManager.openInputStream(entry.uri)
                        ?: throw IllegalStateException("Cannot open input stream for: ${entry.name}")
                    val remoteFile = sshManager.openRemoteFileOutputStream(remoteFilePath)
                    outStream = remoteFile.RemoteFileOutputStream(0, 32768)

                    val buffer = ByteArray(32768)
                    var read: Int

                    while (inStream.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        outStream.write(buffer, 0, read)

                        fileBytesDone += read
                        overallBytesDone += read
                        bytesSinceLastSample += read

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastSpeedSampleTime
                        if (timeDiff >= 400) {
                            val instantSpeed = (bytesSinceLastSample.toDouble() / (timeDiff.toDouble() / 1000.0))
                            smoothedSpeed = if (smoothedSpeed <= 0.0) instantSpeed else (smoothedSpeed * 0.7 + instantSpeed * 0.3)
                            val remainingBytes = (totalBytes - overallBytesDone).coerceAtLeast(0L)
                            val eta = if (smoothedSpeed > 0) (remainingBytes / smoothedSpeed).toLong() else 0L

                            lastSpeedSampleTime = now
                            bytesSinceLastSample = 0L

                            _progress.update {
                                it.copy(
                                    overallBytesTransferred = overallBytesDone,
                                    currentFileBytesTransferred = fileBytesDone,
                                    speedBytesPerSec = smoothedSpeed,
                                    etaSeconds = eta
                                )
                            }
                        }
                    }

                    outStream.flush()
                    updateQueueStatus(queueItem.id, TransferItemStatus.COMPLETED)

                    historyRepository.insertHistory(
                        TransferHistoryEntity(
                            fileName = entry.name,
                            direction = "UPLOAD",
                            bytesTransferred = fileBytesDone,
                            totalBytes = entry.size,
                            status = "SUCCESS",
                            serverHost = serverHost,
                            remotePath = remoteFilePath,
                            localUriOrPath = entry.uri.toString(),
                            durationMs = System.currentTimeMillis() - fileStartTime
                        )
                    )
                } catch (e: CancellationException) {
                    updateQueueStatus(queueItem.id, TransferItemStatus.CANCELLED)
                    historyRepository.insertHistory(
                        TransferHistoryEntity(
                            fileName = entry.name,
                            direction = "UPLOAD",
                            bytesTransferred = fileBytesDone,
                            totalBytes = entry.size,
                            status = "CANCELLED",
                            serverHost = serverHost,
                            remotePath = remoteFilePath,
                            localUriOrPath = entry.uri.toString(),
                            durationMs = System.currentTimeMillis() - fileStartTime,
                            errorMessage = "Cancelled by user"
                        )
                    )
                    throw e
                } catch (e: Exception) {
                    updateQueueStatus(queueItem.id, TransferItemStatus.FAILED, e.message)
                    historyRepository.insertHistory(
                        TransferHistoryEntity(
                            fileName = entry.name,
                            direction = "UPLOAD",
                            bytesTransferred = fileBytesDone,
                            totalBytes = entry.size,
                            status = "FAILED",
                            serverHost = serverHost,
                            remotePath = remoteFilePath,
                            localUriOrPath = entry.uri.toString(),
                            durationMs = System.currentTimeMillis() - fileStartTime,
                            errorMessage = e.message
                        )
                    )
                    throw e
                } finally {
                    try { inStream?.close() } catch (_: Exception) {}
                    try { outStream?.close() } catch (_: Exception) {}
                }
            }

            _progress.update {
                it.copy(
                    isRunning = false,
                    speedBytesPerSec = 0.0,
                    etaSeconds = 0L,
                    overallBytesTransferred = totalBytes
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            _progress.update { it.copy(isRunning = false, speedBytesPerSec = 0.0, etaSeconds = 0L) }
            Result.failure(e)
        } catch (e: Exception) {
            _progress.update { it.copy(isRunning = false, speedBytesPerSec = 0.0, etaSeconds = 0L) }
            Result.failure(e)
        }
    }

    suspend fun downloadItems(
        remoteItems: List<RemoteItem>,
        targetLocalDirectory: DocumentFile,
        conflictResolution: ConflictResolution = ConflictResolution.OVERWRITE,
        serverHost: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val sftp = sshManager.getSftpClient()
            ?: return@withContext Result.failure(IllegalStateException("SFTP client is not connected"))

        val allRemoteEntries = mutableListOf<RemoteTransferEntry>()

        for (item in remoteItems) {
            allRemoteEntries.addAll(collectRemoteFilesRecursively(sftp, item))
        }

        val dirEntries = allRemoteEntries.filter { it.isDirectory }
        val fileEntries = allRemoteEntries.filter { !it.isDirectory }

        // Create local directories first using SAF
        for (dir in dirEntries) {
            safManager.createOrGetSubdirectory(targetLocalDirectory, dir.relativePath)
        }

        val queueItems = fileEntries.map { entry ->
            TransferQueueItem(
                id = UUID.randomUUID().toString(),
                name = entry.name,
                direction = TransferDirection.DOWNLOAD,
                totalBytes = entry.size,
                status = TransferItemStatus.QUEUED
            )
        }

        val totalBytes = fileEntries.sumOf { it.size }

        _progress.value = ActiveTransferProgress(
            isRunning = true,
            currentFileName = if (fileEntries.isNotEmpty()) fileEntries.first().name else "",
            direction = TransferDirection.DOWNLOAD,
            currentFileIndex = 0,
            totalFiles = fileEntries.size,
            overallBytesTransferred = 0L,
            overallTotalBytes = totalBytes,
            currentFileBytesTransferred = 0L,
            currentFileTotalBytes = 0L,
            speedBytesPerSec = 0.0,
            etaSeconds = 0L,
            queueItems = queueItems,
            isExpanded = true
        )

        var overallBytesDone = 0L
        var lastSpeedSampleTime = System.currentTimeMillis()
        var bytesSinceLastSample = 0L
        var smoothedSpeed = 0.0

        try {
            fileEntries.forEachIndexed { index, entry ->
                currentCoroutineContext().ensureActive()
                val queueItem = queueItems[index]

                // Resolve parent directory in local SAF
                val relativeDir = entry.relativePath.substringBeforeLast('/', "")
                val targetDirDoc = if (relativeDir.isNotEmpty()) {
                    safManager.createOrGetSubdirectory(targetLocalDirectory, relativeDir)
                        ?: targetLocalDirectory
                } else {
                    targetLocalDirectory
                }

                // Check conflict and create DocumentFile
                val targetDocFile = safManager.prepareTargetFile(
                    targetDir = targetDirDoc,
                    fileName = entry.name,
                    mimeType = safManager.guessMimeType(entry.name),
                    conflictResolution = conflictResolution
                )

                if (targetDocFile == null) {
                    // Skipped
                    updateQueueStatus(queueItem.id, TransferItemStatus.SKIPPED)
                    overallBytesDone += entry.size
                    _progress.update {
                        it.copy(
                            currentFileIndex = index + 1,
                            overallBytesTransferred = overallBytesDone
                        )
                    }
                    return@forEachIndexed
                }

                updateQueueStatus(queueItem.id, TransferItemStatus.TRANSFERRING)
                _progress.update {
                    it.copy(
                        currentFileName = entry.name,
                        currentFileIndex = index + 1,
                        currentFileBytesTransferred = 0L,
                        currentFileTotalBytes = entry.size
                    )
                }

                val fileStartTime = System.currentTimeMillis()
                var fileBytesDone = 0L
                var inStream: InputStream? = null
                var outStream: OutputStream? = null

                try {
                    val remoteFile = sshManager.openRemoteFileInputStream(entry.remoteFullPath)
                    inStream = remoteFile.RemoteFileInputStream(0)
                    outStream = safManager.openOutputStream(targetDocFile.uri)
                        ?: throw IllegalStateException("Cannot open local SAF output stream for: ${entry.name}")

                    val buffer = ByteArray(32768)
                    var read: Int

                    while (inStream.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        outStream.write(buffer, 0, read)

                        fileBytesDone += read
                        overallBytesDone += read
                        bytesSinceLastSample += read

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastSpeedSampleTime
                        if (timeDiff >= 400) {
                            val instantSpeed = (bytesSinceLastSample.toDouble() / (timeDiff.toDouble() / 1000.0))
                            smoothedSpeed = if (smoothedSpeed <= 0.0) instantSpeed else (smoothedSpeed * 0.7 + instantSpeed * 0.3)
                            val remainingBytes = (totalBytes - overallBytesDone).coerceAtLeast(0L)
                            val eta = if (smoothedSpeed > 0) (remainingBytes / smoothedSpeed).toLong() else 0L

                            lastSpeedSampleTime = now
                            bytesSinceLastSample = 0L

                            _progress.update {
                                it.copy(
                                    overallBytesTransferred = overallBytesDone,
                                    currentFileBytesTransferred = fileBytesDone,
                                    speedBytesPerSec = smoothedSpeed,
                                    etaSeconds = eta
                                )
                            }
                        }
                    }

                    outStream.flush()
                    updateQueueStatus(queueItem.id, TransferItemStatus.COMPLETED)

                    historyRepository.insertHistory(
                        TransferHistoryEntity(
                            fileName = entry.name,
                            direction = "DOWNLOAD",
                            bytesTransferred = fileBytesDone,
                            totalBytes = entry.size,
                            status = "SUCCESS",
                            serverHost = serverHost,
                            remotePath = entry.remoteFullPath,
                            localUriOrPath = targetDocFile.uri.toString(),
                            durationMs = System.currentTimeMillis() - fileStartTime
                        )
                    )
                } catch (e: CancellationException) {
                    updateQueueStatus(queueItem.id, TransferItemStatus.CANCELLED)
                    historyRepository.insertHistory(
                        TransferHistoryEntity(
                            fileName = entry.name,
                            direction = "DOWNLOAD",
                            bytesTransferred = fileBytesDone,
                            totalBytes = entry.size,
                            status = "CANCELLED",
                            serverHost = serverHost,
                            remotePath = entry.remoteFullPath,
                            localUriOrPath = targetDocFile.uri.toString(),
                            durationMs = System.currentTimeMillis() - fileStartTime,
                            errorMessage = "Cancelled by user"
                        )
                    )
                    throw e
                } catch (e: Exception) {
                    updateQueueStatus(queueItem.id, TransferItemStatus.FAILED, e.message)
                    historyRepository.insertHistory(
                        TransferHistoryEntity(
                            fileName = entry.name,
                            direction = "DOWNLOAD",
                            bytesTransferred = fileBytesDone,
                            totalBytes = entry.size,
                            status = "FAILED",
                            serverHost = serverHost,
                            remotePath = entry.remoteFullPath,
                            localUriOrPath = targetDocFile.uri.toString(),
                            durationMs = System.currentTimeMillis() - fileStartTime,
                            errorMessage = e.message
                        )
                    )
                    throw e
                } finally {
                    try { inStream?.close() } catch (_: Exception) {}
                    try { outStream?.close() } catch (_: Exception) {}
                }
            }

            _progress.update {
                it.copy(
                    isRunning = false,
                    speedBytesPerSec = 0.0,
                    etaSeconds = 0L,
                    overallBytesTransferred = totalBytes
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            _progress.update { it.copy(isRunning = false, speedBytesPerSec = 0.0, etaSeconds = 0L) }
            Result.failure(e)
        } catch (e: Exception) {
            _progress.update { it.copy(isRunning = false, speedBytesPerSec = 0.0, etaSeconds = 0L) }
            Result.failure(e)
        }
    }

    private fun updateQueueStatus(id: String, status: TransferItemStatus, error: String? = null) {
        _progress.update { current ->
            val updatedQueue = current.queueItems.map {
                if (it.id == id) it.copy(status = status, errorMessage = error) else it
            }
            current.copy(queueItems = updatedQueue)
        }
    }

    private suspend fun generateUniqueRemotePath(path: String): String {
        val lastSlash = path.lastIndexOf('/')
        val dir = if (lastSlash >= 0) path.substring(0, lastSlash) else ""
        val fileName = if (lastSlash >= 0) path.substring(lastSlash + 1) else path

        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var counter = 1
        var candidate = if (dir.isNotEmpty()) "$dir/$baseName ($counter)$ext" else "$baseName ($counter)$ext"
        while (sshManager.remoteItemExists(candidate)) {
            counter++
            candidate = if (dir.isNotEmpty()) "$dir/$baseName ($counter)$ext" else "$baseName ($counter)$ext"
        }
        return candidate
    }

    private fun cleanPath(path: String): String {
        return path.replace("//+", "/")
    }

    private fun collectRemoteFilesRecursively(
        sftp: SFTPClient,
        rootItem: RemoteItem,
        basePath: String = ""
    ): List<RemoteTransferEntry> {
        val result = mutableListOf<RemoteTransferEntry>()
        val relPath = if (basePath.isEmpty()) rootItem.name else "$basePath/${rootItem.name}"

        var isDir = rootItem.isDirectory
        var itemSize = rootItem.size
        var targetRemotePath = rootItem.path

        if (rootItem.isSymlink) {
            try {
                val stat = sftp.stat(rootItem.path)
                isDir = stat.mode.type == FileMode.Type.DIRECTORY
                itemSize = if (isDir) 0L else stat.size
                targetRemotePath = sftp.canonicalize(rootItem.path)
            } catch (_: Exception) {
                // broken link or unreachable target
            }
        }

        if (isDir) {
            result.add(
                RemoteTransferEntry(
                    name = rootItem.name,
                    relativePath = relPath,
                    remoteFullPath = targetRemotePath,
                    size = 0L,
                    isDirectory = true
                )
            )
            try {
                val children = sftp.ls(targetRemotePath)
                for (child in children) {
                    if (child.name == "." || child.name == "..") continue
                    val isChildSymlink = child.attributes.mode.type == FileMode.Type.SYMLINK
                    var isChildDir = child.isDirectory
                    var childSize = if (isChildDir) 0L else child.attributes.size
                    if (isChildSymlink) {
                        try {
                            val cStat = sftp.stat(child.path)
                            isChildDir = cStat.mode.type == FileMode.Type.DIRECTORY
                            childSize = if (isChildDir) 0L else cStat.size
                        } catch (_: Exception) {}
                    }
                    val childItem = RemoteItem(
                        name = child.name,
                        path = child.path,
                        isDirectory = isChildDir,
                        size = childSize,
                        permissions = "",
                        lastModified = child.attributes.mtime * 1000L,
                        isSymlink = isChildSymlink
                    )
                    result.addAll(collectRemoteFilesRecursively(sftp, childItem, relPath))
                }
            } catch (_: Exception) {}
        } else {
            result.add(
                RemoteTransferEntry(
                    name = rootItem.name,
                    relativePath = relPath,
                    remoteFullPath = targetRemotePath,
                    size = itemSize,
                    isDirectory = false
                )
            )
        }
        return result
    }
}

data class RemoteTransferEntry(
    val name: String,
    val relativePath: String,
    val remoteFullPath: String,
    val size: Long,
    val isDirectory: Boolean
)
