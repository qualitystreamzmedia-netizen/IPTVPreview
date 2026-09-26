package com.example.iptvpreview.data

import com.example.iptvpreview.data.model.Playlist
import com.example.iptvpreview.data.model.Category
import org.json.JSONArray
import org.json.JSONObject

internal fun encodePlaylists(list: List<Playlist>): String = JSONArray().apply {
    list.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name)
        .put("type", p.type.name).put("configJson", p.configJson)
        .put("isActive", p.isActive).put("addedDate", p.addedDate)) }
}.toString()

internal fun decodePlaylists(json: String): List<Playlist> {
    val array = JSONArray(json)
    return (0 until array.length()).map { index ->
        val p = array.getJSONObject(index)
        Playlist(id = p.getString("id"), name = p.getString("name"),
            type = PlaylistType.valueOf(p.getString("type")), configJson = p.getString("configJson"),
            isActive = p.optBoolean("isActive", true), addedDate = p.optLong("addedDate", 0L))
    }
}

internal fun categorySummary(channels: List<Channel>, hidden: Set<String>): List<Category> {
    val counts = channels.filterNot { it.isHidden }.groupingBy { it.group }.eachCount()
    val firstOccurrences = mutableMapOf<String, Int>()
    channels.forEach { channel ->
        firstOccurrences[channel.group] = minOf(firstOccurrences[channel.group] ?: Int.MAX_VALUE, channel.orderIndex)
    }
    return (counts.keys + hidden.sorted()).map {
        Category(it, counts[it] ?: 0, it in hidden, firstOccurrences[it] ?: Int.MAX_VALUE)
    }.sortedBy { it.orderIndex }
}

internal fun applyCustomCategoryOrder(categories: List<Category>, customOrder: List<String>?): List<Category> {
    val positions = customOrder.orEmpty().distinct().withIndex().associate { it.value to it.index }
    return categories.sortedBy { positions[it.name] ?: Int.MAX_VALUE }
}

internal fun sortChannelsBySourceOrder(channels: List<Channel>, source: List<Channel>, customOrder: List<String>? = null): List<Channel> {
    val categoryOrder = applyCustomCategoryOrder(categorySummary(source, emptySet()), customOrder)
        .mapIndexed { index, category -> category.name to index }.toMap()
    return channels.sortedWith(compareBy<Channel> { categoryOrder[it.group] ?: Int.MAX_VALUE }.thenBy { it.orderIndex })
}
