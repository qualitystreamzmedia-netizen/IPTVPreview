package com.example.iptvpreview

import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.content.pm.PackageManager
import com.example.iptvpreview.ui.utils.enterPipMode
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import com.example.iptvpreview.ui.navigation.FocusArea
import com.example.iptvpreview.ui.navigation.IptvNavController
import com.example.iptvpreview.ui.navigation.rememberIptvNavController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import com.example.iptvpreview.ui.utils.remotePaneNavigation
import com.example.iptvpreview.ui.utils.adjacentFocusArea
import com.example.iptvpreview.ui.utils.rememberRemoteListState
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.iptvpreview.data.Channel
import com.example.iptvpreview.data.IptvRepository
import com.example.iptvpreview.data.PlaylistType
import com.example.iptvpreview.data.model.EpgProgram
import com.example.iptvpreview.ui.player.PlayerViewModel
import com.example.iptvpreview.ui.components.VlcPlayer
import com.example.iptvpreview.ui.components.VlcPlayerController
import com.example.iptvpreview.ui.components.PlayerControlsOverlay
import com.example.iptvpreview.ui.components.PlayerStatus
import com.example.iptvpreview.ui.components.PlaylistSelector
import com.example.iptvpreview.ui.components.PlaylistManagerDialog
import com.example.iptvpreview.ui.components.IPTVSearchBar
import com.example.iptvpreview.ui.components.CategorySidebar
import com.example.iptvpreview.ui.components.ChannelListPanel
import com.example.iptvpreview.ui.components.PlayerInfoPanel
import com.example.iptvpreview.ui.components.PinPromptDialog
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import com.example.iptvpreview.ui.components.ResizableDivider
import com.example.iptvpreview.ui.components.LeftNavRail
import com.example.iptvpreview.ui.components.NavItem
import com.example.iptvpreview.ui.utils.toggleImmersiveMode
import com.example.iptvpreview.ui.utils.componentActivity
import com.example.iptvpreview.ui.screens.IptvRoot
import com.example.iptvpreview.ui.theme.getScaledTypography
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val navController get() = playerViewModel.navController
    private val _keyEventFlow = MutableSharedFlow<KeyEvent>(extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val keyEventFlow = _keyEventFlow.asSharedFlow()
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerViewModel.setPipMode(isInPictureInPictureMode)
    }
    enum class RemoteAction { UP, DOWN, LEFT, RIGHT, OK, BACK, MENU, INFO, GUIDE, PLAY_PAUSE, PLAY, PAUSE, STOP, NEXT, PREVIOUS }
    private val _remoteAction = MutableSharedFlow<RemoteAction>(extraBufferCapacity = 64)
    val remoteAction = _remoteAction.asSharedFlow()

    private fun remoteActionFor(keyCode: Int): RemoteAction? = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RemoteAction.PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteAction.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteAction.PAUSE
            KeyEvent.KEYCODE_MEDIA_STOP -> RemoteAction.STOP
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> RemoteAction.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> RemoteAction.PREVIOUS
            KeyEvent.KEYCODE_MENU -> RemoteAction.MENU
            KeyEvent.KEYCODE_INFO -> RemoteAction.INFO
            KeyEvent.KEYCODE_GUIDE -> RemoteAction.GUIDE
            else -> null
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = remoteActionFor(keyCode)
        if (action != null) {
            // Ignore long-press repeats for toggles; separate presses remain separate events.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                _remoteAction.tryEmit(action)
            }
            return true
        }
        // Compose handles D-pad/Enter focus and dialog navigation. Back uses BackHandler.
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (remoteActionFor(keyCode) != null) true else super.onKeyUp(keyCode, event)
    private val playerViewModel: PlayerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(IptvRepository(applicationContext)) as T
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerViewModel.setPipMode(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode)
        setContent {
            val uiScale by playerViewModel.uiScale.collectAsState()
            val typography = remember(uiScale) { getScaledTypography(uiScale) }
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF00BCD4), secondary = Color(0xFF9C27B0),
                background = Color(0xFF121212), surface = Color(0xFF1E1E1E),
                surfaceVariant = Color(0xFF2C2C2C), onSurface = Color.White,
                onBackground = Color.White), typography = typography) {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    // Observe without consuming: focused controls retain native key handling.
                    _keyEventFlow.tryEmit(KeyEvent(event.nativeKeyEvent))
                    false
                }) {
                    IptvRoot(playerViewModel, remoteAction, navController)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvApp(viewModel: PlayerViewModel, remoteActions: Flow<MainActivity.RemoteAction> = emptyFlow(),
    onNavigateToDashboard: (() -> Unit)? = null, onParentalControls: () -> Unit = {}, onManageCategoryOrder: () -> Unit = {},
    navController: IptvNavController = viewModel.navController) {
    val channels by viewModel.channels.collectAsState()
    val displayedChannels by viewModel.displayedChannels.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recentIds by viewModel.recentChannelIds.collectAsState()
    var filterRecent by remember { mutableStateOf(false) }
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()

    val securityReady by viewModel.securityReady.collectAsState()
    val lockedCategories by viewModel.lockedCategories.collectAsState()
    val lockedChannels by viewModel.lockedChannels.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var pendingChannel by remember { mutableStateOf<Channel?>(null) }
    var authorizedChannelId by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val epgMap by viewModel.epgMap.collectAsState()
    val epgError by viewModel.epgError.collectAsState()
    val epgUrl by viewModel.epgUrl.collectAsState()
    val isEpgLoading by viewModel.isEpgLoading.collectAsState()
    val epgLastUpdated by viewModel.epgLastUpdated.collectAsState()
    val appLifecycle = LocalLifecycleOwner.current
    LaunchedEffect(epgUrl, appLifecycle) {
        if (epgUrl.isNotBlank()) appLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(300_000)
                viewModel.refreshEpg(epgUrl)
            }
        }
    }
    
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showManagerDialog by remember { mutableStateOf(false) }
    val filterByFavorites by viewModel.showFavoritesOnly.collectAsState()
    var playbackError by remember { mutableStateOf<String?>(null) }
    var vlcController by remember { mutableStateOf<VlcPlayerController?>(null) }
    var playbackAttempt by remember { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }
    val activity = LocalContext.current.componentActivity()
    val inPip by viewModel.pipMode.collectAsState()
    val expandedPlayer = fullscreen || inPip
    val pipSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    DisposableEffect(fullscreen, inPip, activity) {
        activity?.let { toggleImmersiveMode(fullscreen && !inPip, it) }
        onDispose { activity?.let { toggleImmersiveMode(false, it) } }
    }
    LaunchedEffect(selectedChannel) { if (selectedChannel == null) fullscreen = false }
    val currentStatus = vlcController?.status?.collectAsState()?.value ?: PlayerStatus.IDLE
    LaunchedEffect(channels) {
        if (selectedChannel != null && channels.none { it.id == selectedChannel?.id }) {
            selectedChannel = null
            vlcController = null
            playbackError = null
        }
    }
    val visibleChannels = remember(displayedChannels, filterByFavorites, selectedCategory, selectedPlaylistId, filterRecent, recentIds, searchQuery) {
        val recentOrder = recentIds.withIndex().associate { it.value to it.index }
        val filtered = displayedChannels.filter { !it.isHidden && (!filterByFavorites || it.isFavorite) &&
            (searchQuery.isNotBlank() || selectedCategory == null || it.group == selectedCategory) &&
            (!filterRecent || it.id in recentOrder) }
        if (filterRecent) filtered.sortedBy { recentOrder[it.id] } else filtered
    }
    val sidebarCategories = categories
    LaunchedEffect(sidebarCategories) {
        viewModel.setFocusedCategory(viewModel.focusedCategoryIndex, sidebarCategories.count { !it.isHidden })
    }
    LaunchedEffect(categories, searchQuery) {
        if (searchQuery.isBlank() && selectedCategory != null && categories.none { !it.isHidden && it.name == selectedCategory }) selectedCategory = null
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val pipScope = rememberCoroutineScope()
    val enterPip: (() -> Unit)? = if (pipSupported && currentStatus == PlayerStatus.PLAYING) ({
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.enterPipMode() != true) {
            pipScope.launch { snackbarHostState.showSnackbar("Picture-in-Picture is unavailable. Check Android app permissions.") }
        }
    }) else null
    LaunchedEffect(inPip) {
        if (inPip) {
            showSettingsDialog = false
            showManagerDialog = false
            pendingChannel = null

            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    var showProgramInfo by remember { mutableStateOf(false) }
    val channelListScroll = rememberLazyListState()
    val categoryListScroll = rememberLazyListState()
    val currentFocus by navController.focusArea.collectAsState()
    val channelListFocus = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val playerFocus = remember { FocusRequester() }
    val requestPaneFocus: (FocusArea) -> Unit = { area ->
        if (!expandedPlayer || area == FocusArea.PLAYER_CONTROLS) {
            navController.setFocus(area)
            when (area) {
                FocusArea.NAV_RAIL -> railFocus
                FocusArea.CATEGORY_LIST -> categoryFocus
                FocusArea.CHANNEL_LIST -> channelListFocus
                FocusArea.PLAYER_CONTROLS -> playerFocus
            }.requestFocus()
        }
    }
    var channelListFocused by remember { mutableStateOf(false) }
    LaunchedEffect(currentFocus) {
        if (!showSettingsDialog && !showManagerDialog && !showProgramInfo && pendingChannel == null) {
            requestPaneFocus(currentFocus)
        }
    }
    var remotePlayback by remember { mutableStateOf(false) }
    DisposableEffect(appLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pendingChannel = null
                authorizedChannelId = null
                if (selectedChannel?.let { viewModel.isChannelLocked(it) } == true) {
                    selectedChannel = null; vlcController = null
                }
            }
        }
        appLifecycle.lifecycle.addObserver(observer)
        onDispose { appLifecycle.lifecycle.removeObserver(observer) }
    }
    val playGranted: (Channel) -> Unit = { channel ->
        if (playbackError != null || currentStatus == PlayerStatus.ERROR) playbackAttempt++
        if (selectedChannel?.id != channel.id || playbackError != null || currentStatus == PlayerStatus.ERROR) vlcController = null
        selectedChannel = channel
        playbackError = null
    }
    val attemptPlayChannel: (Channel) -> Unit = { channel ->
        if (securityReady) {
            if (viewModel.isChannelLocked(channel)) pendingChannel = channel
            else { authorizedChannelId = null; playGranted(channel) }
        }
    }
    val requestedChannelId by viewModel.requestedChannelId.collectAsState()
    LaunchedEffect(currentStatus, selectedChannel?.id) {
        if (currentStatus == PlayerStatus.PLAYING) selectedChannel?.let(viewModel::markAsRecent)
    }
    LaunchedEffect(requestedChannelId, securityReady, channels, isLoading) {
        val requested = requestedChannelId
        if (requested != null && securityReady) {
            val channel = channels.find { it.id == requested }
            if (channel != null) {
                viewModel.consumeRequestedChannel()
                attemptPlayChannel(channel)
            } else if (!isLoading) viewModel.consumeRequestedChannel()
        }
    }
    LaunchedEffect(lockedCategories, lockedChannels, securityReady) {
        if (selectedChannel?.let { viewModel.isChannelLocked(it) && it.id != authorizedChannelId } == true) {
            selectedChannel = null; vlcController = null
        }
    }
    val activateChannel: (Int) -> Unit = { index ->
        visibleChannels.getOrNull(index)?.let { channel ->
            remotePlayback = true
            attemptPlayChannel(channel)
        }
    }
    val handleSelection by rememberUpdatedState<(PlayerViewModel.FocusSelection) -> Unit> { selection ->
        if (!showSettingsDialog && !showManagerDialog && !showProgramInfo && pendingChannel == null && !inPip) {
            when (selection.pane) {
                PlayerViewModel.Pane.CHANNEL -> activateChannel(selection.index)
                PlayerViewModel.Pane.CATEGORY -> {
                    val choices = sidebarCategories.filterNot { it.isHidden }
                    if (selection.index == 0) selectedCategory = null
                    else choices.getOrNull(selection.index - 1)?.let { selectedCategory = it.name }
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(viewModel, appLifecycle) {
        appLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.selections.collect { handleSelection(it) }
        }
    }
    val listNavigation = rememberRemoteListState(visibleChannels.size, remoteActions,
        enabled = channelListFocused && !showSettingsDialog && !showProgramInfo,
        onActivate = activateChannel, navigationState = viewModel.channelFocus)
    LaunchedEffect(searchQuery, selectedCategory, filterByFavorites, selectedPlaylistId, filterRecent) {
        listNavigation.select(0, visibleChannels.size)
        if (visibleChannels.isNotEmpty()) channelListScroll.scrollToItem(0)
    }
    val remoteHandler by rememberUpdatedState<(MainActivity.RemoteAction) -> Unit> { action ->
        if (pendingChannel == null && !inPip) {
        when (action) {
            MainActivity.RemoteAction.MENU -> if (!showManagerDialog) { showSettingsDialog = true }
            MainActivity.RemoteAction.INFO, MainActivity.RemoteAction.GUIDE -> showProgramInfo = true
            MainActivity.RemoteAction.PLAY -> vlcController?.play()
            MainActivity.RemoteAction.PAUSE -> vlcController?.pause()
            MainActivity.RemoteAction.PLAY_PAUSE -> {
                if (currentStatus == PlayerStatus.PLAYING || currentStatus == PlayerStatus.BUFFERING)
                    vlcController?.pause() else vlcController?.play()
            }
            MainActivity.RemoteAction.STOP -> {
                vlcController?.stop()
                selectedChannel = null
                vlcController = null
                playbackError = null
                if (!showSettingsDialog && !showProgramInfo) channelListFocus.requestFocus()
            }
            MainActivity.RemoteAction.LEFT, MainActivity.RemoteAction.RIGHT,
            MainActivity.RemoteAction.NEXT, MainActivity.RemoteAction.PREVIOUS -> {
                if (!showSettingsDialog && !showProgramInfo && !showManagerDialog) {
                    val index = visibleChannels.indexOfFirst { it.id == selectedChannel?.id }
                    val target = if (index < 0) 0 else index + if (action == MainActivity.RemoteAction.NEXT || action == MainActivity.RemoteAction.RIGHT) 1 else -1
                    if (target in visibleChannels.indices) {
                        listNavigation.select(target, visibleChannels.size)
                        activateChannel(target)
                    }
                }
            }
            else -> Unit
        }
        }
    }
    LaunchedEffect(remoteActions, appLifecycle) {
        appLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            remoteActions.collect { remoteHandler(it) }
        }
    }
    BackHandler(enabled = (isSearchActive || selectedChannel != null || onNavigateToDashboard != null) && pendingChannel == null && !showSettingsDialog && !showProgramInfo && !showManagerDialog) {
        if (isSearchActive) {
            isSearchActive = false
            viewModel.clearSearch()
            requestPaneFocus(FocusArea.CHANNEL_LIST)
            return@BackHandler
        }
        if (fullscreen) { fullscreen = false; return@BackHandler }
        if (navController.focusArea.value != FocusArea.NAV_RAIL) {
            requestPaneFocus(adjacentFocusArea(navController.focusArea.value, false))
            return@BackHandler
        }
        if (selectedChannel == null) {
            onNavigateToDashboard?.invoke()
            return@BackHandler
        }
        selectedChannel = null
        vlcController = null
        playbackError = null
        channelListFocus.requestFocus()
    }
    if (showProgramInfo && !inPip) {
        val program = selectedChannel?.tvgId?.let { epgMap[it] }
        AlertDialog(onDismissRequest = { showProgramInfo = false },
            title = { Text(selectedChannel?.name ?: "Programme guide") },
            text = { Text(program?.let { "${it.title}\n\n${it.description.orEmpty()}" }
                ?: "No programme information available. Add an XMLTV guide in Settings.") },
            confirmButton = { TextButton(onClick = { showProgramInfo = false }) { Text("Done") } })
    }
    var leftWeight by rememberSaveable { mutableFloatStateOf(0.4f) }
    var catWeight by rememberSaveable { mutableFloatStateOf(0.3f) }
    val density = LocalDensity.current
    Scaffold(
        snackbarHost = { if (!inPip) SnackbarHost(snackbarHostState) },
        topBar = {
            Column(if (expandedPlayer) Modifier.height(0.dp) else Modifier) {
            TopAppBar(
                navigationIcon = {
                    if (onNavigateToDashboard != null) IconButton(onClick = onNavigateToDashboard) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Home")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("IPTV Pro", fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(16.dp))
                        if (isSearchActive) {
                            val searchFocus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { searchFocus.requestFocus() }
                            IPTVSearchBar(initialQuery = searchQuery, onQueryChange = viewModel::updateSearch,
                                modifier = Modifier.weight(1f).focusRequester(searchFocus),
                                onSubmit = { channelListFocus.requestFocus() })
                        } else PlaylistSelector(
                            playlists = playlists,
                            selectedId = selectedPlaylistId,
                            onSelect = { viewModel.selectPlaylist(it); selectedCategory = null },
                            modifier = Modifier.weight(1f),
                            enabled = !expandedPlayer
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) viewModel.clearSearch()
                    }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            if (isSearchActive) "Close search" else "Search")
                    }
                    if (onNavigateToDashboard != null) TextButton(onClick = onNavigateToDashboard) { Text("Dashboard") }
                    IconButton(onClick = onParentalControls) { Icon(Icons.Default.Lock, "Parental Controls") }
                    TextButton(onClick = { showManagerDialog = true }) { Text("Manage") }
                    IconButton(onClick = { viewModel.toggleFavorites() }) {
                        Icon(
                            imageVector = if(filterByFavorites) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Filter Favorites",
                            tint = if(filterByFavorites) Color.Yellow else Color.White
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            if (searchQuery.isNotBlank()) Text("Results: ${visibleChannels.size}",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    ) { padding ->
        val contentPadding = if (expandedPlayer) PaddingValues(0.dp) else padding
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding).background(Color.Black)) {
        val availableWidth = (maxWidth.value - 8f).coerceAtLeast(0f)
        val availableWidthPx = with(density) { availableWidth.dp.toPx() }
        val browserWidth = availableWidth * leftWeight.coerceIn(0.2f, 0.8f)
        val railWidth = minOf(96f, (browserWidth - 8f).coerceAtLeast(0f))
        val innerBrowserWidth = (browserWidth - railWidth - 8f).coerceAtLeast(0f)
        val innerBrowserWidthPx = with(density) { innerBrowserWidth.dp.toPx() }
        val categoryWidth = innerBrowserWidth * catWeight.coerceIn(0.2f, 0.8f)
        val resizeCategories: (Float) -> Unit = { deltaPx ->
            if (innerBrowserWidthPx > 0f) {
                catWeight = (catWeight + deltaPx / innerBrowserWidthPx).coerceIn(0.2f, 0.8f)
            }
        }
        val resizeBrowser: (Float) -> Unit = { deltaPx ->
            if (availableWidthPx > 0f) {
                val fractionalDelta = deltaPx / availableWidthPx
                leftWeight = (leftWeight + fractionalDelta).coerceIn(0.2f, 0.8f)
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            LeftNavRail(selectedItem = when {
                showProgramInfo -> NavItem.GUIDE
                filterRecent -> NavItem.RECENT
                filterByFavorites -> NavItem.FAVORITES
                else -> NavItem.HOME
            }, onItemClick = { item ->
                selectedCategory = null
                when (item) {
                    NavItem.HOME -> { filterRecent = false; viewModel.setFavoritesOnly(false) }
                    NavItem.FAVORITES -> { viewModel.setFavoritesOnly(true); filterRecent = false }
                    NavItem.RECENT -> { filterRecent = true; viewModel.setFavoritesOnly(false) }
                    NavItem.GUIDE -> showProgramInfo = true
                }
            }, isFocused = currentFocus == FocusArea.NAV_RAIL,
                modifier = Modifier.width(if (expandedPlayer) 0.dp else railWidth.dp)
                .remotePaneNavigation(FocusArea.NAV_RAIL, requestPaneFocus).focusRequester(railFocus)
                .onFocusChanged { if (it.hasFocus) navController.setFocus(FocusArea.NAV_RAIL) }.focusGroup())
            CategorySidebar(sidebarCategories, selectedCategory, { selectedCategory = it },
                Modifier.width(if (expandedPlayer) 0.dp else categoryWidth.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
                    .remotePaneNavigation(FocusArea.CATEGORY_LIST, requestPaneFocus).focusRequester(categoryFocus)
                    .onFocusChanged { if (it.hasFocus) navController.setFocus(FocusArea.CATEGORY_LIST) }.focusGroup(),
                selectedPlaylistName = playlists.find { it.id == selectedPlaylistId }?.name,
                onEditOrder = onManageCategoryOrder, listState = categoryListScroll,
                isFocused = currentFocus == FocusArea.CATEGORY_LIST,
                onFocusIndexChanged = { viewModel.setFocusedCategory(it, sidebarCategories.count { cat -> !cat.isHidden }) },
                focusedIndex = viewModel.focusedCategoryIndex,
                onRemoteMove = { down ->
                    val maxIndex = sidebarCategories.count { !it.isHidden } // Includes All Channels at index zero.
                    if (down) viewModel.moveFocusDown(maxIndex) else viewModel.moveFocusUp(maxIndex)
                }, onRemoteSelect = viewModel::selectItem)
            ResizableDivider(enabled = !expandedPlayer, onResize = resizeCategories,
                onStep = { deltaDp -> resizeCategories(with(density) { deltaDp.dp.toPx() }) },
                label = "Resize categories and channels", resizedPanel = "categories")
            
            // LEFT: CHANNEL LIST
            Column(modifier = Modifier.width(if (expandedPlayer) 0.dp else (innerBrowserWidth - categoryWidth).dp).fillMaxHeight().background(Color.DarkGray.copy(alpha=0.8f))) {
                if (isLoading || isEpgLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                
                ChannelListPanel(channels = visibleChannels, selectedChannel = selectedChannel,
                    isFocused = currentFocus == FocusArea.CHANNEL_LIST && !expandedPlayer && !showSettingsDialog && !showProgramInfo && !showManagerDialog && pendingChannel == null,
                    focusRequester = channelListFocus,
                    onSelectChannel = { channel ->
                        remotePlayback = false
                        listNavigation.select(visibleChannels.indexOfFirst { it.id == channel.id }, visibleChannels.size)
                        attemptPlayChannel(channel)
                    }, listState = channelListScroll, epg = epgMap,
                    focusedIndex = viewModel.focusedChannelIndex,
                    onToggleFav = viewModel::toggleFavorite, locked = viewModel::isChannelLocked,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                    .border(1.dp, if (currentFocus == FocusArea.CHANNEL_LIST) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent)
                    .remotePaneNavigation(FocusArea.CHANNEL_LIST, requestPaneFocus)
                    .onFocusChanged {
                        channelListFocused = it.isFocused
                        if (it.hasFocus) navController.setFocus(FocusArea.CHANNEL_LIST)
                    }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val action = when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> MainActivity.RemoteAction.UP
                            KeyEvent.KEYCODE_DPAD_DOWN -> MainActivity.RemoteAction.DOWN
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> MainActivity.RemoteAction.OK
                            else -> null
                        }
                        if (!channelListFocused || action == null || visibleChannels.isEmpty()) false
                        else if (action == MainActivity.RemoteAction.UP && listNavigation.selectedIndex == 0) false
                        else {
                            if (native.action == KeyEvent.ACTION_DOWN && (action in listOf(MainActivity.RemoteAction.UP, MainActivity.RemoteAction.DOWN) || native.repeatCount == 0)) {
                                when (action) {
                                    MainActivity.RemoteAction.UP -> viewModel.moveFocusUp(visibleChannels.lastIndex)
                                    MainActivity.RemoteAction.DOWN -> viewModel.moveFocusDown(visibleChannels.lastIndex)
                                    MainActivity.RemoteAction.OK -> viewModel.selectItem()
                                    else -> Unit
                                }
                            }
                            true
                        }
                    })
            }

            // RIGHT: PLAYER
            ResizableDivider(enabled = !expandedPlayer,
                onResize = resizeBrowser,
                onStep = { deltaDp -> resizeBrowser(with(density) { deltaDp.dp.toPx() }) })
            PlayerInfoPanel(channel = selectedChannel, controller = vlcController,
                fullscreen = expandedPlayer, pipMode = inPip, onToggleFullscreen = { fullscreen = !fullscreen },
                onEnterPip = enterPip,
                modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
                    .onKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        if (!expandedPlayer && key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) requestPaneFocus(FocusArea.CHANNEL_LIST)
                            true
                        } else false
                    }.focusRequester(playerFocus)
                    .onFocusChanged { if (it.hasFocus) navController.setFocus(FocusArea.PLAYER_CONTROLS) }.focusGroup(),
                program = selectedChannel?.epgId?.let { epgMap[it] },
                isFavorite = channels.find { it.id == selectedChannel?.id }?.isFavorite == true,
                onFavorite = { selectedChannel?.let { viewModel.toggleFavorite(it.id) } },
                onPlay = { if (currentStatus == PlayerStatus.PLAYING) vlcController?.pause() else vlcController?.play() },
                onStop = { vlcController?.stop(); selectedChannel = null; vlcController = null }) {
                val channel = selectedChannel
                if (channel != null) {
                    val hasError = playbackError != null || currentStatus == PlayerStatus.ERROR
                    val showControls = !inPip && !hasError && currentStatus in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED)
                    val closePlayer = {
                        vlcController = null
                        playbackError = null
                        selectedChannel = null
                    }
                    // Retry changes the key, disposing the old native player and
                    // opening exactly the same URL with fresh native resources.
                    key(channel.id, playbackAttempt) {
                        VlcPlayer(
                            streamUrl = channel.url,
                            onError = { playbackError = it },
                            onControllerReady = { vlcController = it },
                            showBuiltInControls = false
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !inPip && !hasError && (vlcController == null || currentStatus == PlayerStatus.BUFFERING),
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("Connecting…", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    if (hasError && !inPip) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text("Stream Error", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(playbackError ?: "Stream unavailable", color = Color.LightGray)
                            Button(onClick = {
                                playbackError = null
                                vlcController = null
                                playbackAttempt++
                            }) { Text("Retry") }
                        }
                    } else if (!inPip && currentStatus == PlayerStatus.IDLE && vlcController != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Stopped", color = Color.White)
                            Button(onClick = { vlcController?.play() }) { Text("Play") }
                        }
                    }
                    if (showControls) {
                        val index = visibleChannels.indexOfFirst { it.id == channel.id }
                        val nextChannel = if (index >= 0) visibleChannels.getOrNull(index + 1) else null
                        key(channel.id, playbackAttempt) {
                            PlayerControlsOverlay(
                                controller = vlcController,
                                isFullScreen = fullscreen,
                                onEnterPip = enterPip,
                                onToggleFullScreen = { fullscreen = !fullscreen },
                                isVisibleInitially = !remotePlayback,
                                title = channel.name,
                                subtitle = channel.group,
                                onChannelSwitchRequested = nextChannel?.let { next ->
                                    {
                                        attemptPlayChannel(next)
                                    }
                                },
                                onClose = closePlayer
                            )
                        }
                    } else if (!inPip) {
                        Text(channel.name, color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp, end = 64.dp))
                        IconButton(onClick = closePlayer, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White)
                        }
                    }
                } else {
                    if (!inPip) Text(if (visibleChannels.isEmpty()) "No channels available" else "Press OK to Play", color = Color.Gray)
                }
                if (!securityReady) Text("Loading parental settings…", Modifier.padding(8.dp))
            }
        }
    }
    }
    if (showManagerDialog && !inPip) PlaylistManagerDialog(viewModel,
        onManageOrder = { showManagerDialog = false; onManageCategoryOrder() }, onDismiss = { showManagerDialog = false })
    pendingChannel?.takeIf { !inPip }?.let { pending ->
        PinPromptDialog(onDismiss = { pendingChannel = null }, onSuccess = {
            if (channels.any { it.id == pending.id }) {
                authorizedChannelId = pending.id
                playGranted(pending)
            }
            pendingChannel = null
        }, verifyLogic = viewModel::verifyPin)
    }
    if (showSettingsDialog && !inPip) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            savedEpgUrl = epgUrl,
            epgLoading = isEpgLoading,
            epgError = epgError,
            epgProgramCount = epgMap.size,
            epgLoaded = epgLastUpdated != null,
            onEpgSave = { viewModel.refreshEpg(it) },
            onEpgClear = { viewModel.clearEpg() },
            onSave = { type, config ->
                selectedChannel = null
                vlcController = null
                playbackError = null
                viewModel.loadNewPlaylist(type, config)
                showSettingsDialog = false
            }
        )
    }
    
    // Show Error Snackbar if needed
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(epgError) {
        epgError?.let { snackbarHostState.showSnackbar(it) }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    epgInfo: EpgProgram?,
    isFocused: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), epgInfo, lifecycleOwner) {
        if (epgInfo != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = System.currentTimeMillis()
                    delay(1_000)
                }
            }
        }
    }
    val program = epgInfo?.takeIf { it.endTime > now && it.endTime > it.startTime }
    Surface(
        modifier = Modifier.fillMaxWidth().height(80.dp)
            .clickable(onClick = onClick),
        color = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        border = if (isFocused) BorderStroke(2.dp, Color.Cyan) else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            // Favorite Toggle
            IconButton(onClick = onToggleFav, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (channel.isFavorite) "Remove favorite ${channel.name}" else "Favorite ${channel.name}",
                    tint = if (channel.isFavorite) Color.Yellow else Color.Gray
                )
            }
            
            Spacer(Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.name, color = Color.White, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (program != null) {
                    val live = now >= program.startTime
                    Text(
                        text = "${if (live) "LIVE" else "NEXT"} • ${program.title}",
                        color = if (live) Color.Green else Color.LightGray,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (live) {
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { calculateProgress(program, now) },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = Color.Green,
                            trackColor = Color.DarkGray
                        )
                    }
                } else {
                    Text(text = channel.group, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun calculateProgress(program: EpgProgram, now: Long): Float {
    val duration = program.endTime.toDouble() - program.startTime.toDouble()
    if (duration <= 0) return 0f
    return ((now.toDouble() - program.startTime.toDouble()) / duration).coerceIn(0.0, 1.0).toFloat()
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onSave: (PlaylistType, Map<String, String>) -> Unit,
    savedEpgUrl: String,
    epgLoading: Boolean,
    epgError: String?,
    epgProgramCount: Int,
    epgLoaded: Boolean,
    onEpgSave: (String) -> Unit,
    onEpgClear: () -> Unit,
    viewModel: PlayerViewModel
) {
    val savedScale by viewModel.uiScale.collectAsState()
    val scaleError by viewModel.uiSettingsError.collectAsState()
    var tempScale by remember(savedScale) { mutableFloatStateOf(savedScale.coerceIn(0.7f, 1.3f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { /* Handled inside content */ },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Settings") },
        text = {
            var tab by remember { mutableStateOf(0) } // 0: M3U, 1: Xtream
            var m3uUrl by remember { mutableStateOf("") }
            var playlistName by remember { mutableStateOf("") }
            var xHost by remember { mutableStateOf("") }
            var xUser by remember { mutableStateOf("") }
            var xPass by remember { mutableStateOf("") }
            var guideUrl by remember(savedEpgUrl) { mutableStateOf(savedEpgUrl) }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("M3U URL") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Xtream Codes") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Guide") })
                    Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Appearance") })
                }
                
                Spacer(Modifier.height(16.dp))
                
                if (tab == 0) {
                    OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Playlist name") }, singleLine = true)
                    OutlinedTextField(value = m3uUrl, onValueChange = { m3uUrl = it }, label = { Text("M3U Link") }, singleLine = true)
                } else if (tab == 1) {
                    OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Playlist name") }, singleLine = true)
                    OutlinedTextField(value = xHost, onValueChange = { xHost = it }, label = { Text("Host (http://...)") }, singleLine = true)
                    OutlinedTextField(value = xUser, onValueChange = { xUser = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = xPass, onValueChange = { xPass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } else if (tab == 3) {
                    Text("Interface Scale", style = MaterialTheme.typography.titleMedium)
                    Text("Adjust text size to fit more channels or improve readability.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Compact", style = MaterialTheme.typography.labelSmall)
                        Slider(value = tempScale, onValueChange = { tempScale = it },
                            valueRange = 0.7f..1.3f, steps = 5,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        Text("Large", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Selected: ${Math.round(tempScale * 100)}% • Applied: ${Math.round(savedScale * 100)}%",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    scaleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } else {
                    OutlinedTextField(value = guideUrl, onValueChange = { guideUrl = it },
                        label = { Text("XMLTV URL (.xml or .xml.gz)") }, singleLine = true,
                        enabled = !epgLoading)
                    Text("Use a guide whose channel IDs match your playlist. Refreshes every 5 minutes while the app is open.",
                        style = MaterialTheme.typography.bodySmall)
                    if (epgLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (epgError != null) Text(epgError, color = MaterialTheme.colorScheme.error)
                    if (epgLoaded) Text("Guide loaded: $epgProgramCount channels with current or upcoming programs.",
                        style = MaterialTheme.typography.bodySmall)
                    if (savedEpgUrl.isNotBlank()) TextButton(enabled = !epgLoading, onClick = {
                        guideUrl = ""
                        onEpgClear()
                    }) { Text("Remove guide") }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (tab == 3) {
                            viewModel.updateUiScale(tempScale)
                            return@Button
                        }
                        if (tab == 2) {
                            onEpgSave(guideUrl.trim())
                            return@Button
                        }
                        val config = if(tab == 0) mapOf("url" to m3uUrl) 
                                     else mapOf("host" to xHost, "user" to xUser, "pass" to xPass)
                        
                        val type = if(tab == 0) PlaylistType.M3U else PlaylistType.XTREAM
                        
                        if ((tab == 0 && m3uUrl.isNotBlank()) || (tab == 1 && xHost.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank())) {
                            onSave(type, config + ("name" to playlistName))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = when (tab) {
                        0 -> m3uUrl.isNotBlank()
                        1 -> xHost.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank()
                        3 -> true
                        else -> guideUrl.isNotBlank() && !epgLoading
                    }
                ) {
                    Text(when (tab) { 3 -> "Apply Scale"; 2 -> "Load / Refresh Guide"; else -> "Load Playlist" })
                }
            }
        }
    )
}

// Reuse the VideoPlayerComponent from previous step, ensure imports are correct
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerComponent(url: String) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                controllerShowTimeoutMs = 10000
                setErrorMessageProvider { error -> android.util.Pair.create(error.errorCode, "Unable to play this channel. Check your connection or select another channel.") }
            }
        },
        update = { it.player = exoPlayer },
        modifier = Modifier.fillMaxSize()
    )
}
