package com.example.iptvpreview.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface EpgDao {
    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()
    // End time is exclusive, matching EpgProgram.isLive().
    @Query("""
        SELECT * FROM epg_programs
        WHERE channel_id = :chId AND title != ''
          AND start_time <= :now AND end_time > :now
        ORDER BY start_time DESC, id DESC
        LIMIT 1
    """)
    suspend fun getCurrentProgram(chId: String, now: Long): EpgProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE end_time < :cutoffTime")
    suspend fun deleteOldPrograms(cutoffTime: Long)
}
