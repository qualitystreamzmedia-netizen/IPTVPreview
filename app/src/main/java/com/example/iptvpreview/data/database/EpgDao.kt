package com.example.iptvpreview.data.database

import androidx.room.Dao
import androidx.room.Query
import com.example.iptvpreview.data.model.EpgProgram

@Dao
interface EpgDao {
    // Explicit aliases map the database columns to the existing domain model.
    // End time is exclusive, matching EpgProgram.isLive().
    @Query("""
        SELECT channel_id AS channelId, title, description,
               start_time AS startTime, end_time AS endTime, NULL AS category
        FROM epg_programs
        WHERE channel_id = :chId AND title IS NOT NULL
          AND start_time <= :now AND end_time > :now
        ORDER BY start_time DESC, id DESC
        LIMIT 1
    """)
    suspend fun getCurrentProgram(chId: String, now: Long): EpgProgram?
}
