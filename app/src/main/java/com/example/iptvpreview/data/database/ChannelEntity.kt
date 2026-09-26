package com.example.iptvpreview.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels", indices = [Index(value = ["playlistId", "orderIndex"]), Index(value = ["playlistId", "group"])])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val url: String,
    val group: String,
    val orderIndex: Int,
    val isFavorite: Boolean = false,
    val logoUrl: String? = null,
    val epgId: String? = null
)

fun ChannelEntity.toDomainModel() = com.example.iptvpreview.data.model.Channel(
    id = id, playlistId = playlistId, name = name, url = url, group = group,
    orderIndex = orderIndex, isFavorite = isFavorite, logoUrl = logoUrl, epgId = epgId
)
