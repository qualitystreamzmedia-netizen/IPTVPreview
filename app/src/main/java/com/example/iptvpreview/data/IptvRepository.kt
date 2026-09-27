package com.example.iptvpreview.data

import android.content.Context
import com.example.iptvpreview.data.database.ChannelDao
import com.example.iptvpreview.data.database.IptvDatabase
import com.example.iptvpreview.data.database.toDomainModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import com.example.iptvpreview.data.database.ChannelEntity
import com.example.iptvpreview.data.model.EpgProgram
import com.example.iptvpreview.data.model.Playlist
import com.example.iptvpreview.data.model.Category
import com.example.iptvpreview.data.parser.EpgParser
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

typealias Channel = com.example.iptvpreview.data.model.Channel
typealias PlaylistType = com.example.iptvpreview.data.model.PlaylistType
private val Context.playlistStore by preferencesDataStore("playlists")

class IptvRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build(),
    private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> = context.applicationContext.playlistStore,
    private val dao: ChannelDao = IptvDatabase.getInstance(context).channelDao(),
    private val database: IptvDatabase = IptvDatabase.getInstance(context)
) {
    private val vodImportLock = Mutex()
    val movies = database.vodDao().getVodsByType("MOVIE")
    val series = database.vodDao().getVodsByType("SERIES")
    suspend fun importVodContent(playlist: Playlist) = vodImportLock.withLock {
        importXtreamVod(playlist, client, database)
    }
    constructor(context: Context, dao: ChannelDao) : this(context = context, dao = dao,
        client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build())

    fun getFilteredChannels(playlistId: String?, query: String, showOnlyFavorites: Boolean = false): Flow<List<Channel>> =
        dao.filterChannels(playlistId, query.trim(), showOnlyFavorites)
            .map { rows -> rows.map { it.toDomainModel() } }.flowOn(Dispatchers.IO)

    fun getCategoriesForCurrentView(selectedPlaylistId: String?, searchQuery: String, showOnlyFavorites: Boolean): Flow<List<Category>> =
        getFilteredChannels(selectedPlaylistId, searchQuery, showOnlyFavorites)
            .map { categorySummary(it, emptySet()) }.distinctUntilChanged()

    val security = ParentalSecurity(store)
    private fun customOrderKey(playlistId: String) = stringPreferencesKey("custom_order_$playlistId")
    private fun decodeCategoryOrder(json: String?): List<String>? = try {
        json?.let { value ->
            val array = JSONArray(value)
            (0 until array.length()).map { array.getString(it) }.distinct()
        }
    } catch (_: org.json.JSONException) { null }
    val customCategoryOrders = store.data.map { preferences ->
        preferences.asMap().entries.mapNotNull { (key, value) ->
            if (!key.name.startsWith("custom_order_")) null
            else decodeCategoryOrder(value as? String)?.let { key.name.removePrefix("custom_order_") to it }
        }.toMap()
    }.distinctUntilChanged()
    suspend fun saveCustomCategoryOrder(playlistId: String, orderedCategories: List<String>) {
        require(playlistId.isNotBlank())
        store.edit { it[customOrderKey(playlistId)] = JSONArray(orderedCategories.distinct()).toString() }
    }
    suspend fun getCustomCategoryOrder(playlistId: String): List<String>? =
        decodeCategoryOrder(store.data.first()[customOrderKey(playlistId)])
    suspend fun resetCustomCategoryOrder(playlistId: String) {
        store.edit { it.remove(customOrderKey(playlistId)) }
    }
    private val uiScaleKey = floatPreferencesKey("ui_scale_factor")
    private val layoutPresetKey = stringPreferencesKey("layout_preset")
    private val manualResizeKey = androidx.datastore.preferences.core.booleanPreferencesKey("manual_resize")
    val layoutPreset = store.data.map { prefs ->
        LayoutPreset.entries.find { it.name == prefs[layoutPresetKey] } ?: LayoutPreset.BALANCED
    }.distinctUntilChanged()
    val manualResize = store.data.map { it[manualResizeKey] ?: false }.distinctUntilChanged()
    suspend fun setLayoutPreset(preset: LayoutPreset) { store.edit { it[layoutPresetKey] = preset.name } }
    suspend fun setManualResize(enabled: Boolean) { store.edit { it[manualResizeKey] = enabled } }
    private val uiSettingsErrorState = MutableStateFlow<String?>(null)
    val uiSettingsError = uiSettingsErrorState.asStateFlow()
    val uiScale = store.data.catch { error ->
        if (error is java.io.IOException) {
            uiSettingsErrorState.value = "Could not load display settings."
            emit(emptyPreferences())
        } else throw error
    }.map { preferences ->
        preferences[uiScaleKey]?.takeIf { it.isFinite() && it > 0f } ?: 1f
    }
    suspend fun setUiScale(scale: Float) {
        require(scale.isFinite() && scale > 0f) { "UI scale must be a finite positive number." }
        try {
            store.edit { it[uiScaleKey] = scale }
            uiSettingsErrorState.value = null
        } catch (e: CancellationException) { throw e
        } catch (_: java.io.IOException) {
            uiSettingsErrorState.value = "Could not save display settings."
        }
    }
    private val lock = Mutex()
    private val epgLock = Mutex()
    private val epgParser = EpgParser()
    private val _epgMap = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val epgMap = _epgMap.asStateFlow()
    private val epgErrorState = MutableStateFlow<String?>(null)
    val epgError = epgErrorState.asStateFlow()
    private val epgUrlState = MutableStateFlow("")
    val epgUrl = epgUrlState.asStateFlow()
    private val epgLoadingState = MutableStateFlow(false)
    val isEpgLoading = epgLoadingState.asStateFlow()
    private val epgUpdatedState = MutableStateFlow<Long?>(null)
    val epgLastUpdated = epgUpdatedState.asStateFlow()
    private val loadingState = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)
    private val playlistState = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = playlistState.asStateFlow()
    val allChannels = combine(dao.getAllChannels(), playlists) { rows, sources ->
        val active = sources.filter { it.isActive }.map { it.id }
        rows.filter { it.playlistId in active }.sortedBy { active.indexOf(it.playlistId) }.map { it.toDomainModel() }
    }
    private val playlistsKey = stringPreferencesKey("playlists_list_json")
    private val hiddenKey = stringSetPreferencesKey("hidden_categories")
    private val recentKey = stringPreferencesKey("recent_channel_ids")
    val recentChannelIds = store.data.map { prefs ->
        try {
            val array = JSONArray(prefs[recentKey] ?: "[]")
            (0 until array.length()).map { array.getString(it) }.distinct().take(20)
        } catch (_: Exception) { emptyList() }
    }
    suspend fun recordRecentChannel(id: String) {
        store.edit { prefs ->
            val old = try {
                val array = JSONArray(prefs[recentKey] ?: "[]")
                (0 until array.length()).map { array.getString(it) }
            } catch (_: Exception) { emptyList() }
            prefs[recentKey] = JSONArray(updatedRecentIds(old, id)).toString()
        }
    }
    private val hiddenCategories = store.data.map { it[hiddenKey].orEmpty() }
    val visibleChannels = combine(allChannels, hiddenCategories) { channels, hidden ->
        channels.filter { !it.isHidden && it.group !in hidden }
    }
    val channels = visibleChannels
    private val searchQueryState = MutableStateFlow("")
    val searchQuery = searchQueryState.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchedChannels = searchQuery.flatMapLatest { query ->
        combine(getFilteredChannels(null, query), playlists, hiddenCategories) { channels, sources, hidden ->
            val active = sources.filter { it.isActive }.map { it.id }.toSet()
            channels.filter { it.playlistId in active && it.group !in hidden }
        }
    }
    fun setSearchQuery(query: String) { searchQueryState.value = query }
    fun clearSearch() { searchQueryState.value = "" }
    val categories = combine(allChannels, hiddenCategories) { channels, hidden ->
        categorySummary(channels, hidden)
    }
    private var restored = false
    val isLoading = loadingState.asStateFlow()
    val error = errorState.asStateFlow()
    private val sessionKey = stringPreferencesKey("session")
    private val favoritesKey = stringSetPreferencesKey("favorites")
    private val epgUrlKey = stringPreferencesKey("epg_url")

    suspend fun restoreEpgSession() {
        try {
            val savedUrl = store.data.first()[epgUrlKey].orEmpty()
            epgUrlState.value = savedUrl
            if (savedUrl.isNotBlank()) loadEpg(savedUrl)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { epgErrorState.value = "Could not restore the saved guide." }
    }

    suspend fun clearEpg() = epgLock.withLock {
        try {
            database.epgDao().deleteAll()
            store.edit { it.remove(epgUrlKey) }
            epgUrlState.value = ""
            _epgMap.value = emptyMap()
            epgUpdatedState.value = null
            epgErrorState.value = null
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { epgErrorState.value = "Could not remove the saved guide." }
    }

    suspend fun importEpg(xmlUrl: String) = loadEpg(xmlUrl)

    /** Persists the guide and publishes its current/upcoming snapshot. */
    suspend fun loadEpg(epgUrl: String) = epgLock.withLock {
        epgErrorState.value = null
        epgLoadingState.value = true
        try {
            val normalizedUrl = epgUrl.trim().toHttpUrl().toString()
            val programs = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(normalizedUrl).build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "EPG request failed" }
                    val body = response.body ?: error("Empty EPG response")
                    importEpgStream(database, body.byteStream())
                }
            }
            store.edit { it[epgUrlKey] = normalizedUrl }
            epgUrlState.value = normalizedUrl
            _epgMap.value = programs
            epgUpdatedState.value = System.currentTimeMillis()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            epgErrorState.value = "Could not load the guide. Check the XMLTV URL and connection."
        } finally { epgLoadingState.value = false }
    }

    /** Pass the channel's tvgId, not its app-generated favorite ID. */
    fun getEpgForChannel(channelId: String?): EpgProgram? = channelId?.let { id ->
        _epgMap.value[id]?.takeIf { it.endTime > System.currentTimeMillis() }
    }

    suspend fun restoreLastSession() = lock.withLock {
        if (restored) return@withLock
        loadingState.value = true
        try {
            ensureRestored()
            playlistState.value.filter { it.isActive }.forEach { refreshInternal(it) }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { errorState.value = "Could not restore saved playlists." }
        finally { loadingState.value = false }
    }

    private suspend fun ensureRestored() {
        if (restored) return
        val prefs = store.data.first()
        val saved = prefs[playlistsKey]
        val list = if (saved != null) decodePlaylists(saved) else {
            prefs[sessionKey]?.let {
                val json = JSONObject(it)
                val type = PlaylistType.valueOf(json.getString("type"))
                val config = json.getJSONObject("config")
                val id = if (type == PlaylistType.M3U) stableId("M3U|${config.getString("url").trim().toHttpUrl()}")
                    else stableId("XTREAM|${config.getString("host").trim().trimEnd('/').toHttpUrl()}|${config.getString("user")}")
                listOf(Playlist(id = id, name = "Saved playlist", type = type, configJson = config.toString()))
            }.orEmpty()
        }
        if (saved == null) savePlaylists(list) else playlistState.value = list
        restored = true
    }

    private suspend fun savePlaylists(list: List<Playlist>) {
        store.edit { it[playlistsKey] = encodePlaylists(list); it.remove(sessionKey) }
        playlistState.value = list
    }

    suspend fun loadPlaylist(type: PlaylistType, config: Map<String, String>) =
        addPlaylist(config["name"].orEmpty(), type, config.filterKeys { it != "name" })

    suspend fun addPlaylist(name: String, type: PlaylistType, config: Map<String, String>) = mutate {
        val playlist = Playlist(name = name.trim().ifBlank { "${type.name} playlist" }, type = type,
            configJson = JSONObject(config).toString())
        savePlaylists(playlistState.value + playlist)
        refreshInternal(playlist)
    }

    suspend fun removePlaylist(playlistId: String) = mutate {
        savePlaylists(playlistState.value.filterNot { it.id == playlistId })
        dao.deletePlaylistChannels(playlistId)
    }

    suspend fun togglePlaylistActive(playlistId: String, active: Boolean) = mutate {
        val list = playlistState.value.map { if (it.id == playlistId) it.copy(isActive = active) else it }
        savePlaylists(list)
        if (active) list.find { it.id == playlistId }?.let { refreshInternal(it) }

    }

    suspend fun refreshPlaylistChannels(playlistId: String) = mutate {
        playlistState.value.find { it.id == playlistId && it.isActive }?.let { refreshInternal(it) }
    }

    suspend fun toggleCategoryVisibility(categoryName: String, hide: Boolean) = mutate {
        store.edit { prefs ->
            prefs[hiddenKey] = prefs[hiddenKey].orEmpty().toMutableSet().apply {
                if (hide) add(categoryName) else remove(categoryName)
            }
        }
    }

    private suspend fun mutate(block: suspend () -> Unit) = lock.withLock {
        loadingState.value = true
        errorState.value = null
        try { ensureRestored(); block() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errorState.value = "Could not update playlists. Check storage and connection." }
        finally { loadingState.value = false }
    }

    // Called under the repository mutex; a failed refresh retains this source's previous channels.
    private suspend fun refreshInternal(playlist: Playlist) {
        try { importInternal(playlist) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errorState.value = "Could not load ${playlist.name}. Check the URL, credentials and connection." }
    }

    suspend fun importPlaylist(playlist: Playlist) = lock.withLock {
        ensureRestored()
        importInternal(playlist)
    }

    private suspend fun importInternal(playlist: Playlist) {
            val json = JSONObject(playlist.configJson)
            val config = json.keys().asSequence().associateWith { json.getString(it) }
            val loaded = withContext(Dispatchers.IO) {
                when (playlist.type) {
                    PlaylistType.M3U -> {
                        val url = config["url"].orEmpty().trim().toHttpUrl()
                        parseM3u(fetch(url.toString()), url.toString(), playlist.id)
                    }
                    PlaylistType.XTREAM -> loadXtream(config, playlist.id)
                }
            }
            require(loaded.isNotEmpty()) { "No supported channels found" }
            var favorites = emptySet<String>()
            store.edit { prefs ->
                val migrated = migrateFavoriteIds(prefs[favoritesKey].orEmpty(), loaded)
                favorites = migrated
                prefs[favoritesKey] = migrated
            }
            dao.replacePlaylistChannels(playlist.id, loaded.map {
                ChannelEntity(it.id, it.playlistId, it.name, it.url, it.group, it.orderIndex,
                    it.id in favorites, it.logoUrl, it.epgId)
            })
    }

    suspend fun toggleFavorite(id: String) = lock.withLock {
        try {
            store.edit { prefs ->
                val updated = prefs[favoritesKey].orEmpty().toMutableSet()
                if (!updated.add(id)) updated.remove(id)
                prefs[favoritesKey] = updated
                dao.setFavorite(id, id in updated)
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { errorState.value = "Could not save favorites." }
    }

    private fun fetch(url: String): String = client.newCall(Request.Builder().url(url).build()).execute().use {
        check(it.isSuccessful) { "HTTP ${it.code}" }
        it.body?.string() ?: error("Empty response")
    }

    private fun loadXtream(config: Map<String, String>, playlistId: String): List<Channel> {
        val host = config["host"].orEmpty().trim().trimEnd('/').toHttpUrl()
        val user = config["user"].orEmpty()
        val pass = config["pass"].orEmpty()
        require(user.isNotBlank() && pass.isNotBlank())
        fun api(action: String? = null): String {
            val url = host.newBuilder().addPathSegment("player_api.php")
                .addQueryParameter("username", user).addQueryParameter("password", pass)
            if (action != null) url.addQueryParameter("action", action)
            return fetch(url.build().toString())
        }
        val auth = JSONObject(api()).optJSONObject("user_info") ?: error("Missing account")
        check(auth.optInt("auth") == 1) { "Invalid credentials" }
        val categories = JSONArray(api("get_live_categories"))
        val groups = (0 until categories.length()).associate { i ->
            categories.getJSONObject(i).let { it.getString("category_id") to it.getString("category_name") }
        }
        val streams = JSONArray(api("get_live_streams"))
        return (0 until streams.length()).map { i ->
            val item = streams.getJSONObject(i)
            val streamId = item.getString("stream_id")
            val url = host.newBuilder().addPathSegment("live").addPathSegment(user)
                .addPathSegment(pass).addPathSegment("$streamId.ts").build().toString()
            Channel("$playlistId:${stableId("$host|$user|$streamId")}", playlistId, item.optString("name", "Channel $streamId"), url,
                groups[item.optString("category_id")] ?: "Live TV",
                logoUrl = if (item.isNull("stream_icon")) null else item.optString("stream_icon").trim().takeIf { it.isNotEmpty() },
                epgId = if (item.isNull("epg_channel_id")) null else item.optString("epg_channel_id").trim().takeIf { it.isNotEmpty() },
                orderIndex = i)
        }
    }
}

internal fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

internal fun migrateFavoriteIds(saved: Set<String>, loaded: List<Channel>): Set<String> =
    saved.toMutableSet().apply {
        loaded.forEach { channel ->
            if (remove(channel.id.substringAfter(':'))) add(channel.id)
        }
    }

internal fun parseM3u(content: String, sourceUrl: String, playlistId: String = stableId("M3U|$sourceUrl")): List<Channel> {
    require(content.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith("#EXTM3U") || content.contains("#EXTINF:")) { "Not an M3U playlist" }
    val base = sourceUrl.toHttpUrl()
    val result = mutableListOf<Channel>()
    var name = ""
    var group = "Live TV"
    var tvgId: String? = null
    var logoUrl: String? = null
    for (raw in content.lineSequence()) {
        val line = raw.trim().removePrefix("\uFEFF")
        if (line.startsWith("#EXTINF:")) {
            // Find the display-name separator outside quoted attributes.
            var quoted = false
            val separator = line.indices.firstOrNull { index ->
                if (line[index] == '"') quoted = !quoted
                line[index] == ',' && !quoted
            }
            name = separator?.let { line.substring(it + 1).trim() }.orEmpty()
            logoUrl = Regex("(?:^|\\s)tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            tvgId = Regex("(?:^|\\s)tvg-id=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.ifBlank { "Live TV" } ?: "Live TV"
        } else if (line.startsWith("#EXTGRP:")) {
            group = line.substringAfter(':').trim()
        } else if (line.isNotBlank() && !line.startsWith('#')) {
            val resolved = base.resolve(line) ?: continue
            if (resolved.scheme !in listOf("http", "https")) continue
            val url = resolved.toString()
            result.add(Channel("$playlistId:${stableId(url)}", playlistId, name.ifBlank { "Channel ${result.size + 1}" }, url, group, logoUrl = logoUrl, epgId = tvgId, orderIndex = result.size))
            name = ""
            group = "Live TV"
            tvgId = null
            logoUrl = null
        }
    }
    return result.distinctBy { it.id }
}
