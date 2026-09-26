package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.data.model.Playlist

@Composable
fun PlaylistSelector(
    playlists: List<Playlist>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    val currentName = playlists.find { it.id == selectedId }?.name ?: "All Playlists"
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true }, enabled = enabled,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.ListAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(currentName, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, "Choose playlist")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            modifier = Modifier.width(250.dp).heightIn(max = 400.dp)) {
            DropdownMenuItem(
                text = { Text("All Playlists", fontWeight = if (selectedId == null) FontWeight.Bold else null) },
                leadingIcon = { Icon(Icons.Default.GridView, null) },
                onClick = { onSelect(null); expanded = false },
                trailingIcon = { if (selectedId == null) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) }
            )
            HorizontalDivider()
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    enabled = playlist.isActive,
                    text = {
                        Column {
                            Text(playlist.name, fontWeight = if (selectedId == playlist.id) FontWeight.Bold else null)
                            Text("${playlist.type}${if (playlist.isActive) "" else " • Disabled"}",
                                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    leadingIcon = {
                        Icon(if (playlist.isActive) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                            null, tint = if (playlist.isActive) MaterialTheme.colorScheme.primary else Color.Gray)
                    },
                    onClick = { onSelect(playlist.id); expanded = false },
                    trailingIcon = { if (selectedId == playlist.id) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }
    }
}
