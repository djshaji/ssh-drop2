package com.example.data.repository

import com.example.data.local.dao.KnownHostDao
import com.example.data.local.dao.ServerDao
import com.example.data.local.dao.TransferHistoryDao
import com.example.data.local.entity.KnownHostEntity
import com.example.data.local.entity.ServerEntity
import com.example.data.local.entity.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow

class ServerRepository(private val serverDao: ServerDao) {
    val allServers: Flow<List<ServerEntity>> = serverDao.getAllServers()

    suspend fun getServerById(id: Long): ServerEntity? = serverDao.getServerById(id)
    suspend fun insertServer(server: ServerEntity): Long = serverDao.insertServer(server)
    suspend fun updateServer(server: ServerEntity) = serverDao.updateServer(server)
    suspend fun deleteServer(server: ServerEntity) = serverDao.deleteServer(server)
    suspend fun updateLastConnected(id: Long, timestamp: Long) =
        serverDao.updateLastConnected(id, timestamp)
}

class KnownHostRepository(private val knownHostDao: KnownHostDao) {
    suspend fun getKnownHost(hostKey: String): KnownHostEntity? =
        knownHostDao.getKnownHost(hostKey)

    suspend fun saveKnownHost(knownHost: KnownHostEntity) =
        knownHostDao.insertKnownHost(knownHost)

    suspend fun removeKnownHost(hostKey: String) =
        knownHostDao.deleteKnownHost(hostKey)
}

class TransferHistoryRepository(private val historyDao: TransferHistoryDao) {
    val allHistory: Flow<List<TransferHistoryEntity>> = historyDao.getAllHistory()

    suspend fun insertHistory(history: TransferHistoryEntity): Long =
        historyDao.insertHistory(history)

    suspend fun clearHistory() = historyDao.clearHistory()
    suspend fun deleteById(id: Long) = historyDao.deleteHistoryById(id)
}
