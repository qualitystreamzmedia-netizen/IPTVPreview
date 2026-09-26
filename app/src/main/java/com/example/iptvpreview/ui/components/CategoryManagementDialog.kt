package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.data.model.Category

@Composable
fun CategoryManagementDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onToggleHide: (String, Boolean) -> Unit,
    enabled: Boolean = true,
    error: String? = null
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (!enabled) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                if (categories.isEmpty()) item { Text("No categories yet. Enable a playlist and load its channels first.") }
                items(categories, key = { it.name }) { category ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${category.name} (${category.count})", Modifier.weight(1f))
                        TextButton(enabled = enabled,
                            onClick = { onToggleHide(category.name, !category.isHidden) }) {
                            Text(if (category.isHidden) "Show" else "Hide",
                                color = if (category.isHidden) Color.Green else Color.Red)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
