package com.example.iptvpreview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun DragHandle(onDragHorizontal: (Float) -> Unit, modifier: Modifier = Modifier) {
    val onDrag by rememberUpdatedState(onDragHorizontal)
    var dragging by remember { mutableStateOf(false) }
    Box(modifier.width(8.dp).fillMaxHeight()
        .background(Color.Gray.copy(alpha = if (dragging) 0.65f else 0.3f))
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onHorizontalDrag = { change, deltaPx ->
                    change.consume()
                    onDrag(deltaPx)
                }
            )
        })
}
