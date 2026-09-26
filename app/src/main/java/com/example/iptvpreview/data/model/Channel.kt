package com.example.iptvpreview.data.model

data class Channel(
    val id: String,
    val playlistId: String,
    val name: String,
    val url: String,
    val group: String,
    val logoUrl: String? = null,
    val epgId: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val orderIndex: Int = 0
) {
    // Compatibility for the existing EPG UI and parser callers.
    val tvgId: String? get() = epgId
}
