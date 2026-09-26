package com.example.iptvpreview

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptvpreview.data.database.IptvDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EpgDaoAndroidTest {
    @Test fun currentProgramUsesExclusiveEndAndLatestOverlappingStart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java).build()
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO epg_programs(channel_id,title,start_time,end_time,description) VALUES ('a','First',100,200,'Details'),('a','Second',200,300,NULL),('b','Other',100,400,NULL),('a','',100,400,NULL)")
            val dao = db.epgDao()
            assertNull(dao.getCurrentProgram("a", 99))
            assertEquals("Details", dao.getCurrentProgram("a", 100)?.description)
            assertEquals("First", dao.getCurrentProgram("a", 199)?.title)
            val second = dao.getCurrentProgram("a", 200)
            assertEquals("Second", second?.title)
            assertEquals("a", second?.channelId)
            assertEquals(200L, second?.startTime)
            assertNull(second?.category)
            assertNull(dao.getCurrentProgram("a", 300))
            assertNull(dao.getCurrentProgram("missing", 200))
            sql.execSQL("INSERT INTO epg_programs(channel_id,title,start_time,end_time) VALUES ('a','Overlap',220,280),('a','Last tie',220,280)")
            assertEquals("Last tie", dao.getCurrentProgram("a", 250)?.title)
        } finally { db.close() }
    }
}
