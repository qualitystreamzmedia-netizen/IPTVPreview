package com.example.iptvpreview.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChannelEntity::class], version = 1, exportSchema = true)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile private var instance: IptvDatabase? = null

        fun getInstance(context: Context): IptvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, IptvDatabase::class.java, "iptv_channels.db")
                .build().also { instance = it }
        }
    }
}
