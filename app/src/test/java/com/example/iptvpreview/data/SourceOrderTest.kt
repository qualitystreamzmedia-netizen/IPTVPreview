package com.example.iptvpreview.data

import org.junit.Assert.*
import org.junit.Test

class SourceOrderTest {
    @Test fun customOrderIgnoresMissingNamesAndAppendsNewGroupsInSourceOrder() {
        val source = listOf(channel("s", "Sports", 0), channel("n", "News", 1), channel("m", "Movies", 2))
        val custom = listOf("Missing", "News", "News")
        assertEquals(listOf("n", "s", "m"), sortChannelsBySourceOrder(source, source, custom).map { it.id })
        assertEquals(listOf("s", "n", "m"), sortChannelsBySourceOrder(source, source, null).map { it.id })
    }
    private fun channel(id: String, group: String, position: Int) =
        Channel(id, "source", id, "https://example.org/$id", group, orderIndex = position)

    @Test fun groupsByFirstAppearanceThenChannelPosition() {
        val source = listOf(channel("s1", "Sports", 0), channel("n1", "News", 1), channel("s2", "Sports", 2))
        assertEquals(listOf("Sports", "News"), categorySummary(source, emptySet()).map { it.name })
        assertEquals(listOf("s1", "s2", "n1"), sortChannelsBySourceOrder(source, source).map { it.id })
        assertEquals(listOf("s2", "n1"), sortChannelsBySourceOrder(listOf(source[1], source[2]), source).map { it.id })
    }

    @Test fun keepsHiddenGroupsAndUsesMinimumStoredIndex() {
        val source = listOf(channel("late", "Sports", 8), channel("news", "News", 3), channel("early", "Sports", 1))
        val categories = categorySummary(source, setOf("Sports", "Missing"))
        assertEquals(listOf("Sports", "News", "Missing"), categories.map { it.name })
        assertEquals(1, categories.first().orderIndex)
        assertTrue(categories.first().isHidden)
        assertEquals(0, categories.last().count)
    }
}
