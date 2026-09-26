package com.example.iptvpreview.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VodType { MOVIE, SERIES }

@Entity(tableName = "vod_items")
data class VodItemEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val type: VodType,
    val name: String,
    val url: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val durationMinutes: Int?,
    val year: Int?,
    val rating: Float?,
    val plot: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)
