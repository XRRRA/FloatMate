package com.solobolo.floatmate.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solobolo.floatmate.features.home.HomeController
import kotlinx.serialization.Serializable

sealed interface RootNavDestinations {
    @Serializable
    data object Home : RootNavDestinations

    @Serializable
    data object Settings : RootNavDestinations
}

@Composable
fun RootNavGraph(permissionsGranted: Boolean = false) {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalRootNavigator provides navController) {
        NavHost(
            navController = navController,
            startDestination = RootNavDestinations.Home,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<RootNavDestinations.Home> {
                HomeController(permissionsGranted = permissionsGranted)
            }
            composable<RootNavDestinations.Settings> {
//                SettingsController()
            }
        }
    }
}

val LocalRootNavigator = staticCompositionLocalOf<NavHostController> {
    error("NavController not initialized")
}