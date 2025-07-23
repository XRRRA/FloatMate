package com.solobolo.floatmate.features.home

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.solobolo.floatmate.navigation.LocalRootNavigator
import com.solobolo.floatmate.navigation.RootNavDestinations
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeController(permissionsGranted: Boolean) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val navigator = LocalRootNavigator.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sendEvent(HomeContract.Event.Initialize)
    }

    LaunchedEffect(viewModel) {
        viewModel.action.collectLatest { action ->
            when (action) {
                is HomeContract.Action.ShowToast -> {
                    Toast.makeText(context, action.message, Toast.LENGTH_SHORT).show()
                }
                is HomeContract.Action.NavigateToSettings -> {
                    navigator.navigate(RootNavDestinations.Settings)
                }
            }
        }
    }

    HomeScreen(
        state = state,
        permissionsGranted = permissionsGranted,
        onToggleService = {
            viewModel.sendEvent(HomeContract.Event.ToggleService)
        },
        onSettingsClick = { viewModel.sendEvent(HomeContract.Event.NavigateToSettings) }
    )
}