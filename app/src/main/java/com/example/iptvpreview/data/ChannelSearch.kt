package com.example.iptvpreview.data

internal fun searchChannels(channels: List<Channel>, query: String): List<Channel> {
    val term = query.trim()
    if (term.isEmpty()) return channels
    return channels.filter {
        it.name.contains(term, ignoreCase = true) || it.group.contains(term, ignoreCase = true)
    }
}
