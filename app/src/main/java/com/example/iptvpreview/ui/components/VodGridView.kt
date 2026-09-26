package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    } else LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = modifier,
        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { item ->
            Card(onClick = { onItemClick(item) }) {
                AsyncImage(model = item.posterUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text(item.name, modifier = Modifier.padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.seasonNumber?.let { season ->
                    Text("Season $season • Episode ${item.episodeNumber ?: "—"}",
                        modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
