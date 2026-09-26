package com.example.iptvpreview.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: PlaylistType,
    val configJson: String,
    val isActive: Boolean = true,
    val addedDate: Long = System.currentTimeMillis()
)

enum class PlaylistType { M3U, XTREAM }
