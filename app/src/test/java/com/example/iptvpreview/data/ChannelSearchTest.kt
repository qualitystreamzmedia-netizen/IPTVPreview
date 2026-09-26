package com.example.iptvpreview.data

import org.junit.Assert.*
import org.junit.Test

class ChannelSearchTest {
    private val channels = listOf(
        Channel("p:1", "p", "World News", "https://example.org/1", "International"),
        Channel("p:2", "p", "Sports Live", "https://example.org/2", "Sports")
    )
    @Test fun matchesNamesAndCategoriesIgnoringCaseAndOuterWhitespace() {
        assertEquals(listOf(channels[0]), searchChannels(channels, "  WORLD  "))
        assertEquals(listOf(channels[0]), searchChannels(channels, "INTERNATIONAL"))
        assertEquals(listOf(channels[1]), searchChannels(channels, "sports"))
    }
    @Test fun blankAndNoMatchQueries() {
        assertEquals(channels, searchChannels(channels, " \t\n"))
        assertTrue(searchChannels(channels, "missing").isEmpty())
        assertTrue(searchChannels(emptyList(), "news").isEmpty())
    }
}
