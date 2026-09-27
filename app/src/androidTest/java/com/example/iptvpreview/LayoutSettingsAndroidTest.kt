package com.example.iptvpreview

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.IptvRepository
import com.example.iptvpreview.data.LayoutPreset
import com.example.iptvpreview.data.database.IptvDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LayoutSettingsAndroidTest {
    @Test fun savesAndRestoresLayoutChoices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "layout-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val db = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java).build()
        try {
            val repo = IptvRepository(context, store = store, dao = db.channelDao(), database = db)
            assertEquals(LayoutPreset.BALANCED, repo.layoutPreset.first())
            assertFalse(repo.manualResize.first())
            repo.setLayoutPreset(LayoutPreset.LARGER_CHANNEL_LIST)
            repo.setManualResize(true)
            val restored = IptvRepository(context, store = store, dao = db.channelDao(), database = db)
            assertEquals(LayoutPreset.LARGER_CHANNEL_LIST, restored.layoutPreset.first())
            assertTrue(restored.manualResize.first())
            restored.setManualResize(false)
            assertEquals(LayoutPreset.LARGER_CHANNEL_LIST, restored.layoutPreset.first())
            assertFalse(restored.manualResize.first())
        } finally { job.cancelAndJoin(); db.close(); file.delete() }
    }
}
