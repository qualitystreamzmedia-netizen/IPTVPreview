package com.example.iptvpreview

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.iptvpreview.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryAndroidTest {
    @Test fun migratesMergesHidesAndRestoresWithoutTouchingUserData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        var fail = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (fail) 500 else 200).message("Test")
                .body("#EXTM3U\n#EXTINF:-1 group-title=\"News\",One\nhttps://example.org/one.ts".toResponseBody()).build()
        }.build()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.iptvpreview.data.database.IptvDatabase::class.java).build()
        try {
            store.edit {
                it[stringPreferencesKey("session")] = """{"type":"M3U","config":{"url":"https://example.org/list"}}"""
                it[stringSetPreferencesKey("favorites")] = setOf(stableId("https://example.org/one.ts"))
            }
            val repo = IptvRepository(context, client, store, db.channelDao())
            repo.restoreLastSession()
            assertEquals(1, repo.playlists.value.size)
            assertTrue(repo.allChannels.first().single().isFavorite)
            val recentId = repo.allChannels.first().single().id
            repo.recordRecentChannel(recentId)
            repo.recordRecentChannel(recentId)
            assertEquals(listOf(recentId), repo.recentChannelIds.first())
            assertNull(store.data.first()[stringPreferencesKey("session")])
            repo.addPlaylist("Second", PlaylistType.M3U, mapOf("url" to "https://example.org/other"))
            assertEquals(2, repo.allChannels.first().map { it.id }.distinct().size)
            val second = repo.playlists.value.last()
            repo.toggleCategoryVisibility("News", true)
            assertTrue(repo.visibleChannels.first().isEmpty())
            assertTrue(repo.categories.first().single().isHidden)
            assertEquals(2, repo.categories.first().single().count)
            repo.toggleCategoryVisibility("News", false)
            assertEquals(2, repo.visibleChannels.first().size)
            fail = true
            repo.refreshPlaylistChannels(second.id)
            assertEquals(2, repo.allChannels.first().size)
            assertNotNull(repo.error.value)
            fail = false
            repo.togglePlaylistActive(second.id, false)
            assertEquals(1, repo.allChannels.first().size)
            val restored = IptvRepository(context, client, store, db.channelDao())
            fail = true // Cached channels must survive an offline restart.
            restored.restoreLastSession()
            assertEquals(listOf(recentId), restored.recentChannelIds.first())
            assertEquals(repo.playlists.value, restored.playlists.value)
            assertEquals(1, restored.allChannels.first().size)
            fail = false
            restored.togglePlaylistActive(second.id, true)
            assertEquals(2, restored.allChannels.first().size)
            restored.removePlaylist(second.id)
            assertEquals(1, restored.playlists.value.size)
            assertEquals(1, restored.allChannels.first().size)
        } finally {
            db.close()
            job.cancelAndJoin()
            file.delete()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
