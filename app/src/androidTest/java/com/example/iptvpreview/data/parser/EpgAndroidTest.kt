package com.example.iptvpreview.data.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(AndroidJUnit4::class)
class EpgAndroidTest {
    @Test fun parsesCompressedGuideWithAndroidSax() = runBlocking {
        val xml = "<tv><programme channel='Demo.id' start='20231027190000 +0200' stop='20231027200000 +0200'><title>Android Guide Test</title></programme></tv>"
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
        val programs = EpgParser().parseCurrentOrNext(ByteArrayInputStream(output.toByteArray()), 1698426000000L)
        assertEquals("Android Guide Test", programs["Demo.id"]?.title)
        assertEquals(1698426000000L, programs["Demo.id"]?.startTime)
    }
}
