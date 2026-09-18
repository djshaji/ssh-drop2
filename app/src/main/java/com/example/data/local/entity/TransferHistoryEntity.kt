package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val direction: String, // "UPLOAD" or "DOWNLOAD"
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: String, // "SUCCESS", "FAILED", "CANCELLED"
    val serverHost: String,
    val remotePath: String,
    val localUriOrPath: String,
    val durationMs: Long,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
