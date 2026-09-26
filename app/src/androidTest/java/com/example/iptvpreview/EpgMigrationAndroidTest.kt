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
            .addMigrations(IptvDatabase.MIGRATION_2_3).build()
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
            .addMigrations(IptvDatabase.MIGRATION_1_2, IptvDatabase.MIGRATION_2_3).build()
        try {
            assertTrue(db.channelDao().getChannelsByPlaylist("p").first().single().isFavorite)
            val sql = db.openHelper.writableDatabase
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
