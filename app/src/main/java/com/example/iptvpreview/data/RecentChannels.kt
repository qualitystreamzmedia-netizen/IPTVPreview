package com.example.iptvpreview.data

internal fun updatedRecentIds(previous: List<String>, id: String): List<String> =
    (listOf(id) + previous.filterNot { it == id }).distinct().take(20)
