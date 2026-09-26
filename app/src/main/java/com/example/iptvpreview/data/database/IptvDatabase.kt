package com.example.iptvpreview.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChannelEntity::class, EpgProgramEntity::class, com.example.iptvpreview.data.local.VodItemEntity::class], version = 5, exportSchema = true)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun vodDao(): com.example.iptvpreview.data.local.VodDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS vod_items (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, posterUrl TEXT, backdropUrl TEXT, durationMinutes INTEGER, year INTEGER, rating REAL, plot TEXT, seasonNumber INTEGER, episodeNumber INTEGER)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX index_channels_playlistId ON channels(playlistId)")
                db.execSQL("CREATE INDEX index_channels_group ON channels(`group`)")
            }
        }
        fun getDatabase(context: Context): IptvDatabase = getInstance(context)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN logoUrl TEXT")
                db.execSQL("ALTER TABLE channels ADD COLUMN epgId TEXT")
                db.execSQL("CREATE INDEX index_channels_playlistId_group ON channels(playlistId, `group`)")
                db.execSQL("CREATE TABLE epg_programs_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, channel_id TEXT NOT NULL, title TEXT NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER NOT NULL, description TEXT)")
                // Preserve legacy rows and IDs; unknown required values become empty/zero.
                db.execSQL("INSERT INTO epg_programs_new SELECT id, COALESCE(channel_id,''), COALESCE(title,''), COALESCE(start_time,0), COALESCE(end_time,0), description FROM epg_programs")
                db.execSQL("DROP TABLE epg_programs")
                db.execSQL("ALTER TABLE epg_programs_new RENAME TO epg_programs")
                db.execSQL("CREATE INDEX idx_epg_channel_time ON epg_programs(channel_id, start_time)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS epg_programs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, channel_id TEXT, title TEXT, start_time INTEGER, end_time INTEGER, description TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_epg_channel_time ON epg_programs(channel_id, start_time)")
            }
        }
        @Volatile private var instance: IptvDatabase? = null

        fun getInstance(context: Context): IptvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, IptvDatabase::class.java, "iptv_channels.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}
