package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.KnownHostEntity

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE hostKey = :hostKey")
    suspend fun getKnownHost(hostKey: String): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnownHost(knownHost: KnownHostEntity)

    @Query("DELETE FROM known_hosts WHERE hostKey = :hostKey")
    suspend fun deleteKnownHost(hostKey: String)
}
