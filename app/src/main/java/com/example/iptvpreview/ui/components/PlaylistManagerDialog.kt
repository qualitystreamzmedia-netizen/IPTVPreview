package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.ui.player.PlayerViewModel

@Composable
fun PlaylistManagerDialog(viewModel: PlayerViewModel, onManageOrder: () -> Unit = {}, onDismiss: () -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Playlists & Categories") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                item { TextButton(onClick = onManageOrder) { Text("Reorder categories") } }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                if (playlists.isEmpty()) item { Text("Add a source in Settings to get started.") }
                items(playlists, key = { "playlist:${it.id}" }) { playlist ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row {
                            Text(playlist.name, modifier = Modifier.weight(1f))
                            Switch(checked = playlist.isActive, enabled = !loading,
                                onCheckedChange = { viewModel.togglePlaylistActive(playlist.id, it) })
                        }
                        Text(playlist.type.name, style = MaterialTheme.typography.labelSmall)
                        Row {
                            TextButton(enabled = !loading && playlist.isActive,
                                onClick = { viewModel.refreshPlaylist(playlist.id) }) { Text("Refresh") }
                            TextButton(enabled = !loading,
                                onClick = { viewModel.removePlaylist(playlist.id) }) { Text("Remove") }
                        }
                    }
                }
                item { Text("Visible categories", style = MaterialTheme.typography.titleSmall) }
                items(categories, key = { "category:${it.name}" }) { category ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${category.name} (${category.count})", Modifier.weight(1f))
                        Checkbox(checked = !category.isHidden, enabled = !loading,
                            onCheckedChange = { viewModel.toggleCategoryVisibility(category.name, !it) })
                    }
                }
            }
        })
}
