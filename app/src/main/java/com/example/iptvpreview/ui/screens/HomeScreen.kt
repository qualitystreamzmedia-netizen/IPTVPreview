package com.example.iptvpreview.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.data.Channel
import com.example.iptvpreview.data.model.Playlist
import com.example.iptvpreview.ui.components.PlaylistManagerDialog
import com.example.iptvpreview.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PlayerViewModel, onNavigateToBrowser: () -> Unit,
    onOpenSettings: () -> Unit, onParentalControls: () -> Unit = {}, onManageCategoryOrder: () -> Unit = {}) {
    val playlists by viewModel.playlists.collectAsState()
    val recent by viewModel.recentChannels.collectAsState()
    val favorites by viewModel.favoriteChannels.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var showManager by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("IPTV Pro", fontWeight = FontWeight.Bold) }, actions = {
            TextButton(onClick = { showManager = true }) { Text("Manage") }
            IconButton(onClick = onParentalControls) { Icon(Icons.Default.Lock, "Parental Controls") }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            item { SectionHeader("Your Playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        HomePlaylistCard(playlist, onClick = {
                            if (playlist.isActive) {
                                viewModel.selectPlaylist(playlist.id); viewModel.clearSearch(); onNavigateToBrowser()
                            } else showManager = true
                        })
                    }
                    item { AddPlaylistButton(onOpenSettings) }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionHeader("Continue Watching") }
                item { HomeChannelRow(recent.take(10)) { viewModel.selectChannel(it); onNavigateToBrowser() } }
            }
            if (favorites.isNotEmpty()) {
                item { SectionHeader("Favorites") }
                item { HomeChannelRow(favorites.take(10)) { viewModel.selectChannel(it); onNavigateToBrowser() } }
            }
            item {
                Button(onClick = { viewModel.selectPlaylist(null); viewModel.clearSearch(); onNavigateToBrowser() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.GridView, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Browse All Channels", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    if (showManager) PlaylistManagerDialog(viewModel,
        onManageOrder = { showManager = false; onManageCategoryOrder() }, onDismiss = { showManager = false })
}

@Composable
fun SectionHeader(title: String) {
    Text(title, Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun HomePlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(180.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp).heightIn(min = 100.dp)) {
            Icon(if (playlist.isActive) Icons.Default.PlayCircle else Icons.Default.PauseCircle, null,
                Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (playlist.isActive) "Active" else "Disabled • Manage to enable", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AddPlaylistButton(onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.width(160.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 124.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Add, null, Modifier.size(32.dp))
            Text("Add Source")
        }
    }
}

@Composable
private fun HomeChannelRow(channels: List<Channel>, onClick: (Channel) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(channels, key = { it.id }) { channel -> ChannelPreviewCard(channel) { onClick(channel) } }
    }
}

@Composable
fun ChannelPreviewCard(channel: Channel, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(160.dp), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp).heightIn(min = 90.dp)) {
            Icon(Icons.Default.Tv, null, Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}
