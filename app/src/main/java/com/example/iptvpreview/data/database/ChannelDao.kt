package com.example.iptvpreview.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE (:playlistId IS NULL OR playlistId = :playlistId) AND (:favoritesOnly = 0 OR isFavorite = 1) AND (:query = '' OR name LIKE '%' || :query || '%' OR `group` LIKE '%' || :query || '%') ORDER BY playlistId, orderIndex, id")
    fun filterChannels(playlistId: String?, query: String, favoritesOnly: Boolean): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC, id ASC")
    fun getFavorites(): Flow<List<ChannelEntity>>

    @Query("UPDATE channels SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :pid")
    suspend fun deleteByPlaylist(pid: String)

    @androidx.room.Transaction
    suspend fun replacePlaylistChannels(playlistId: String, channels: List<ChannelEntity>) {
        require(channels.all { it.playlistId == playlistId })
        deletePlaylistChannels(playlistId)
        channels.chunked(1000).forEach { insertAll(it) }
    }
    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deletePlaylistChannels(playlistId: String)

    @Query("SELECT id FROM channels WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun favoriteIds(playlistId: String): List<String>

    @Query("SELECT * FROM channels ORDER BY `group` COLLATE NOCASE ASC, orderIndex ASC, id ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :pid ORDER BY orderIndex ASC, id ASC")
    fun getChannelsByPlaylist(pid: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE (:playlistId IS NULL OR playlistId = :playlistId) AND (name LIKE '%' || :query || '%' OR `group` LIKE '%' || :query || '%') ORDER BY playlistId ASC, orderIndex ASC, id ASC LIMIT 500")
    fun searchChannels(query: String, playlistId: String? = null): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)
}
