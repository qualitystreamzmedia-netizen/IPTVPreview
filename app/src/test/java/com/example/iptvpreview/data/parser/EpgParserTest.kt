package com.example.iptvpreview.data.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class EpgParserTest {
    @Test fun detectsAndParsesGzipWithoutRelyingOnUrlExtension() = runBlocking {
        val xml = "<tv><programme channel='test' start='20231027170000' stop='20231027180000'><title>Compressed</title></programme></tv>"
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(xml.toByteArray()) }
        val result = EpgParser().parseCurrentOrNext(ByteArrayInputStream(bytes.toByteArray()), 1698426000000L)
        assertEquals("Compressed", result["test"]?.title)
    }

    @Test fun rejectsNonXmltvDocuments() = runBlocking {
        val stream = TrackedStream("<html><body>Sign in</body></html>")
        try { EpgParser().parse(stream); fail("Expected an XMLTV document") }
        catch (_: SAXException) { assertTrue(stream.closed) }
    }

    @Test fun selectsLiveOrNearestUpcomingRegardlessOfFeedOrder() = runBlocking {
        val entries = listOf(
            "<programme channel='a' start='20231027190000' stop='20231027200000'><title>Later</title></programme>",
            "<programme channel='a' start='20231027160000' stop='20231027180000'><title>Older live</title></programme>",
            "<programme channel='a' start='20231027170000' stop='20231027190000'><title>Current</title></programme>",
            "<programme channel='b' start='20231027200000' stop='20231027210000'><title>Distant</title></programme>",
            "<programme channel='b' start='20231027180000' stop='20231027190000'><title>Next</title></programme>",
            "<programme channel='c' start='20231027160000' stop='20231027170000'><title>Ended</title></programme>"
        )
        for (ordered in listOf(entries, entries.reversed())) {
            val stream = TrackedStream("<tv>${ordered.joinToString("")}</tv>")
            val result = EpgParser().parseCurrentOrNext(stream, 1698426000000L)
            assertEquals(setOf("a", "b"), result.keys)
            assertEquals("Current", result["a"]?.title)
            assertEquals("Next", result["b"]?.title)
            assertTrue(stream.closed)
        }
    }

    private class TrackedStream(xml: String) : ByteArrayInputStream(xml.toByteArray()) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun preservesOffsetsFieldsAndFirstLanguage() = runBlocking {
        val stream = TrackedStream("""
            <tv><programme channel="test.id" start="20231027190000 +0200" stop="20231027200000 +0200">
            <title lang="en"> News &amp; <![CDATA[Weather]]> </title><title lang="fr">Autre titre</title>
            <desc>Daily bulletin</desc><category>News</category></programme></tv>
        """.trimIndent())
        val result = EpgParser().parse(stream).single()
        assertEquals("test.id", result.channelId)
        assertEquals("News & Weather", result.title)
        assertEquals("Daily bulletin", result.description)
        assertEquals("News", result.category)
        assertEquals(1698426000000L, result.startTime)
        assertEquals(3600000L, result.endTime - result.startTime)
        assertTrue(stream.closed)
    }

    @Test fun defaultsToUtcAndAcceptsMinutePrecision() = runBlocking {
        val result = EpgParser().parse(TrackedStream("""
            <tv><programme channel="a" start="202310271700" stop="202310271800 +0000"><title>Test</title></programme></tv>
        """.trimIndent())).single()
        assertEquals(1698426000000L, result.startTime)
        assertNull(result.description)
        assertNull(result.category)
    }

    @Test fun skipsInvalidProgramsWithoutLeakingState() = runBlocking {
        val result = EpgParser().parse(TrackedStream("""
            <tv>
            <programme channel="a" start="20230230000000" stop="20230301000000"><title>Invalid date</title></programme>
            <programme channel="a" start="20231027180000" stop="20231027170000"><title>Reversed</title></programme>
            <programme channel="a" start="20231027170000" stop="20231027180000"><title> </title></programme>
            <programme channel="a" start="20231027170000" stop="20231027180000"><title>Valid</title></programme>
            </tv>
        """.trimIndent()))
        assertEquals(listOf("Valid"), result.map { it.title })
    }

    @Test fun closesStreamOnMalformedXml() = runBlocking {
        val stream = TrackedStream("<tv><programme>")
        try { EpgParser().parse(stream); fail("Expected malformed XML to fail") }
        catch (_: SAXException) { assertTrue(stream.closed) }
    }

    @Test fun rejectsDtdAndClosesStream() = runBlocking {
        val stream = TrackedStream("<!DOCTYPE tv [<!ENTITY title SYSTEM 'file:///should-not-be-read'>]><tv/>")
        try { EpgParser().parse(stream); fail("Expected DTD rejection") }
        catch (_: SAXException) { assertTrue(stream.closed) }
    }
}
