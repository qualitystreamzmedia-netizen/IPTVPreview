package com.example.iptvpreview

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.iptvpreview.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ParentalSecurityAndroidTest {
    @Test fun pinLocksPersistAndCannotBeChangedWithoutAuthentication() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "security-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            val security = ParentalSecurity(store)
            val channel = Channel("one", "source", "News", "https://example.org", "News")
            assertTrue(security.isChannelLocked(channel))
            security.restore()
            assertFalse(security.isChannelLocked(channel))
            assertTrue(security.setPin("1234"))
            assertFalse(security.setPin("9876"))
            assertFalse(security.setCategoryLock("News", true, "0000"))
            assertTrue(security.setCategoryLock("News", true, "1234"))
            assertTrue(security.isChannelLocked(channel))
            val restored = ParentalSecurity(store)
            restored.restore()
            assertTrue(restored.isPinSet.value)
            assertTrue(restored.isChannelLocked(channel))
            assertTrue(restored.setPin("5678", "1234"))
            assertFalse(restored.verifyPin("1234"))
            assertTrue(restored.verifyPin("5678"))
            assertTrue(restored.setCategoryLock("News", false, "5678"))
            assertFalse(restored.isChannelLocked(channel))
            repeat(5) { assertFalse(restored.verifyPin("0000")) }
            assertFalse(restored.verifyPin("5678"))
            val lockedOut = ParentalSecurity(store)
            assertFalse(lockedOut.verifyPin("5678"))
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
