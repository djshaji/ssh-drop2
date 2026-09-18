package com.example.ssh

import android.content.Context
import android.util.Base64
import com.example.data.local.entity.KnownHostEntity
import com.example.data.repository.KnownHostRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security

class SshManager(
    private val context: Context,
    private val knownHostRepository: KnownHostRepository,
    private val onHostKeyPrompt: (HostKeyVerificationRequest) -> Unit
) {

    init {
        setupBouncyCastle()
    }

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    val isConnected: Boolean
        get() = sshClient?.isConnected == true && sshClient?.isAuthenticated == true

    suspend fun connectAndAuthenticate(
        host: String,
        port: Int,
        username: String,
        authType: String,
        password: String?,
        keyContent: String?,
        passphrase: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val client = SSHClient()
            client.addHostKeyVerifier(createHostKeyVerifier())
            client.timeout = 20000
            client.connectTimeout = 15000

            client.connect(host, port)

            if (authType.equals("KEY", ignoreCase = true)) {
                if (keyContent.isNullOrBlank()) {
                    client.disconnect()
                    return@withContext Result.failure(IllegalArgumentException("Private key file content is empty or unreadable."))
                }

                val passFinder = if (!passphrase.isNullOrEmpty()) {
                    object : PasswordFinder {
                        override fun reqPassword(resource: Resource<*>?): CharArray {
                            return passphrase.toCharArray()
                        }
                        override fun shouldRetry(resource: Resource<*>?): Boolean {
                            return false
                        }
                    }
                } else null

                val keyProvider = client.loadKeys(keyContent, null, passFinder)
                client.authPublickey(username, keyProvider)
            } else {
                client.authPassword(username, password ?: "")
            }

            if (!client.isAuthenticated) {
                client.disconnect()
                return@withContext Result.failure(IllegalStateException("Authentication failed for user: $username"))
            }

            sshClient = client
            sftpClient = client.newSFTPClient()

            Result.success(Unit)
        } catch (e: Throwable) {
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun listRemoteDirectory(remotePath: String, showHidden: Boolean = false): List<RemoteItem> =
        withContext(Dispatchers.IO) {
            val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
            val rawItems = sftp.ls(remotePath)
            rawItems.mapNotNull { item ->
                val name = item.name
                if (name == "." || name == "..") return@mapNotNull null
                if (!showHidden && name.startsWith(".")) return@mapNotNull null

                val isDir = item.isDirectory
                val permissionsStr = formatFileMode(item.attributes.mode)
                RemoteItem(
                    name = name,
                    path = item.path,
                    isDirectory = isDir,
                    size = if (isDir) 0L else item.attributes.size,
                    permissions = permissionsStr,
                    lastModified = item.attributes.mtime * 1000L
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }

    suspend fun createDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
            sftp.mkdir(remotePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectoriesRecursively(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
            sftp.mkdirs(remotePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRemoteItem(remotePath: String, isDirectory: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
                if (isDirectory) {
                    deleteRemoteDirectoryRecursive(sftp, remotePath)
                } else {
                    sftp.rm(remotePath)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun deleteRemoteDirectoryRecursive(sftp: SFTPClient, path: String) {
        val items = sftp.ls(path)
        for (item in items) {
            if (item.name == "." || item.name == "..") continue
            if (item.isDirectory) {
                deleteRemoteDirectoryRecursive(sftp, item.path)
            } else {
                sftp.rm(item.path)
            }
        }
        sftp.rmdir(path)
    }

    suspend fun renameRemoteItem(oldPath: String, newPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
                sftp.rename(oldPath, newPath)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun remoteItemExists(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sftp = sftpClient ?: return@withContext false
            sftp.statExistence(remotePath) != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun openRemoteFileOutputStream(remotePath: String): RemoteFile = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
        sftp.open(remotePath, setOf(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC))
    }

    suspend fun openRemoteFileInputStream(remotePath: String): RemoteFile = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP client is not connected")
        sftp.open(remotePath, setOf(OpenMode.READ))
    }

    fun getSftpClient(): SFTPClient? = sftpClient

    fun disconnect() {
        try {
            sftpClient?.close()
        } catch (_: Exception) {}
        sftpClient = null

        try {
            sshClient?.disconnect()
        } catch (_: Exception) {}
        sshClient = null
    }

    private fun createHostKeyVerifier(): HostKeyVerifier {
        return object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val hostKey = "$hostname:$port"
                val rawKey = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
                val sha256Digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
                val fingerprint = "SHA256:" + Base64.encodeToString(
                    sha256Digest,
                    Base64.NO_PADDING or Base64.NO_WRAP
                )
                val algorithm = key.algorithm ?: "Unknown"

                val known = runBlocking { knownHostRepository.getKnownHost(hostKey) }
                if (known != null) {
                    return known.fingerprint == fingerprint
                }

                val deferred = CompletableDeferred<Boolean>()
                val request = HostKeyVerificationRequest(
                    host = hostname,
                    port = port,
                    fingerprint = fingerprint,
                    keyAlgorithm = algorithm,
                    rawKey = rawKey,
                    deferredResult = deferred
                )
                onHostKeyPrompt(request)

                val trusted = runBlocking {
                    try {
                        deferred.await()
                    } catch (_: Exception) {
                        false
                    }
                }

                if (trusted) {
                    runBlocking {
                        knownHostRepository.saveKnownHost(
                            KnownHostEntity(
                                hostKey = hostKey,
                                fingerprint = fingerprint,
                                keyAlgorithm = algorithm,
                                rawKey = rawKey
                            )
                        )
                    }
                }
                return trusted
            }

            override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> {
                return emptyList()
            }
        }
    }

    private fun formatFileMode(mode: FileMode): String {
        val mask = mode.mask
        val sb = StringBuilder()
        sb.append(if (mode.type == FileMode.Type.DIRECTORY) 'd' else if (mode.type == FileMode.Type.SYMLINK) 'l' else '-')
        // User (400, 200, 100)
        sb.append(if ((mask and 0x100) != 0) 'r' else '-')
        sb.append(if ((mask and 0x080) != 0) 'w' else '-')
        sb.append(if ((mask and 0x040) != 0) 'x' else '-')
        // Group (040, 020, 010)
        sb.append(if ((mask and 0x020) != 0) 'r' else '-')
        sb.append(if ((mask and 0x010) != 0) 'w' else '-')
        sb.append(if ((mask and 0x008) != 0) 'x' else '-')
        // Other (004, 002, 001)
        sb.append(if ((mask and 0x004) != 0) 'r' else '-')
        sb.append(if ((mask and 0x002) != 0) 'w' else '-')
        sb.append(if ((mask and 0x001) != 0) 'x' else '-')
        return sb.toString()
    }

    companion object {
        private var bouncyCastleInitialized = false

        fun setupBouncyCastle() {
            if (!bouncyCastleInitialized) {
                try {
                    Security.removeProvider("BC")
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                } catch (_: Exception) {
                    try {
                        Security.addProvider(BouncyCastleProvider())
                    } catch (_: Exception) {}
                }
                bouncyCastleInitialized = true
            }
        }
    }
}
