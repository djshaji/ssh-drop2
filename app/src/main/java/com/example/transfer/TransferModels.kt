package com.example.transfer

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}

enum class TransferItemStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED
}

enum class ConflictResolution {
    OVERWRITE,
    SKIP,
    KEEP_BOTH
}

data class TransferQueueItem(
    val id: String,
    val name: String,
    val direction: TransferDirection,
    val totalBytes: Long,
    val bytesTransferred: Long = 0,
    val status: TransferItemStatus = TransferItemStatus.QUEUED,
    val errorMessage: String? = null
)

data class ActiveTransferProgress(
    val isRunning: Boolean = false,
    val currentFileName: String = "",
    val direction: TransferDirection = TransferDirection.UPLOAD,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val overallBytesTransferred: Long = 0L,
    val overallTotalBytes: Long = 0L,
    val currentFileBytesTransferred: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val speedBytesPerSec: Double = 0.0,
    val etaSeconds: Long = 0L,
    val queueItems: List<TransferQueueItem> = emptyList(),
    val isExpanded: Boolean = true
) {
    val overallProgressFraction: Float
        get() = if (overallTotalBytes > 0) {
            (overallBytesTransferred.toFloat() / overallTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val currentFileProgressFraction: Float
        get() = if (currentFileTotalBytes > 0) {
            (currentFileBytesTransferred.toFloat() / currentFileTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val speedFormatted: String
        get() = formatSpeed(speedBytesPerSec)

    val etaFormatted: String
        get() = formatEta(etaSeconds)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, index.toDouble())
            return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
        }

        fun formatSpeed(bytesPerSec: Double): String {
            if (bytesPerSec <= 0) return "0 KB/s"
            val mb = bytesPerSec / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.2f MB/s", mb)
            } else {
                val kb = bytesPerSec / 1024.0
                String.format(java.util.Locale.US, "%.1f KB/s", kb)
            }
        }

        fun formatEta(seconds: Long): String {
            if (seconds <= 0) return "--"
            if (seconds < 60) return "${seconds}s remaining"
            val mins = seconds / 60
            val secs = seconds % 60
            return "${mins}m ${secs}s remaining"
        }
    }
}
