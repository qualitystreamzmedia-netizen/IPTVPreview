package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.database.ChannelEntity
import com.example.iptvpreview.data.database.IptvDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChannelDaoAndroidTest {
    @Test fun preservesPlaylistOrderSearchesNamesAndGroupsAndReplacesById() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java).build()
        try {
            val dao = db.channelDao()
            val news = ChannelEntity("a", "p", "World News", "https://example.org/a", "International", 2)
            val sports = ChannelEntity("b", "p", "Match", "https://example.org/b", "Sports", 0)
            dao.insertAll(listOf(news, sports, news.copy(id = "c", playlistId = "q")))
            assertEquals(listOf("b", "a"), dao.getChannelsByPlaylist("p").first().map { it.id })
            assertEquals(listOf("b"), dao.searchChannels("sports").first().map { it.id })
            assertEquals(2, dao.searchChannels("NEWS").first().size)
            val repo = com.example.iptvpreview.data.IptvRepository(context, dao)
            assertEquals(listOf("b", "a", "c"), repo.getFilteredChannels(null, " ").first().map { it.id })
            assertEquals(listOf("b", "a"), repo.getFilteredChannels("p", "").first().map { it.id })
            assertEquals(listOf("a"), repo.getFilteredChannels("p", " NEWS ").first().map { it.id })
            assertTrue(repo.getFilteredChannels("missing", "News").first().isEmpty())
            assertEquals("Sports", repo.getFilteredChannels("p", "sports").first().single().group)
            dao.insertAll(listOf(news.copy(name = "Updated", isFavorite = true)))
            assertTrue(dao.getChannelsByPlaylist("p").first().last().isFavorite)
            assertEquals("Updated", dao.searchChannels("Updated").first().single().name)
            dao.insertAll((0..505).map { news.copy(id = "extra$it", playlistId = "extra") })
            assertEquals(500, dao.searchChannels("").first().size)
        } finally { db.close() }
    }
}
