package com.example.iptvpreview.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChannelEntity::class, EpgProgramEntity::class], version = 2, exportSchema = true)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS epg_programs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, channel_id TEXT, title TEXT, start_time INTEGER, end_time INTEGER, description TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_epg_channel_time ON epg_programs(channel_id, start_time)")
            }
        }
        @Volatile private var instance: IptvDatabase? = null

        fun getInstance(context: Context): IptvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, IptvDatabase::class.java, "iptv_channels.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
