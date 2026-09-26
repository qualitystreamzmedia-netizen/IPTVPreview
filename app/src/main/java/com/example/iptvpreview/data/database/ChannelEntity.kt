package com.example.iptvpreview.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels", indices = [Index(value = ["playlistId", "orderIndex"])])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val url: String,
    val group: String,
    val orderIndex: Int,
    val isFavorite: Boolean = false
)

fun ChannelEntity.toDomainModel() = com.example.iptvpreview.data.model.Channel(
    id = id, playlistId = playlistId, name = name, url = url, group = group,
    orderIndex = orderIndex, isFavorite = isFavorite
)
