package com.example.iptvpreview.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VlcPlayer(
    modifier: Modifier = Modifier,
    streamUrl: String?,
    onError: ((String) -> Unit)? = null,
    onControllerReady: ((VlcPlayerController) -> Unit)? = null,
    showBuiltInControls: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)
    val libVlc = remember { LibVLC(context.applicationContext, arrayListOf("--no-video-title-show", "--network-caching=3000")) }
    val player = remember(libVlc) { MediaPlayer(libVlc) }
    val controller = remember(player, libVlc) { VlcPlayerController(player, libVlc) }
    val layout = remember(context) { VLCVideoLayout(context) }
    val status by controller.status.collectAsState()
    val playing = status == PlayerStatus.PLAYING
    val buffering = status == PlayerStatus.BUFFERING
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    DisposableEffect(player, layout) {
        // This API takes a VLCVideoLayout, not a SurfaceHolder. TextureView also
        // allows Compose overlays and handles video sizing through VLC's helper.
        player.attachViews(layout, null, false, true)
        onDispose {
            controller.release()
        }
    }

    LaunchedEffect(status, lifecycleOwner) {
        if (status == PlayerStatus.ERROR) {
            error = "Unable to play this channel. Try another channel or retry."
            currentOnError?.invoke(error!!)
        } else if (status == PlayerStatus.PLAYING &&
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.pause()
        }
    }

    LaunchedEffect(controller) {
        if (!controller.isReleased) currentOnControllerReady?.invoke(controller)
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, streamUrl, retry) {
        if (controller.isReleased) return@LaunchedEffect
        controller.stop()
        error = null
        if (!streamUrl.isNullOrBlank()) {
            try {
                val media = Media(libVlc, Uri.parse(streamUrl))
                try {
                    // Prefer hardware decoding, while allowing software fallback.
                    media.setHWDecoderEnabled(true, false)
                    player.media = media
                } finally { media.release() }
                controller.play()
            } catch (_: Exception) {
                controller.reportError()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { layout },
            update = { it.keepScreenOn = playing && !controller.isReleased },
            modifier = Modifier.fillMaxSize()
        )
        if (showBuiltInControls && buffering && !controller.isReleased) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        if (showBuiltInControls && error != null) {
            Text(error!!, color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f)).padding(16.dp))
        }
        if (showBuiltInControls && !streamUrl.isNullOrBlank()) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(when (status) {
                    PlayerStatus.IDLE -> "Stopped"
                    PlayerStatus.BUFFERING -> "Buffering…"
                    PlayerStatus.PLAYING -> "Playing"
                    PlayerStatus.PAUSED -> "Paused"
                    PlayerStatus.ERROR -> "Stream error"
                }, color = Color.White)
                TextButton(enabled = !controller.isReleased, onClick = {
                    if (error != null) retry++ else if (controller.isPlaying()) controller.pause() else controller.play()
                }) {
                    Text(if (error != null) "Retry" else if (playing) "Pause" else "Play")
                }
            }
        }
    }
}
