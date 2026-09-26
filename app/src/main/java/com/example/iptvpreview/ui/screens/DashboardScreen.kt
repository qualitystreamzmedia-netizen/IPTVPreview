package com.example.iptvpreview.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.data.model.Playlist
import com.example.iptvpreview.ui.player.PlayerViewModel
import com.example.iptvpreview.ui.components.CategoryManagementDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: PlayerViewModel, onNavigateToPlayer: () -> Unit, onAddPlaylist: () -> Unit,
    onParentalControls: () -> Unit = {}, onOpenSettings: () -> Unit = onAddPlaylist) {
    val playlists by viewModel.playlists.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    var showCategories by remember { mutableStateOf(false) }
    if (showCategories) CategoryManagementDialog(categories,
        onDismiss = { showCategories = false },
        onToggleHide = viewModel::toggleCategoryVisibility,
        enabled = !loading, error = error)
    Scaffold(
        topBar = { TopAppBar(title = { Text("IPTV Manager") }, actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            IconButton(onClick = onParentalControls) { Icon(Icons.Default.Lock, contentDescription = "Parental Controls") }
            TextButton(onClick = { showCategories = true }) { Text("Categories") }
        }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPlaylist) {
                Icon(Icons.Default.Add, contentDescription = "Add Playlist")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            item {
                Button(onClick = onNavigateToPlayer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Go to Player")
                }
            }
            if (playlists.isEmpty() && !loading) item {
                Text("No playlists yet. Use Add Playlist to add an M3U or Xtream source.", Modifier.padding(16.dp))
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist, enabled = !loading,
                    onToggleActive = { viewModel.togglePlaylist(playlist.id, it) },
                    onDelete = { viewModel.deletePlaylist(playlist.id) })
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onToggleActive: (Boolean) -> Unit, onDelete: () -> Unit, enabled: Boolean = true) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                Text("${playlist.type} • ${if (playlist.isActive) "Active" else "Disabled"}",
                    color = if (playlist.isActive) Color.Green else Color.Gray,
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = playlist.isActive, onCheckedChange = onToggleActive, enabled = enabled)
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${playlist.name}", tint = Color.Red)
            }
        }
    }
}
