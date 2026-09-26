package com.example.iptvpreview.ui.utils

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.iptvpreview.MainActivity.RemoteAction
import kotlinx.coroutines.flow.Flow

@Stable
class ListNavigationState(initialIndex: Int = 0) {
    var selectedIndex by mutableIntStateOf(initialIndex.coerceAtLeast(0))
        private set

    fun select(index: Int, itemCount: Int) {
        selectedIndex = index.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    }

    fun handle(action: RemoteAction, itemCount: Int, onActivate: (Int) -> Unit): Boolean {
        select(selectedIndex, itemCount)
        if (itemCount <= 0) return false
        when (action) {
            RemoteAction.UP -> select(selectedIndex - 1, itemCount)
            RemoteAction.DOWN -> select(selectedIndex + 1, itemCount)
            RemoteAction.OK -> onActivate(selectedIndex)
            else -> return false
        }
        return true
    }
}

/** Event flow: each press is delivered once, including consecutive identical presses. */
@Composable
fun rememberRemoteListState(
    itemCount: Int,
    remoteActions: Flow<RemoteAction>,
    initialIndex: Int = 0,
    enabled: Boolean = true,
    onActivate: (Int) -> Unit = {}
): ListNavigationState {
    val state = remember { ListNavigationState(initialIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))) }
    val count by rememberUpdatedState(itemCount)
    val active by rememberUpdatedState(enabled)
    val activate by rememberUpdatedState(onActivate)
    val owner = LocalLifecycleOwner.current
    SideEffect { state.select(state.selectedIndex, itemCount) }
    LaunchedEffect(remoteActions, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            remoteActions.collect { if (active) state.handle(it, count, activate) }
        }
    }
    return state
}
