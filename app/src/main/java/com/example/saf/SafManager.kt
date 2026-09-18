package com.example.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.transfer.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class LocalFileEntry(
    val name: String,
    val relativePath: String,
    val uri: Uri,
    val size: Long,
    val isDirectory: Boolean
)

class SafManager(private val context: Context) {

    fun takePersistablePermission(treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Permission might already be held or not persistable
        }
    }

    suspend fun listFolder(folderUri: Uri): List<LocalItem> = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            ?: return@withContext emptyList()
        if (!documentFile.exists() || !documentFile.isDirectory) {
            return@withContext emptyList()
        }

        val files = documentFile.listFiles()
        files.mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            LocalItem(
                name = name,
                uri = file.uri,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                mimeType = file.type
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun listDirectoryDocument(documentFile: DocumentFile): List<LocalItem> = withContext(Dispatchers.IO) {
        if (!documentFile.exists() || !documentFile.isDirectory) {
            return@withContext emptyList()
        }
        documentFile.listFiles().mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            LocalItem(
                name = name,
                uri = file.uri,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                mimeType = file.type
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun collectFilesRecursively(
        rootDoc: DocumentFile,
        basePath: String = ""
    ): List<LocalFileEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LocalFileEntry>()
        val name = rootDoc.name ?: "unnamed"
        val currentRelPath = if (basePath.isEmpty()) name else "$basePath/$name"

        if (rootDoc.isDirectory) {
            result.add(
                LocalFileEntry(
                    name = name,
                    relativePath = currentRelPath,
                    uri = rootDoc.uri,
                    size = 0L,
                    isDirectory = true
                )
            )
            for (child in rootDoc.listFiles()) {
                result.addAll(collectFilesRecursively(child, currentRelPath))
            }
        } else {
            result.add(
                LocalFileEntry(
                    name = name,
                    relativePath = currentRelPath,
                    uri = rootDoc.uri,
                    size = rootDoc.length(),
                    isDirectory = false
                )
            )
        }
        result
    }

    suspend fun getDocumentFromUri(uri: Uri, isTree: Boolean = true): DocumentFile? = withContext(Dispatchers.IO) {
        if (isTree) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }

    fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    fun openOutputStream(uri: Uri, mode: String = "wt"): OutputStream? {
        return context.contentResolver.openOutputStream(uri, mode)
    }

    suspend fun createOrGetSubdirectory(
        parentDoc: DocumentFile,
        relativeSubDirPath: String
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val segments = relativeSubDirPath.trim('/').split('/').filter { it.isNotEmpty() }
        var current = parentDoc
        for (segment in segments) {
            val existing = current.findFile(segment)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else {
                current.createDirectory(segment) ?: return@withContext null
            }
        }
        current
    }

    suspend fun prepareTargetFile(
        targetDir: DocumentFile,
        fileName: String,
        mimeType: String?,
        conflictResolution: ConflictResolution
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val resolvedMime = mimeType ?: guessMimeType(fileName)
        val existing = targetDir.findFile(fileName)

        if (existing != null) {
            when (conflictResolution) {
                ConflictResolution.SKIP -> {
                    return@withContext null
                }
                ConflictResolution.OVERWRITE -> {
                    existing.delete()
                    return@withContext targetDir.createFile(resolvedMime, fileName)
                }
                ConflictResolution.KEEP_BOTH -> {
                    val uniqueName = generateUniqueFileName(targetDir, fileName)
                    return@withContext targetDir.createFile(resolvedMime, uniqueName)
                }
            }
        }
        targetDir.createFile(resolvedMime, fileName)
    }

    private fun generateUniqueFileName(targetDir: DocumentFile, originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var counter = 1
        var candidate = "$baseName ($counter)$extension"
        while (targetDir.findFile(candidate) != null) {
            counter++
            candidate = "$baseName ($counter)$extension"
        }
        return candidate
    }

    fun guessMimeType(fileName: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(fileName).lowercase()
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }
}
