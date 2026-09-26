package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.database.IptvDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EpgMigrationAndroidTest {
    @Test fun versionTwoMigrationPreservesLegacyEpgRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "epg-v2-${java.util.UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE channels (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, `group` TEXT NOT NULL, orderIndex INTEGER NOT NULL, isFavorite INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_channels_playlistId_orderIndex ON channels(playlistId, orderIndex)")
            old.execSQL("CREATE TABLE epg_programs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, channel_id TEXT, title TEXT, start_time INTEGER, end_time INTEGER, description TEXT)")
            old.execSQL("CREATE INDEX idx_epg_channel_time ON epg_programs(channel_id,start_time)")
            old.execSQL("INSERT INTO epg_programs VALUES (7,'news','News',100,200,'Kept'),(8,NULL,NULL,NULL,NULL,NULL)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, IptvDatabase::class.java, name)
            .addMigrations(IptvDatabase.MIGRATION_2_3, IptvDatabase.MIGRATION_3_4, IptvDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("Kept", db.epgDao().getCurrentProgram("news", 150)?.description)
            db.openHelper.readableDatabase.query("SELECT id,channel_id,start_time FROM epg_programs ORDER BY id").use {
                assertEquals(2, it.count); assertTrue(it.moveToFirst()); assertEquals(7L, it.getLong(0))
                assertTrue(it.moveToNext()); assertEquals("", it.getString(1)); assertEquals(0L, it.getLong(2))
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun migrationPreservesChannelsAndAddsIndexedEpgTable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "epg-migration-${java.util.UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE channels (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, `group` TEXT NOT NULL, orderIndex INTEGER NOT NULL, isFavorite INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_channels_playlistId_orderIndex ON channels(playlistId, orderIndex)")
            old.execSQL("INSERT INTO channels VALUES ('kept', 'p', 'News', 'https://example.org', 'News', 0, 1)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, IptvDatabase::class.java, name)
            .addMigrations(IptvDatabase.MIGRATION_1_2, IptvDatabase.MIGRATION_2_3, IptvDatabase.MIGRATION_3_4, IptvDatabase.MIGRATION_4_5).build()
        try {
            assertTrue(db.channelDao().getChannelsByPlaylist("p").first().single().isFavorite)
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO vod_items(id,playlistId,type,name,url,durationMinutes,year,rating,plot) VALUES ('movie','p','MOVIE','Movie','https://example.org/movie.mp4',120,2024,8.5,'Plot')")
            sql.execSQL("INSERT INTO vod_items(id,playlistId,type,name,url,seasonNumber,episodeNumber) VALUES ('episode','p','SERIES','Episode','https://example.org/episode.m3u8',2,3)")
            sql.query("SELECT type,durationMinutes,year,rating,plot,seasonNumber,episodeNumber FROM vod_items ORDER BY id").use {
                assertEquals(2, it.count)
                assertTrue(it.moveToFirst()); assertEquals("SERIES", it.getString(0))
                assertTrue(it.isNull(1)); assertEquals(2, it.getInt(5)); assertEquals(3, it.getInt(6))
                assertTrue(it.moveToNext()); assertEquals("MOVIE", it.getString(0))
                assertEquals(120, it.getInt(1)); assertEquals(2024, it.getInt(2))
                assertEquals(8.5f, it.getFloat(3), 0f); assertEquals("Plot", it.getString(4))
                assertTrue(it.isNull(5)); assertTrue(it.isNull(6))
            }
            listOf("playlistId", "group").forEach { column ->
                sql.query("PRAGMA index_info('index_channels_$column')").use {
                    assertTrue(it.moveToFirst()); assertEquals(column, it.getString(2))
                }
            }
            sql.execSQL("INSERT INTO epg_programs(channel_id,title,start_time,end_time) VALUES ('news','Now',1700000000000,1700003600000)")
            sql.query("SELECT id,start_time FROM epg_programs").use {
                assertTrue(it.moveToFirst()); assertTrue(it.getLong(0) > 0); assertEquals(1700000000000L, it.getLong(1))
            }
            sql.query("PRAGMA index_info('idx_epg_channel_time')").use {
                assertTrue(it.moveToFirst()); assertEquals("channel_id", it.getString(2))
                assertTrue(it.moveToNext()); assertEquals("start_time", it.getString(2))
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
