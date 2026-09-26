package com.example.iptvpreview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.ChannelRow
import com.example.iptvpreview.data.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun CategorySidebar(categories: List<Category>, selectedCategory: String?, onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier, selectedPlaylistName: String? = null, onEditOrder: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(), isFocused: Boolean = false) {
    Column(modifier.background(if (isFocused) Color.DarkGray.copy(alpha = 0.3f) else Color.Transparent).padding(top = 16.dp)) {
        val headerText = selectedCategory ?: selectedPlaylistName ?: "ALL CATEGORIES"
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(headerText.uppercase(), Modifier.weight(1f), color = Color.Gray,
                style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onEditOrder != null) IconButton(onClick = onEditOrder) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Order")
            }
        }
        ListItem(headlineContent = { Text("All Channels") }, leadingContent = { Icon(Icons.Default.GridView, null) },
            colors = ListItemDefaults.colors(containerColor = if (selectedCategory == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            modifier = Modifier.clickable { onSelect(null) })
        Divider()
        LazyColumn(state = listState) {
            items(categories.filterNot { it.isHidden }, key = { it.name }) { category ->
                val selected = selectedCategory == category.name
                ListItem(headlineContent = {
                    Text(category.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                },
                    supportingContent = { Text("${category.count} ch", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Label, null,
                        tint = if (selected) MaterialTheme.colorScheme.secondary else Color.Gray) },
                    colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp).clickable { onSelect(category.name) })
            }
        }
    }
}

@Composable
fun ChannelListPanel(
    channels: List<Channel>, selectedChannel: Channel?, onSelectChannel: (Channel) -> Unit,
    listState: LazyListState, isFocused: Boolean, modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    epg: Map<String, EpgProgram> = emptyMap(), focusedId: String? = null,
    onToggleFav: (String) -> Unit = {}, locked: (Channel) -> Boolean = { false }
) {
    LaunchedEffect(isFocused, focusRequester) {
        if (isFocused) focusRequester.requestFocus()
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxHeight()) {
        ChannelBrowserPanel(channels, selectedChannel, onSelectChannel,
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable(),
            listState = listState, epg = epg, focusedId = focusedId,
            onToggleFav = onToggleFav, locked = locked)
    }
}

@Composable
fun ChannelBrowserPanel(channels: List<Channel>, selectedChannel: Channel?, onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState(), epg: Map<String, EpgProgram> = emptyMap(),
    focusedId: String? = null, onToggleFav: (String) -> Unit = {}, locked: (Channel) -> Boolean = { false }) {
    LazyColumn(modifier, state = listState) {
        if (channels.isEmpty()) item { Text("No channels found", Modifier.padding(16.dp), color = Color.Gray) }
        items(channels, key = { it.id }) { channel ->
            ChannelListItem(channel, selectedChannel?.id == channel.id, { onSelectChannel(channel) },
                epgInfo = channel.epgId?.let { epg[it] }, isFocused = channel.id == focusedId,
                onToggleFav = { onToggleFav(channel.id) }, isLocked = locked(channel))
        }
    }
}

@Composable
fun ChannelListItem(channel: Channel, isSelected: Boolean, onClick: () -> Unit, epgInfo: EpgProgram? = null,
    isFocused: Boolean = false, onToggleFav: () -> Unit = {}, isLocked: Boolean = false) {
    val owner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), epgInfo, owner) {
        if (epgInfo != null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = System.currentTimeMillis(); delay(1000) }
        }
    }
    val program = epgInfo?.takeIf { it.endTime > now && it.endTime > it.startTime }
    Surface(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 8.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        color = when { isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) },
        border = if (isFocused) BorderStroke(2.dp, Color.Cyan) else null,
        shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isLocked) Icons.Default.Lock else Icons.Default.Tv,
                contentDescription = if (isLocked) "PIN required" else null,
                modifier = Modifier.size(28.dp), tint = Color.LightGray)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.group, style = MaterialTheme.typography.labelSmall, color = Color.LightGray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (program != null) {
                    val live = now >= program.startTime
                    Text("${if (live) "LIVE" else "NEXT"} • ${program.title}",
                        color = if (live) Color.Green else Color.LightGray,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val format = DateFormat.getTimeInstance(DateFormat.SHORT)
                    Text("${format.format(Date(program.startTime))} – ${format.format(Date(program.endTime))}",
                        color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
                    if (live) LinearProgressIndicator(progress = {
                        ((now - program.startTime).toDouble() / (program.endTime - program.startTime).toDouble()).toFloat().coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color.Green)
                }
            }
            IconButton(onClick = onToggleFav, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Star, if (channel.isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (channel.isFavorite) Color.Yellow else Color.Gray)
            }
        }
    }
}

@Composable
fun PlayerInfoPanel(channel: Channel?, controller: VlcPlayerController?, modifier: Modifier = Modifier,
    program: EpgProgram? = null, isFavorite: Boolean = false, onFavorite: () -> Unit = {},
    onPlay: () -> Unit = {}, onStop: () -> Unit = {}, fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}, pipMode: Boolean = false,
    onEnterPip: (() -> Unit)? = null, videoContent: @Composable BoxScope.() -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(program) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val current = program?.takeIf { it.endTime > now }
    Column(modifier.padding(if (fullscreen) 0.dp else 12.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)
            .then(if (fullscreen) Modifier else Modifier.clip(RoundedCornerShape(12.dp))).background(Color.Black),
            contentAlignment = Alignment.Center) {
            videoContent()
            if (fullscreen && !pipMode) IconButton(onClick = onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f))) {
                Icon(Icons.Default.FullscreenExit, "Exit fullscreen", tint = Color.White)
            }
        }
        if (!fullscreen) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("NOW PLAYING", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(channel?.name ?: "Ready to watch", style = MaterialTheme.typography.titleLarge)
                channel?.let { Text(it.group, color = Color.LightGray) }
                if (current != null) {
                    val format = DateFormat.getTimeInstance(DateFormat.SHORT)
                    Text("${if (now >= current.startTime) "LIVE" else "NEXT"} • ${current.title}")
                    Text("${format.format(Date(current.startTime))} – ${format.format(Date(current.endTime))}", style = MaterialTheme.typography.labelSmall)
                    current.description?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                } else Text(if (channel == null) "Choose a channel from the browser." else "No programme information available.", color = Color.Gray)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPlay, enabled = channel != null) { Text(if (controller?.isPlaying() == true) "Pause" else "Play") }
            TextButton(onClick = onStop, enabled = channel != null) { Text("Stop") }
            IconButton(onClick = onFavorite, enabled = channel != null) {
                Icon(Icons.Default.Star, "Toggle favorite", tint = if (isFavorite) Color.Yellow else Color.Gray)
            }
            IconButton(onClick = onToggleFullscreen, enabled = channel != null) {
                Icon(Icons.Default.Fullscreen, "Fullscreen")
            }
            if (onEnterPip != null) IconButton(onClick = onEnterPip) {
                Icon(Icons.Default.PictureInPictureAlt, "Picture-in-Picture")
            }
        }
        }
        }
    }
}
