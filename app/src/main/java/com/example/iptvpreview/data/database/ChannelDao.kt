package com.example.iptvpreview.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :pid ORDER BY orderIndex ASC, id ASC")
    fun getChannelsByPlaylist(pid: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' OR `group` LIKE '%' || :query || '%' ORDER BY playlistId ASC, orderIndex ASC, id ASC LIMIT 500")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)
}
