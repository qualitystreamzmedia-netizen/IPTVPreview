package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.database.IptvDatabase
import com.example.iptvpreview.data.importEpgStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EpgImportAndroidTest {
    @Test fun batchesGzipCleanupAndFailedReplacement() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java).build()
        val now = 1704110400000L // 2024-01-01 12:00 UTC
        fun programme(id: String, start: String, end: String) =
            "<programme channel=\"$id\" start=\"$start\" stop=\"$end\"><title>$id</title><desc>Details</desc></programme>"
        val entries = (0..1000).joinToString("") {
            programme("c$it", "20240101130000 +0200", "20240101150000 +0200")
        }
        fun count(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM epg_programs").use {
            it.moveToFirst(); it.getInt(0)
        }
        try {
            withTimeout(30000) {
                val xml = "<tv>" + entries +
                    programme("old", "20231230110000 +0000", "20231231115959 +0000") +
                    programme("boundary", "20231231110000 +0000", "20231231120000 +0000") + "</tv>"
                val compressed = ByteArrayOutputStream().also { out ->
                    GZIPOutputStream(out).use { it.write(xml.toByteArray()) }
                }.toByteArray()
                var closed = false
                val stream = object : ByteArrayInputStream(compressed) {
                    override fun close() { closed = true; super.close() }
                }
                val snapshot = importEpgStream(db, stream, now)
                assertTrue(closed)
                assertEquals(1001, snapshot.size)
                assertEquals(1002, count())
                assertEquals("Details", db.epgDao().getCurrentProgram("c1000", now)?.description)
                assertEquals(now - 3600000, snapshot.getValue("c0").startTime)
                importEpgStream(db, ByteArrayInputStream(compressed), now)
                assertEquals(1002, count())
                var failed = false
                try {
                    importEpgStream(db, ByteArrayInputStream(("<tv>" + entries + "<broken").toByteArray()), now)
                } catch (expected: org.xml.sax.SAXException) { failed = true }
                assertTrue("Malformed XML must fail", failed)
                assertEquals("Previous committed guide must survive", 1002, count())
                assertEquals("c1000", db.epgDao().getCurrentProgram("c1000", now)?.title)
            }
        } finally { db.close() }
    }
}
