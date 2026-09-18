package com.example.ssh

import kotlinx.coroutines.CompletableDeferred

data class RemoteItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val lastModified: Long
)

data class HostKeyVerificationRequest(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyAlgorithm: String,
    val rawKey: String,
    val deferredResult: CompletableDeferred<Boolean>
)

sealed interface SshConnectionState {
    data object Disconnected : SshConnectionState
    data class Connecting(val message: String) : SshConnectionState
    data class Connected(
        val serverName: String,
        val host: String,
        val port: Int,
        val username: String,
        val currentRemotePath: String
    ) : SshConnectionState
    data class Error(val message: String) : SshConnectionState
}
