package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class NavItem { HOME, FAVORITES, RECENT, GUIDE }

@Composable
fun LeftNavRail(selectedItem: NavItem, onItemClick: (NavItem) -> Unit, modifier: Modifier = Modifier, isFocused: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.width(96.dp).fillMaxHeight()) {
        Column(Modifier.background(if (isFocused) Color.DarkGray.copy(alpha = 0.3f) else Color.Transparent)
            .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            NavRailButton(Icons.Default.Home, "Home", selectedItem == NavItem.HOME) { onItemClick(NavItem.HOME) }
            NavRailButton(Icons.Default.Star, "Favs", selectedItem == NavItem.FAVORITES) { onItemClick(NavItem.FAVORITES) }
            NavRailButton(Icons.Default.Schedule, "Recent", selectedItem == NavItem.RECENT) { onItemClick(NavItem.RECENT) }
            NavRailButton(Icons.Default.CalendarMonth, "Guide", selectedItem == NavItem.GUIDE) { onItemClick(NavItem.GUIDE) }
        }
    }
}

@Composable
fun NavRailButton(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.secondary
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(80.dp).padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp).padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = if (isSelected) accent else Color.LightGray,
            modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) accent else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
