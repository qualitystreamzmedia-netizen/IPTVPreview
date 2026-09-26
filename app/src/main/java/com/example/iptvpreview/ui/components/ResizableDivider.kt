package com.example.iptvpreview.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun ResizableDivider(enabled: Boolean, onResize: (Float) -> Unit, onStep: (Float) -> Unit,
    label: String = "Resize browser and player", resizedPanel: String = "browser") {
    Box(
        Modifier.width(if (enabled) 8.dp else 0.dp).fillMaxHeight()
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction("Narrower $resizedPanel") { onStep(-32f); true },
                    CustomAccessibilityAction("Wider $resizedPanel") { onStep(32f); true }
                )
            }
            .onKeyEvent {
                if (enabled && it.type == KeyEventType.KeyDown && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) {
                    onStep(if (it.key == Key.DirectionLeft) -32f else 32f)
                    true
                } else false
            }.focusable(enabled), contentAlignment = Alignment.Center
    ) {
        if (enabled) DragHandle(onDragHorizontal = onResize)
    }
}
