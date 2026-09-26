package com.example.iptvpreview.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.data.model.Category
import com.example.iptvpreview.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val id by viewModel.selectedPlaylistId.collectAsState()
    val categories by viewModel.editableCategories.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    var edited by remember(id) { mutableStateOf(emptyList<Category>()) }
    var dirty by remember(id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(categories, dirty, busy) { if (!dirty && !busy) edited = categories }
    val back = { if (!busy) { if (dirty) confirmDiscard = true else onBack() } }
    BackHandler { back() }
    fun move(from: Int, to: Int) {
        if (to !in edited.indices || busy) return
        edited = edited.toMutableList().apply { add(to, removeAt(from)) }
        dirty = true
        message = null
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Manage Categories") }, navigationIcon = {
            IconButton(onClick = back, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            TextButton(enabled = id != null && dirty && !busy && !loading, onClick = {
                val selected = id ?: return@TextButton
                val order = edited.map { it.name }
                busy = true
                scope.launch {
                    try { viewModel.persistCustomOrder(selected, order); dirty = false; message = "Order saved." }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = "Could not save. Your edits are still available." }
                    finally { busy = false }
                }
            }) { Text("Save") }
            TextButton(enabled = id != null && !busy && !loading, onClick = {
                val selected = id ?: return@TextButton
                busy = true
                scope.launch {
                    try { viewModel.restoreNaturalOrder(selected); dirty = false; message = "Original order restored." }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = "Could not reset the order." }
                    finally { busy = false }
                }
            }) { Text("Reset") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (id == null) "Select a specific playlist in the player to manage its categories."
                else "Editing: ${playlists.find { it.id == id }?.name.orEmpty()}", Modifier.padding(16.dp))
            message?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
            if (id != null) {
                Text("Use Up and Down, then Save. Reset restores playlist order.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                if (edited.isEmpty() && !loading) Text("No categories loaded for this playlist.", Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(edited, key = { _, category -> category.name }) { index, category ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(category.name + if (category.isHidden) " (hidden)" else "", Modifier.weight(1f))
                                IconButton(enabled = !busy && !loading && index > 0, onClick = { move(index, index - 1) }) {
                                    Icon(Icons.Default.ArrowUpward, "Move ${category.name} up")
                                }
                                IconButton(enabled = !busy && !loading && index < edited.lastIndex, onClick = { move(index, index + 1) }) {
                                    Icon(Icons.Default.ArrowDownward, "Move ${category.name} down")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard unsaved order?") },
        confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
}
