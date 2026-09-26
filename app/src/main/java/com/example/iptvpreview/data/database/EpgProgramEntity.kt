package com.example.iptvpreview.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "epg_programs", indices = [Index(value = ["channel_id", "start_time"], name = "idx_epg_channel_time")])
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val dbId: Long = 0,
    @ColumnInfo(name = "channel_id") val channelId: String,
    val title: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    val description: String? = null
)
