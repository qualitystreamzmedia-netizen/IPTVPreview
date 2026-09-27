package com.example.iptvpreview.data

enum class LayoutPreset(val label: String, val browserWeight: Float, val categoryWeight: Float) {
    BALANCED("Balanced", 0.55f, 0.30f),
    LARGER_PLAYER("Larger Player", 0.40f, 0.30f),
    LARGER_CHANNEL_LIST("Larger Channel List", 0.68f, 0.24f)
}
