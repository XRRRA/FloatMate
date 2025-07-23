package com.solobolo.floatmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.solobolo.floatmate.navigation.RootNavGraph
import com.solobolo.floatmate.ui.theme.FloatMateTheme
import com.solobolo.floatmate.utils.FloatMatePermissionLauncher
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FloatMateTheme {
                var permissionsGranted by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RootNavGraph(permissionsGranted = permissionsGranted)
                }

                FloatMatePermissionLauncher(
                    onAllPermissionsGranted = {
                        permissionsGranted = true
                    },
                    onPermissionsDenied = {
                        // Permissions are handled in the UI
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // This will trigger permission check when returning from settings
    }
}

