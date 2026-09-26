package com.example.iptvpreview.data

import org.junit.Assert.*
import org.junit.Test

class RecentChannelsTest {
    @Test fun promotesExistingWithoutDuplicates() {
        assertEquals(listOf("b", "a", "c"), updatedRecentIds(listOf("a", "b", "c", "b"), "b"))
    }
    @Test fun capsHistoryAtTwenty() {
        val result = updatedRecentIds((1..150).map { "$it" }, "new")
        assertEquals(20, result.size)
        assertEquals("new", result.first())
        assertEquals("19", result.last())
    }
}
