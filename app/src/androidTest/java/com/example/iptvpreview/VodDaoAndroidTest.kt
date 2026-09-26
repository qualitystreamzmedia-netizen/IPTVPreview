package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VodDaoAndroidTest {
    @Test fun roundTripsMetadataAndScopesQueriesAndDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.vodDao()
            val movie = VodItemEntity("p:movie", "p", VodType.MOVIE, "Movie", "https://example.org/movie.mp4",
                "poster", "backdrop", 120, 2024, 8.5f, "Plot")
            val episode = movie.copy(id = "p:episode", type = VodType.SERIES, durationMinutes = null,
                seasonNumber = 2, episodeNumber = 3)
            val other = movie.copy(id = "q:movie", playlistId = "q")
            dao.insertAll(listOf(movie, episode, other))
            assertEquals(movie, dao.getById(movie.id))
            assertEquals(episode, dao.getById(episode.id))
            assertEquals(listOf(movie), dao.getItems("p", VodType.MOVIE).first())
            assertEquals(listOf(episode), dao.getItems("p", VodType.SERIES).first())
            val updated = movie.copy(plot = "Updated")
            dao.insertAll(listOf(updated))
            assertEquals(listOf(updated), dao.getItems("p", VodType.MOVIE).first())
            dao.deleteByPlaylist("p")
            assertNull(dao.getById(movie.id))
            assertNull(dao.getById(episode.id))
            assertEquals(other, dao.getById(other.id))
        } finally { db.close() }
    }
}
