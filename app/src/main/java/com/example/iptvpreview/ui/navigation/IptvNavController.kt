package com.example.iptvpreview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FocusArea { NAV_RAIL, CATEGORY_LIST, CHANNEL_LIST, PLAYER_CONTROLS }

class IptvNavController {
    private val _focusArea = MutableStateFlow(FocusArea.CHANNEL_LIST)
    val focusArea: StateFlow<FocusArea> = _focusArea.asStateFlow()

    fun setFocus(area: FocusArea) { _focusArea.value = area }
}

@Composable
fun rememberIptvNavController(): IptvNavController = remember { IptvNavController() }
