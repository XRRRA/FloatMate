package com.solobolo.floatmate.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

sealed interface RootNavDestinations {
    @Serializable
    data object Home : RootNavDestinations

    @Serializable
    data object Settings : RootNavDestinations

    @Serializable
    data object Permissions : RootNavDestinations
}

@Composable
fun RootNavGraph() {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalRootProvider provides navController) {
        NavHost(
            navController = navController,
            startDestination = RootNavDestinations.Home,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<RootNavDestinations.Home> {
                // HomeController()
            }
            composable<RootNavDestinations.Settings> {
                // SettingsController()
            }
            composable<RootNavDestinations.Permissions> {
                // PermissionsController()
            }
        }
    }
}

val LocalRootProvider = staticCompositionLocalOf<NavHostController> {
    error("NavHostController not initialized")
}