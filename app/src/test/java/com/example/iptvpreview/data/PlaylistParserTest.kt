package com.example.iptvpreview.data

import org.junit.Assert.*
import org.junit.Test

class PlaylistParserTest {
    @Test fun parsesNamesGroupsRelativeUrlsAndDeduplicates() {
        val channels = parseM3u("""
            #EXTM3U
            #EXTINF:-1 tvg-id="NewsOne.example" group-title="News, Local",News One
            streams/one.m3u8
            #EXTINF:-1,Duplicate
            streams/one.m3u8
            #EXTINF:-1,Movie
            #EXTGRP:Films
            https://example.org/movie.mp4
        """.trimIndent(), "https://example.org/list/playlist.m3u")
        assertEquals(2, channels.size)
        assertEquals("News One", channels[0].name)
        assertEquals("NewsOne.example", channels[0].tvgId)
        assertNull(channels[1].tvgId)
        assertEquals("News, Local", channels[0].group)
        assertEquals("https://example.org/list/streams/one.m3u8", channels[0].url)
        assertEquals("Films", channels[1].group)
        assertEquals("${channels[0].playlistId}:${stableId(channels[0].url)}", channels[0].id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLoginPages() {
        parseM3u("<html>Please sign in</html>", "https://example.org/list")
    }

    @Test fun isolatesSourcesAndPreservesLogoAndEpgMetadata() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="guide-id" tvg-logo="https://example.org/logo.png",One
            https://example.org/one.ts
            #EXTINF:-1,Two
            https://example.org/two.ts
        """.trimIndent()
        val first = parseM3u(content, "https://example.org/list", "source-one")
        val second = parseM3u(content, "https://example.org/list", "source-two")
        assertNotEquals(first.first().id, second.first().id)
        assertEquals("source-one", first.first().playlistId)
        assertEquals("guide-id", first.first().epgId)
        assertEquals("https://example.org/logo.png", first.first().logoUrl)
        assertNull(first[1].logoUrl)
        assertNull(first[1].epgId)
        assertFalse(first.first().isHidden)
        val migrated = migrateFavoriteIds(setOf(stableId(first.first().url), "unrelated"), first)
        assertEquals(setOf(first.first().id, "unrelated"), migrated)
        assertEquals(migrated, migrateFavoriteIds(migrated, first))
        assertEquals(setOf("unrelated"), migrateFavoriteIds(migrated - first.first().id, first))
    }

    @Test fun acceptsBomAndSkipsUnsupportedSchemes() {
        val channels = parseM3u("\uFEFF#EXTM3U\n#EXTINF:-1,Unsupported\nrtsp://example.org/video\n#EXTINF:-1,Test\nhttps://example.org/test.ts", "https://example.org/list")
        assertEquals(1, channels.size)
        assertEquals("Test", channels.single().name)
    }
}
