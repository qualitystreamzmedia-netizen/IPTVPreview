package com.example.iptvpreview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FocusArea { NAV_RAIL, CATEGORY_LIST, CHANNEL_LIST, PLAYER_CONTROLS }

class IptvNavController {
    var currentArea by mutableStateOf(FocusArea.CHANNEL_LIST)
        private set
    private val _focusArea = MutableStateFlow(FocusArea.CHANNEL_LIST)
    val focusArea: StateFlow<FocusArea> = _focusArea.asStateFlow()

    fun setFocus(area: FocusArea) { currentArea = area; _focusArea.value = area }
    fun switchPane(direction: Int) {
        val step = direction.compareTo(0)
        setFocus(FocusArea.entries[(currentArea.ordinal + step).coerceIn(0, FocusArea.entries.lastIndex)])
    }
}

@Composable
fun rememberIptvNavController(): IptvNavController = remember { IptvNavController() }
