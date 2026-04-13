package com.vigilex.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PendingEventEntity)

    @Query("SELECT * FROM pending_events ORDER BY timestamp ASC LIMIT 50")
    suspend fun getPending(): List<PendingEventEntity>

    @Query("DELETE FROM pending_events WHERE localId = :localId")
    suspend fun delete(localId: String)

    @Query("UPDATE pending_events SET retryCount = retryCount + 1 WHERE localId = :localId")
    suspend fun incrementRetry(localId: String)

    // Discard events that have failed too many times (> 10) to avoid stale data accumulation
    @Query("DELETE FROM pending_events WHERE retryCount > 10")
    suspend fun purgeStale()
}
