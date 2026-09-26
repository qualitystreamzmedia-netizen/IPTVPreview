package com.example.iptvpreview.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PinPromptDialog(title: String = "Enter Parental PIN", onDismiss: () -> Unit,
    onSuccess: () -> Unit, verifyLogic: suspend (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val verify by rememberUpdatedState(verifyLogic)
    val success by rememberUpdatedState(onSuccess)
    val submit = {
        if (pin.length == 4 && !busy) {
            val entered = pin
            busy = true
            scope.launch {
                try {
                    if (verify(entered)) success()
                    else { message = "Incorrect PIN or temporarily locked. After 5 attempts, wait 30 seconds."; pin = "" }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "Could not verify PIN. Try again."; pin = "" }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(pin) { if (pin.length == 4 && !busy) submit() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(4); message = null },
                    label = { Text("4-digit PIN") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("Clear","0","⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            TextButton(enabled = !busy, onClick = {
                                pin = when (key) { "Clear" -> ""; "⌫" -> pin.dropLast(1); else -> (pin + key).take(4) }
                                message = null
                            }, modifier = Modifier.weight(1f)) { Text(key) }
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = submit, enabled = pin.length == 4 && !busy) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
