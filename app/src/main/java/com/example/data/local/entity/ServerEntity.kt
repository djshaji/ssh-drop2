package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "PASSWORD", // "PASSWORD" or "KEY"
    val password: String? = null,
    val keyUri: String? = null,
    val keyFileName: String? = null,
    val keyPassphrase: String? = null,
    val initialRemoteDir: String = "/home",
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
