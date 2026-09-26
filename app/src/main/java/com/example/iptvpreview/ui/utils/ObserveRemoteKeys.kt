package com.example.iptvpreview.ui.utils

import android.view.KeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.iptvpreview.ui.navigation.FocusArea
import com.example.iptvpreview.ui.navigation.IptvNavController
import kotlinx.coroutines.flow.Flow

internal fun adjacentFocusArea(area: FocusArea, moveRight: Boolean): FocusArea {
    val areas = FocusArea.values()
    return areas[(area.ordinal + if (moveRight) 1 else -1).coerceIn(0, areas.lastIndex)]
}

internal fun remoteScrollTarget(current: Int, delta: Int, count: Int): Int? =
    if (count <= 0) null else (current.coerceIn(0, count - 1) + delta).coerceIn(0, count - 1)

// Use on a pane, not the whole window: dialogs, search and dividers retain their keys.
fun Modifier.remotePaneNavigation(area: FocusArea, onMove: (FocusArea) -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (key.keyCode != KeyEvent.KEYCODE_DPAD_LEFT && key.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) false
        else {
            if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                onMove(adjacentFocusArea(area, key.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            true
        }
    }

/** For exclusive event streams; do not also dispatch these events through native controls. */
@Composable
fun ObserveRemoteKeys(
    keyEvents: Flow<KeyEvent?>,
    navController: IptvNavController,
    onChannelSelected: () -> Unit,
    onCategorySelected: () -> Unit,
    onNavItemSelected: () -> Unit,
    onFocusAreaRequested: (FocusArea) -> Unit = navController::setFocus,
    onPlayerSelected: () -> Unit = {},
    onBackFromRail: () -> Unit = {},
    enabled: Boolean = true,
    catListState: LazyListState? = null,
    chanListState: LazyListState? = null,
    channelCount: Int = 0,
    categoryCount: Int = 0
) {
    val request by rememberUpdatedState(onFocusAreaRequested)
    val channel by rememberUpdatedState(onChannelSelected)
    val category by rememberUpdatedState(onCategorySelected)
    val rail by rememberUpdatedState(onNavItemSelected)
    val player by rememberUpdatedState(onPlayerSelected)
    val back by rememberUpdatedState(onBackFromRail)
    val latestChannelCount by rememberUpdatedState(channelCount)
    val latestCategoryCount by rememberUpdatedState(categoryCount)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(keyEvents, navController, owner, enabled, catListState, chanListState) {
        if (!enabled) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            keyEvents.collect { event ->
                if (event == null || event.action != KeyEvent.ACTION_DOWN) return@collect
                val vertical = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                if (event.repeatCount != 0 && !vertical) return@collect
                val area = navController.focusArea.value
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val list = when (area) {
                            FocusArea.CATEGORY_LIST -> catListState
                            FocusArea.CHANNEL_LIST -> chanListState
                            else -> null
                        }
                        if (list != null) {
                            val count = if (area == FocusArea.CATEGORY_LIST) latestCategoryCount else latestChannelCount
                            val target = remoteScrollTarget(list.firstVisibleItemIndex,
                                if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1,
                                minOf(count, list.layoutInfo.totalItemsCount))
                            if (target != null) list.scrollToItem(target)
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> request(adjacentFocusArea(area, false))
                    KeyEvent.KEYCODE_DPAD_RIGHT -> request(adjacentFocusArea(area, true))
                    KeyEvent.KEYCODE_BACK -> if (area == FocusArea.NAV_RAIL) back() else request(adjacentFocusArea(area, false))
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when (area) {
                        FocusArea.NAV_RAIL -> rail()
                        FocusArea.CATEGORY_LIST -> category()
                        FocusArea.CHANNEL_LIST -> channel()
                        FocusArea.PLAYER_CONTROLS -> player()
                    }
                }
            }
        }
    }
}
