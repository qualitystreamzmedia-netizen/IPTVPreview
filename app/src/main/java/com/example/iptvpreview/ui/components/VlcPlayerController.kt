package com.example.iptvpreview.ui.components

import android.os.Handler
import android.os.Looper
import android.net.Uri
import org.videolan.libvlc.Media
import kotlinx.coroutines.*
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayerStatus { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }
enum class VlcTrackType { VIDEO, AUDIO, SUBTITLE }
data class VlcTrack(val id: Int, val type: VlcTrackType, val name: String, val isSelected: Boolean)

/** UI-thread controls. A controller belongs to one player composition, not the whole app. */
@MainThread
class VlcPlayerController internal constructor(
    private val mediaPlayer: MediaPlayer,
    private val libVLC: LibVLC
) {
    var isReleased by mutableStateOf(false)
        private set
    private val _status = MutableStateFlow(PlayerStatus.IDLE)
    val status: StateFlow<PlayerStatus> = _status.asStateFlow()
    private val _trackRevision = MutableStateFlow(0L)
    val trackRevision = _trackRevision.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var transportStatus = PlayerStatus.IDLE
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var retryJob: Job? = null
    private val retryPolicy = PlaybackRetryPolicy()
    private var currentUrl: String? = null
    private var playbackRequested = false
    private var generation = 0L

    init {
        attachListener()
    }

    private fun attachListener() {
        val eventGeneration = generation
        // LibVLC supports one listener per player. Own it here so observers do
        // not replace each other's buffering, error, or playback notifications.
        mediaPlayer.setEventListener { event ->
            val type = event.type
            val bufferPercent = if (type == MediaPlayer.Event.Buffering) event.buffering else 100f
            mainHandler.post {
                if (!isReleased && eventGeneration == generation && playbackRequested) {
                    when (type) {
                        MediaPlayer.Event.Opening -> updateStatus(PlayerStatus.BUFFERING)
                        MediaPlayer.Event.Playing -> {
                            retryJob?.cancel(); retryJob = null
                            retryPolicy.reset()
                            updateStatus(PlayerStatus.PLAYING)
                        }
                        MediaPlayer.Event.Paused -> updateStatus(PlayerStatus.PAUSED)
                        MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> if (retryJob == null) updateStatus(PlayerStatus.IDLE)
                        MediaPlayer.Event.EncounteredError -> scheduleRetry()
                        MediaPlayer.Event.Buffering -> _status.value = bufferingStatus(transportStatus, bufferPercent)
                        MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> _trackRevision.value++
                    }
                }
            }
        }
    }

    private fun updateStatus(value: PlayerStatus) {
        transportStatus = value
        _status.value = value
    }

    internal fun reportError() { if (!isReleased) scheduleRetry() }

    private fun scheduleRetry() {
        if (!playbackRequested || isReleased || retryJob != null) return
        val url = currentUrl ?: run { updateStatus(PlayerStatus.ERROR); return }
        val wait = retryPolicy.nextDelayMillis() ?: run { updateStatus(PlayerStatus.ERROR); return }
        updateStatus(PlayerStatus.BUFFERING)
        val token = generation
        retryJob = retryScope.launch {
            delay(wait)
            retryJob = null
            if (!isReleased && playbackRequested && token == generation) loadMedia(url)
        }
    }

    fun playUrl(url: String) {
        if (isReleased) return
        retryJob?.cancel(); retryJob = null
        retryPolicy.reset()
        currentUrl = url
        playbackRequested = true
        loadMedia(url)
    }

    private fun loadMedia(url: String) {
        generation++
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        attachListener()
        updateStatus(PlayerStatus.BUFFERING)
        try {
            val media = Media(libVLC, Uri.parse(url))
            try { media.setHWDecoderEnabled(true, false); mediaPlayer.media = media }
            finally { media.release() }
            mediaPlayer.play()
        } catch (_: Exception) { scheduleRetry() }
    }

    fun play() {
        if (!isReleased) {
            if (currentUrl != null && (_status.value == PlayerStatus.ERROR || _status.value == PlayerStatus.IDLE)) {
                playUrl(currentUrl!!)
                return
            }
            playbackRequested = true
            if (!mediaPlayer.isPlaying) updateStatus(PlayerStatus.BUFFERING)
            mediaPlayer.play()
        }
    }
    fun pause() {
        if (!isReleased) {
            playbackRequested = false
            retryJob?.cancel(); retryJob = null
            generation++
            attachListener()
            mediaPlayer.pause()
            if (_status.value != PlayerStatus.IDLE && _status.value != PlayerStatus.ERROR) updateStatus(PlayerStatus.PAUSED)
        }
    }
    fun stop() { if (!isReleased) {
        playbackRequested = false
        retryJob?.cancel(); retryJob = null
        retryPolicy.reset(); generation++
        attachListener()
        mediaPlayer.stop(); updateStatus(PlayerStatus.IDLE)
    } }
    fun isPlaying(): Boolean = !isReleased && mediaPlayer.isPlaying

    fun setVolume(vol: Int) {
        if (!isReleased) mediaPlayer.volume = vol.coerceIn(0, 100)
    }

    fun getVolume(): Int = if (isReleased) 0 else mediaPlayer.volume

    /** Tracks become available after VLC opens the stream. IDs are native IDs, not positions. */
    fun getTracks(): List<VlcTrack> {
        if (isReleased) return emptyList()
        return buildList {
            fun append(type: VlcTrackType, tracks: Array<MediaPlayer.TrackDescription>?, selectedId: Int) {
                tracks.orEmpty().filter { it.id >= 0 }.forEach { track ->
                    add(VlcTrack(track.id, type, track.name.orEmpty().ifBlank { "${type.name.lowercase()} ${track.id}" }, track.id == selectedId))
                }
            }
            append(VlcTrackType.VIDEO, mediaPlayer.videoTracks, mediaPlayer.videoTrack)
            append(VlcTrackType.AUDIO, mediaPlayer.audioTracks, mediaPlayer.audioTrack)
            append(VlcTrackType.SUBTITLE, mediaPlayer.spuTracks, mediaPlayer.spuTrack)
        }
    }

    /** Prefer a typed track to an index when a dialog holds a snapshot of the list. */
    fun selectTrack(track: VlcTrack): Boolean {
        if (isReleased || getTracks().none { it.type == track.type && it.id == track.id }) return false
        return when (track.type) {
            VlcTrackType.VIDEO -> mediaPlayer.setVideoTrack(track.id)
            VlcTrackType.AUDIO -> mediaPlayer.setAudioTrack(track.id)
            VlcTrackType.SUBTITLE -> mediaPlayer.setSpuTrack(track.id)
        }
    }

    /** Index refers to the current combined getTracks() list, not a native VLC ID. */
    fun selectTrack(index: Int): Boolean = getTracks().getOrNull(index)?.let { selectTrack(it) } ?: false

    /** Only subtitle tracks can be disabled through this helper. */
    fun disableTrack(index: Int): Boolean {
        val track = getTracks().getOrNull(index) ?: return false
        return track.type == VlcTrackType.SUBTITLE && disableSubtitles()
    }

    fun disableSubtitles(): Boolean = !isReleased && mediaPlayer.setSpuTrack(-1)

    /** Safe to call again when Compose disposes a player already released by its parent. */
    fun release() {
        if (isReleased) return
        isReleased = true
        retryScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        updateStatus(PlayerStatus.IDLE)
        try {
            mediaPlayer.setEventListener(null)
            try { mediaPlayer.stop() } finally { mediaPlayer.detachViews() }
        } finally {
            try { mediaPlayer.release() } finally { libVLC.release() }
        }
    }
}

internal class PlaybackRetryPolicy {
    private var count = 0
    fun nextDelayMillis(): Long? = if (count >= 3) null else 1000L shl count++
    fun reset() { count = 0 }
}

/** Buffer completion is not proof of playback: wait for the native Playing event. */
internal fun bufferingStatus(transport: PlayerStatus, percent: Float): PlayerStatus = when (transport) {
    PlayerStatus.PAUSED, PlayerStatus.IDLE, PlayerStatus.ERROR -> transport
    else -> if (percent < 100f) PlayerStatus.BUFFERING else transport
}
