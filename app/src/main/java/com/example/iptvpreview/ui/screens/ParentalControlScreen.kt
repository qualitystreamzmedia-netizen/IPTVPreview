package com.example.iptvpreview.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.iptvpreview.ui.components.PinPromptDialog
import com.example.iptvpreview.ui.player.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val ready by viewModel.securityReady.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    val locked by viewModel.lockedCategories.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    var authorizedPin by remember { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { authorizedPin = null; newPin = ""; confirmPin = "" }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler { onBack() }
    if (ready && isPinSet && authorizedPin == null) {
        PinPromptDialog(title = "Unlock Parental Controls", onDismiss = onBack, onSuccess = {}, verifyLogic = { pin ->
            viewModel.verifyPin(pin).also { if (it) authorizedPin = pin }
        })
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Parental Controls") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (!ready) item { Text("Security settings are not ready. Reopen the app if this persists.") }
            if (ready && (!isPinSet || authorizedPin != null)) {
                item {
                    Text(if (isPinSet) "Change PIN" else "Set Master PIN", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(newPin, { newPin = it.filter { c -> c in '0'..'9' }.take(4) },
                        label = { Text("New PIN (4 digits)") }, enabled = !busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(confirmPin, { confirmPin = it.filter { c -> c in '0'..'9' }.take(4) },
                        label = { Text("Confirm PIN") }, enabled = !busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Button(enabled = !busy && newPin.length == 4 && newPin == confirmPin, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                if (viewModel.setParentalPin(newPin, authorizedPin)) {
                                    authorizedPin = newPin; newPin = ""; confirmPin = ""; message = "PIN saved."
                                } else message = "PIN verification failed. Reopen Parental Controls."
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { message = "Could not save PIN." }
                            finally { busy = false }
                        }
                    }) { Text("Save PIN") }
                    message?.let { Text(it) }
                    Spacer(Modifier.height(16.dp))
                }
                if (isPinSet) {
                    item { Text("Lock Categories", style = MaterialTheme.typography.titleMedium) }
                    items((categories.map { it.name } + locked).distinct().sorted(), key = { it }) { name ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(name, Modifier.weight(1f).padding(vertical = 12.dp))
                            Switch(checked = name in locked, enabled = !busy, onCheckedChange = { checked ->
                                busy = true
                                scope.launch {
                                    try {
                                        if (!viewModel.toggleCategoryLock(name, checked, authorizedPin.orEmpty())) message = "PIN verification failed."
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { message = "Could not save category lock." }
                                    finally { busy = false }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
