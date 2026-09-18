package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_hosts")
data class KnownHostEntity(
    @PrimaryKey
    val hostKey: String, // e.g. "192.168.1.100:22"
    val fingerprint: String, // SHA256:...
    val keyAlgorithm: String, // e.g. "ssh-ed25519", "ssh-rsa"
    val rawKey: String, // Base64 representation
    val trustedDate: Long = System.currentTimeMillis()
)
