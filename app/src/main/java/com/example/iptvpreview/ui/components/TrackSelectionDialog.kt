package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun TrackSelectionDialog(controller: VlcPlayerController?, onDismiss: () -> Unit) {
    if (controller == null || controller.isReleased) return
    val revision by controller.trackRevision.collectAsState()
    var tracks by remember(controller) { mutableStateOf(controller.getTracks()) }
    var selectionError by remember(controller) { mutableStateOf<String?>(null) }
    LaunchedEffect(controller, revision) { tracks = controller.getTracks() }
    val audioTracks = tracks.filter { it.type == VlcTrackType.AUDIO }
    val subtitleTracks = tracks.filter { it.type == VlcTrackType.SUBTITLE }

    fun choose(track: VlcTrack) {
        selectionError = if (controller.selectTrack(track)) null else "Could not select this track."
        tracks = controller.getTracks()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio & Subtitles") },
        text = {
            Column {
                if (selectionError != null) Text(selectionError!!, color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item {
                        Text("Audio Tracks", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (audioTracks.isEmpty()) item { Text("No audio tracks available.") }
                    item {
                        Column(Modifier.selectableGroup()) {
                            audioTracks.forEach { track ->
                                key(track.id) {
                                    TrackItem(track.name, track.isSelected, onClick = { choose(track) })
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text("Subtitles", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.selectableGroup()) {
                            TrackItem("Off", subtitleTracks.none { it.isSelected }, onClick = {
                                selectionError = if (subtitleTracks.none { it.isSelected } || controller.disableSubtitles()) null
                                    else "Could not disable subtitles."
                                tracks = controller.getTracks()
                            })
                            subtitleTracks.forEach { track ->
                                key(track.id) {
                                    TrackItem(track.name, track.isSelected, onClick = { choose(track) })
                                }
                            }
                        }
                        if (subtitleTracks.isEmpty()) Text("No subtitle tracks available.")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun TrackItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
