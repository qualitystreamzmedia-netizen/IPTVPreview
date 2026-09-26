package com.example.iptvpreview.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VodDao {
    @Query("DELETE FROM vod_items WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteByPlaylistAndType(playlistId: String, type: VodType)

    @Query("SELECT * FROM vod_items WHERE type = :type ORDER BY name ASC, id ASC")
    fun getVodsByType(type: String): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC, id ASC LIMIT 50")
    fun searchVods(query: String): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId AND type = :type ORDER BY name COLLATE NOCASE, id")
    fun getItems(playlistId: String, type: VodType): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VodItemEntity>)

    @Query("DELETE FROM vod_items WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)
}
