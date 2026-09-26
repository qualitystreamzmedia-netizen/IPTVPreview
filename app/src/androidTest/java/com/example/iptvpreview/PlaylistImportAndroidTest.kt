package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.database.*
import com.example.iptvpreview.data.stableId
import com.example.iptvpreview.data.worker.importM3uFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlaylistImportAndroidTest {
    @Test fun batchesReplaceAtomicallyAndPreserveFavorites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java).build()
        val file = File.createTempFile("import-test", ".m3u", context.cacheDir)
        try {
            val dao = db.channelDao()
            val favoriteId = "p:${stableId("https://example.org/0") }"
            dao.insertAll(listOf(ChannelEntity(favoriteId, "p", "Old", "https://example.org/0", "Old", 0, true),
                ChannelEntity("stale", "p", "Stale", "https://example.org/stale", "Old", 1),
                ChannelEntity("other", "q", "Other", "https://example.org/other", "Other", 0)))
            file.bufferedWriter().use { writer ->
                writer.appendLine("#EXTM3U")
                repeat(2001) { writer.appendLine("#EXTINF:-1 group-title=\"News\",Channel $it"); writer.appendLine("/$it") }
            }
            val batches = mutableListOf<Int>()
            assertEquals(2001, importM3uFile(file, "https://example.org/list.m3u", "p", db) { batches.add(it) })
            assertEquals(listOf(1000, 2000, 2001), batches)
            val rows = dao.getChannelsByPlaylist("p").first()
            assertEquals(2001, rows.size)
            assertTrue(rows.first().isFavorite)
            assertEquals(2000, rows.last().orderIndex)
            assertEquals(1, dao.getChannelsByPlaylist("q").first().size)
            try {
                importM3uFile(file, "https://example.org/list.m3u", "p", db) { throw CancellationException("cancel test") }
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertEquals(rows, dao.getChannelsByPlaylist("p").first())
            file.writeText("<html>Not a playlist</html>")
            try { importM3uFile(file, "https://example.org/list.m3u", "p", db); fail("Expected invalid input") }
            catch (_: IllegalArgumentException) { }
            assertEquals(rows, dao.getChannelsByPlaylist("p").first())
        } finally { file.delete(); db.close() }
    }
}
