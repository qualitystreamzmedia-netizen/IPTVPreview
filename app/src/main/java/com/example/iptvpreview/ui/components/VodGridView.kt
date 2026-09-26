package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.iptvpreview.data.local.VodItemEntity

@Composable
fun VodGridView(items: List<VodItemEntity>, onItemClick: (VodItemEntity) -> Unit,
    modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Box(modifier.padding(24.dp)) { Text("No titles found. Refresh VOD to load your provider’s library.") }
    } else LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items, key = { it.id }) { item ->
            val subtitle = listOfNotNull(item.year?.toString(), item.durationMinutes?.takeIf { it > 0 }?.let { "${it}m" },
                item.seasonNumber?.let { "S$it" + (item.episodeNumber?.let { episode -> " E$episode" } ?: "") })
                .joinToString(" • ")
            VodCard(item.name, item.posterUrl, subtitle) { onItemClick(item) }
        }
    }
}

@Composable
fun VodCard(title: String, imageUrl: String?, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(240.dp), shape = RoundedCornerShape(12.dp)) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.DarkGray)) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.align(Alignment.Center))
                if (!imageUrl.isNullOrBlank()) AsyncImage(model = imageUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.padding(8.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
