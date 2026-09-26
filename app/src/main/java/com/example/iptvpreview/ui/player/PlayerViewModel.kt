package com.example.iptvpreview.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptvpreview.data.IptvRepository
import com.example.iptvpreview.data.PlaylistType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(private val repo: IptvRepository) : ViewModel() {
    val channelFocus = com.example.iptvpreview.ui.utils.ListNavigationState()
    private val categoryFocus = com.example.iptvpreview.ui.utils.ListNavigationState()
    val focusedChannelIndex: Int get() = channelFocus.selectedIndex
    // Zero represents "All Channels"; category rows start at one.
    val focusedCategoryIndex: Int get() = categoryFocus.selectedIndex
    fun moveFocusDown(maxIndex: Int) = channelFocus.moveBy(1, maxIndex)
    fun moveFocusUp(maxIndex: Int) = channelFocus.moveBy(-1, maxIndex)
    fun setFocusedCategory(index: Int, categoryCount: Int) = categoryFocus.select(index, categoryCount + 1)

    val uiScale = repo.uiScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
    val uiSettingsError = repo.uiSettingsError
    fun updateUiScale(scale: Float) = setUiScale(scale)
    fun setUiScale(scale: Float) {
        require(scale.isFinite() && scale > 0f) { "UI scale must be a finite positive number." }
        viewModelScope.launch { repo.setUiScale(scale) }
    }
    private val pipModeState = MutableStateFlow(false)
    val pipMode = pipModeState.asStateFlow()
    val isPipMode = pipMode
    fun setPipMode(enabled: Boolean) { pipModeState.value = enabled }
    val securityReady = repo.security.ready
    val isPinSet = repo.security.isPinSet
    val lockedCategories = repo.security.lockedCategories
    val lockedChannels = repo.security.lockedChannels
    fun isChannelLocked(channel: com.example.iptvpreview.data.Channel) = repo.security.isChannelLocked(channel)
    suspend fun verifyPin(pin: String) = repo.security.verifyPin(pin)
    suspend fun setParentalPin(pin: String, currentPin: String? = null) = repo.security.setPin(pin, currentPin)
    suspend fun toggleCategoryLock(name: String, lock: Boolean, pin: String) = repo.security.setCategoryLock(name, lock, pin)
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allCategories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun addPlaylist(name: String, type: PlaylistType, config: Map<String, String>) {
        viewModelScope.launch { repo.addPlaylist(name, type, config) }
    }
    fun deletePlaylist(id: String) { viewModelScope.launch { repo.removePlaylist(id) } }
    fun togglePlaylist(id: String, active: Boolean) { viewModelScope.launch { repo.togglePlaylistActive(id, active) } }
    fun removePlaylist(id: String) = deletePlaylist(id)
    fun togglePlaylistActive(id: String, active: Boolean) = togglePlaylist(id, active)
    fun refreshPlaylist(id: String) { viewModelScope.launch { repo.refreshPlaylistChannels(id) } }
    fun toggleCategoryVisibility(name: String, hide: Boolean) { viewModelScope.launch { repo.toggleCategoryVisibility(name, hide) } }
    val visibleChannels = repo.visibleChannels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val channels = visibleChannels
    val recentChannelIds = repo.recentChannelIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentChannels = combine(visibleChannels, recentChannelIds) { channels, ids ->
        val byId = channels.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteChannels = visibleChannels.map { list -> list.filter { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val requestedChannelState = MutableStateFlow<String?>(null)
    val requestedChannelId = requestedChannelState.asStateFlow()
    fun selectChannel(channel: com.example.iptvpreview.data.Channel) {
        selectPlaylist(channel.playlistId)
        clearSearch()
        requestedChannelState.value = channel.id
    }
    fun consumeRequestedChannel() { requestedChannelState.value = null }
    fun markAsRecent(channel: com.example.iptvpreview.data.Channel) = recordRecentChannel(channel.id)
    fun recordRecentChannel(id: String) {
        viewModelScope.launch {
            try { repo.recordRecentChannel(id) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* A history write failure must not interrupt playback. */ }
        }
    }
    val searchQuery = repo.searchQuery.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId = _selectedPlaylistId.asStateFlow()
    fun selectPlaylist(id: String?) { _selectedPlaylistId.value = id }
    val customCategoryOrders = repo.customCategoryOrders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val editableCategories = combine(repo.allChannels, selectedPlaylistId, customCategoryOrders, allCategories) { all, id, orders, categories ->
        if (id == null) emptyList() else {
            val source = all.filter { it.playlistId == id }
            val hidden = categories.filter { it.isHidden }.map { it.name }.toSet()
            com.example.iptvpreview.data.applyCustomCategoryOrder(
                com.example.iptvpreview.data.categorySummary(source, emptySet()).map { it.copy(isHidden = it.name in hidden) }, orders[id])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    suspend fun persistCustomOrder(id: String, order: List<String>) = repo.saveCustomCategoryOrder(id, order)
    suspend fun restoreNaturalOrder(id: String) = repo.resetCustomCategoryOrder(id)
    private val categoryOrderErrorState = MutableStateFlow<String?>(null)
    val categoryOrderError = categoryOrderErrorState.asStateFlow()
    fun saveCustomOrder(playlistId: String, orderedNames: List<String>) =
        saveCustomCategoryOrder(playlistId, orderedNames)
    fun resetCustomOrder(playlistId: String) = resetCustomCategoryOrder(playlistId)
    fun saveCustomCategoryOrder(playlistId: String, order: List<String>) {
        viewModelScope.launch {
            try { repo.saveCustomCategoryOrder(playlistId, order); categoryOrderErrorState.value = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { categoryOrderErrorState.value = "Could not save category order." }
        }
    }
    fun resetCustomCategoryOrder(playlistId: String) {
        viewModelScope.launch {
            try { repo.resetCustomCategoryOrder(playlistId); categoryOrderErrorState.value = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { categoryOrderErrorState.value = "Could not reset category order." }
        }
    }
    val displayedChannels = combine(repo.searchedChannels, repo.allChannels, selectedPlaylistId, customCategoryOrders) { channels, allChannels, selectedId, orders ->
        val source = allChannels.filter { selectedId == null || it.playlistId == selectedId }
        val filtered = channels.filter { selectedId == null || it.playlistId == selectedId }
        com.example.iptvpreview.data.sortChannelsBySourceOrder(filtered, source, orders[selectedId])
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = combine(displayedChannels, repo.allChannels, selectedPlaylistId, customCategoryOrders) { channels, allChannels, selectedId, orders ->
        val source = allChannels.filter { selectedId == null || it.playlistId == selectedId }
        val counts = channels.groupingBy { it.group }.eachCount()
        com.example.iptvpreview.data.applyCustomCategoryOrder(
            com.example.iptvpreview.data.categorySummary(source, emptySet()), orders[selectedId]
        ).mapNotNull { category ->
            counts[category.name]?.let { category.copy(count = it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchedChannels = displayedChannels
    fun updateSearch(query: String) = repo.setSearchQuery(query)
    fun setSearchQuery(query: String) = updateSearch(query)
    fun clearSearch() = repo.clearSearch()
    val isLoading = repo.isLoading.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val error = repo.error.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val epgMap = repo.epgMap
    val epgError = repo.epgError
    val epgUrl = repo.epgUrl
    val isEpgLoading = repo.isEpgLoading
    val epgLastUpdated = repo.epgLastUpdated
    fun getEpgForChannel(channelId: String?) = repo.getEpgForChannel(channelId)

    fun clearEpg() {
        viewModelScope.launch { repo.clearEpg() }
    }

    fun loadEpg(url: String) {
        refreshEpg(url)
    }

    fun refreshEpg(url: String) {
        if (!repo.isEpgLoading.value) viewModelScope.launch { repo.loadEpg(url) }
    }

    init {
        viewModelScope.launch {
            repo.playlists.collect { sources ->
                val selected = _selectedPlaylistId.value
                if (selected != null && sources.none { it.id == selected && it.isActive }) selectPlaylist(null)
            }
        }
        viewModelScope.launch {
            try { repo.security.restore() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Playback stays locked when security cannot be loaded. */ }
        }
        viewModelScope.launch { repo.restoreLastSession() }
        viewModelScope.launch { repo.restoreEpgSession() }
    }
    fun loadNewPlaylist(type: PlaylistType, config: Map<String, String>) {
        addPlaylist(config["name"].orEmpty(), type, config.filterKeys { it != "name" })
    }
    fun toggleFavorite(id: String) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }
}
