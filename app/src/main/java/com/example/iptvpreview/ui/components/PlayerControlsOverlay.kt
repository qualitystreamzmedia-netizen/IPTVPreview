package com.example.iptvpreview.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val idlePlayerStatus = MutableStateFlow(PlayerStatus.IDLE)

@Composable
fun PlayerControlsOverlay(
    modifier: Modifier = Modifier,
    controller: VlcPlayerController?,
    isVisibleInitially: Boolean = true,
    onChannelSwitchRequested: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    title: String = "Live Stream",
    subtitle: String = "",
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null
) {
    var showControls by remember(controller) { mutableStateOf(isVisibleInitially) }
    var showTrackDialog by remember(controller) { mutableStateOf(false) }
    val status by (controller?.status ?: idlePlayerStatus).collectAsState()
    val isPlaying = status == PlayerStatus.PLAYING
    val canPause = isPlaying || status == PlayerStatus.BUFFERING
    var volume by remember(controller) { mutableFloatStateOf((controller?.getVolume() ?: 100).toFloat()) }
    var interactions by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var controlsFocused by remember { mutableStateOf(false) }
    val sliderInteraction = remember { MutableInteractionSource() }
    val sliderDragging by sliderInteraction.collectIsDraggedAsState()

    val enabled = controller != null && !controller.isReleased
    LaunchedEffect(isFullScreen) {
        if (isFullScreen) { showControls = true; interactions++ }
    }

    LaunchedEffect(showControls, interactions, dragging, sliderDragging, isPlaying, showTrackDialog, controlsFocused) {
        if (showControls && isPlaying && !dragging && !sliderDragging && !showTrackDialog && !controlsFocused) {
            delay(3_000)
            showControls = false
        }
    }

    if (showTrackDialog) TrackSelectionDialog(controller) {
        showTrackDialog = false
        interactions++
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 260.dp
        // This input layer sits behind the buttons and slider so their taps do
        // not also toggle the overlay or trigger volume gestures.
        Box(Modifier.matchParentSize()
            .pointerInput(controller) {
                detectTapGestures { showControls = !showControls; interactions++ }
            }
            .pointerInput(controller) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true; showControls = true; interactions++ },
                    onDragEnd = { dragging = false; interactions++ },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        if (controller != null && !controller.isReleased) {
                            volume = (volume - amount * 100f / size.height.coerceAtLeast(1)).coerceIn(0f, 100f)
                            controller.setVolume(volume.roundToInt())
                        }
                    }
                )
            })

        if (!showControls) Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.Gray,
                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }

        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().onFocusChanged { controlsFocused = it.hasFocus }
                .background(Color.Black.copy(alpha = 0.6f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth().padding(if (compact) 4.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (onEnterPip != null) IconButton(onClick = { onEnterPip(); interactions++ },
                        modifier = Modifier.size(if (compact) 32.dp else 48.dp)) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }
                    if (onToggleFullScreen != null) IconButton(onClick = { onToggleFullScreen(); interactions++ },
                        modifier = Modifier.size(if (compact) 32.dp else 48.dp)) {
                        Icon(if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit fullscreen" else "Enter fullscreen", tint = Color.White)
                    }
                    IconButton(onClick = { onClose?.invoke() }, enabled = onClose != null,
                        modifier = Modifier.size(if (compact) 32.dp else 48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = enabled, modifier = Modifier.size(if (compact) 32.dp else 48.dp), onClick = { controller?.stop(); interactions++ }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(enabled = enabled, modifier = Modifier.size(if (compact) 40.dp else 64.dp), onClick = {
                        if (canPause) controller?.pause() else controller?.play()
                        interactions++
                    }) {
                        Icon(if (canPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (canPause) "Pause" else "Play", tint = Color.White,
                            modifier = Modifier.size(56.dp))
                    }
                    IconButton(enabled = enabled && onChannelSwitchRequested != null, modifier = Modifier.size(if (compact) 32.dp else 48.dp),
                        onClick = { interactions++; onChannelSwitchRequested?.invoke() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next channel", tint = Color.White,
                            modifier = Modifier.size(40.dp))
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 24.dp, vertical = if (compact) 0.dp else 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume: ${volume.roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        IconButton(enabled = enabled, modifier = Modifier.size(if (compact) 32.dp else 48.dp), onClick = { showTrackDialog = true; interactions++ }) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Audio & Subtitles", tint = Color.White)
                        }
                    }
                    Slider(value = volume, modifier = Modifier.height(if (compact) 24.dp else 48.dp), onValueChange = {
                        volume = it
                        controller?.setVolume(it.roundToInt())
                        showControls = true
                        interactions++
                    }, onValueChangeFinished = { interactions++ }, valueRange = 0f..100f,
                        enabled = enabled, interactionSource = sliderInteraction,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Cyan))
                }
            }
        }
    }
}

