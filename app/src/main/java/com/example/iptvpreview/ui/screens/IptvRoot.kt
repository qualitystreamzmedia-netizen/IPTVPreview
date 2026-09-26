package com.example.iptvpreview.ui.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.iptvpreview.IptvApp
import com.example.iptvpreview.MainActivity.RemoteAction
import com.example.iptvpreview.SettingsDialog
import com.example.iptvpreview.ui.player.PlayerViewModel
import kotlinx.coroutines.flow.Flow
import com.example.iptvpreview.ui.navigation.IptvNavController
import com.example.iptvpreview.ui.navigation.rememberIptvNavController

enum class AppRoute { HOME, BROWSER }

@Composable
fun IptvRoot(viewModel: PlayerViewModel, remoteActions: Flow<RemoteAction>,
    navController: IptvNavController = rememberIptvNavController()) {
    var currentRoute by rememberSaveable { mutableStateOf(AppRoute.HOME) }
    val screenState = rememberSaveableStateHolder()
    var addingPlaylist by rememberSaveable { mutableStateOf(false) }
    var parentalControls by rememberSaveable { mutableStateOf(false) }
    var categoryOrder by rememberSaveable { mutableStateOf(false) }
    if (categoryOrder) {
        ManageCategoriesScreen(viewModel, onBack = { categoryOrder = false })
    } else if (parentalControls) {
        ParentalControlScreen(viewModel, onBack = { parentalControls = false })
    } else {
        screenState.SaveableStateProvider(currentRoute) {
            when (currentRoute) {
                AppRoute.HOME -> HomeScreen(viewModel,
                    onNavigateToBrowser = { currentRoute = AppRoute.BROWSER },
                    onOpenSettings = { addingPlaylist = true },
                    onParentalControls = { parentalControls = true },
                    onManageCategoryOrder = { categoryOrder = true })
                AppRoute.BROWSER -> BrowserScreen(viewModel, remoteActions,
                    navController = navController,
                    onBackToHome = { currentRoute = AppRoute.HOME },
                    onParentalControls = { parentalControls = true },
                    onManageCategoryOrder = { categoryOrder = true })
            }
        }
    }
    if (addingPlaylist) {
        val epgUrl by viewModel.epgUrl.collectAsState()
        val epgLoading by viewModel.isEpgLoading.collectAsState()
        val epgError by viewModel.epgError.collectAsState()
        val epgMap by viewModel.epgMap.collectAsState()
        val epgUpdated by viewModel.epgLastUpdated.collectAsState()
        SettingsDialog(onDismiss = { addingPlaylist = false },
            viewModel = viewModel,
            onSave = { type, config -> viewModel.loadNewPlaylist(type, config); addingPlaylist = false },
            savedEpgUrl = epgUrl, epgLoading = epgLoading, epgError = epgError,
            epgProgramCount = epgMap.size, epgLoaded = epgUpdated != null,
            onEpgSave = viewModel::refreshEpg, onEpgClear = viewModel::clearEpg)
    }
}

@Composable
fun BrowserScreen(viewModel: PlayerViewModel, remoteActions: Flow<RemoteAction>,
    onBackToHome: () -> Unit, onParentalControls: () -> Unit, onManageCategoryOrder: () -> Unit,
    navController: IptvNavController = rememberIptvNavController()) {
    IptvApp(viewModel, remoteActions, onNavigateToDashboard = onBackToHome,
        onParentalControls = onParentalControls, onManageCategoryOrder = onManageCategoryOrder, navController = navController)
}
