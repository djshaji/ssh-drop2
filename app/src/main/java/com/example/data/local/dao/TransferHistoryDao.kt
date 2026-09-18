package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TransferHistoryEntity): Long

    @Query("DELETE FROM transfer_history")
    suspend fun clearHistory()

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)
}
