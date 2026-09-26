package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IPTVSearchBar(
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit = {}
) {
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var pendingSearch by remember { mutableStateOf<Job?>(null) }
    var lastPublished by remember { mutableStateOf(initialQuery) }

    // Also accept externally cleared/replaced queries without re-emitting them.
    LaunchedEffect(initialQuery) {
        if (initialQuery != lastPublished && initialQuery != text) {
            pendingSearch?.cancel()
            text = initialQuery
        }
    }

    TextField(value = text, onValueChange = { value ->
        text = value
        pendingSearch?.cancel()
        pendingSearch = scope.launch {
            delay(300)
            lastPublished = value
            currentOnQueryChange(value)
        }
    }, modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search channels, groups…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) IconButton(onClick = {
                pendingSearch?.cancel()
                text = ""
                lastPublished = ""
                currentOnQueryChange("")
            }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            pendingSearch?.cancel()
            lastPublished = text
            currentOnQueryChange(text)
            keyboard?.hide()
            onSubmit()
        }))
}
