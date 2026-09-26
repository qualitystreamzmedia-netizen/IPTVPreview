package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.importXtreamVod
import com.example.iptvpreview.data.local.*
import com.example.iptvpreview.data.model.Playlist
import com.example.iptvpreview.data.model.PlaylistType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class VodImportAndroidTest {
    @Test fun importsPlayableMoviesAndEpisodesAndRollsBackFailures() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var fail = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("u &", request.url.queryParameter("username"))
            assertEquals("p/+", request.url.queryParameter("password"))
            val body = when (request.url.queryParameter("action")) {
                "get_vod_streams" -> """[{"stream_id":7,"name":"Movie","container_extension":"mkv","duration_secs":7200,"year_released":2024,"rating":"8.5"}]"""
                "get_series" -> """[{"series_id":4,"name":"Show","cover":"poster"}]"""
                "get_series_info" -> """{"info":{"cover":"poster"},"episodes":{"2":[{"id":9,"title":"Pilot","episode_num":1,"container_extension":"mp4","info":{"plot":"Details"}}]}}"""
                else -> error("Unexpected endpoint")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (fail && request.url.queryParameter("action") == "get_series_info") 503 else 200)
                .message("Fixture").body(body.toResponseBody()).build()
        }.build()
        val playlist = Playlist(id = "p", name = "Provider", type = PlaylistType.XTREAM,
            configJson = """{"host":"https://example.org","user":"u &","pass":"p/+"}""")
        try {
            importXtreamVod(playlist, client, db)
            val movie = db.vodDao().getById("p_mov_7")!!
            assertEquals(120, movie.durationMinutes)
            assertEquals(2024, movie.year)
            assertEquals(listOf("movie", "u &", "p/+", "7.mkv"), movie.url.toHttpUrl().pathSegments)
            val episode = db.vodDao().getById("p_ep_9")!!
            assertEquals(2, episode.seasonNumber); assertEquals(1, episode.episodeNumber)
            assertEquals("Details", episode.plot); assertEquals("poster", episode.posterUrl)
            assertEquals(listOf("series", "u &", "p/+", "9.mp4"), episode.url.toHttpUrl().pathSegments)
            db.vodDao().insertAll(listOf(movie.copy(id = "other", playlistId = "other")))
            importXtreamVod(playlist, client, db)
            assertEquals(2, db.vodDao().getVodsByType("MOVIE").first().size)
            fail = true
            var rejected = false
            try { importXtreamVod(playlist, client, db) } catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
            assertEquals(movie, db.vodDao().getById(movie.id))
            assertEquals(episode, db.vodDao().getById(episode.id))
            assertNotNull(db.vodDao().getById("other"))
        } finally { db.close() }
    }
}
