package com.example.iptvpreview.data.model

data class EpgProgram(
    val channelId: String, // Matches the M3U tvg-id.
    val title: String,
    val description: String?,
    val startTime: Long, // Unix timestamp in milliseconds.
    val endTime: Long, // Exclusive Unix timestamp in milliseconds.
    val category: String? = null
) {
    fun isLive(): Boolean {
        val now = System.currentTimeMillis()
        return now >= startTime && now < endTime
    }
}
