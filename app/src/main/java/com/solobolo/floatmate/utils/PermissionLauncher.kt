package com.solobolo.floatmate.utils

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun FloatMatePermissionLauncher(
    onAllPermissionsGranted: () -> Unit = {},
    onPermissionsDenied: () -> Unit = {}
) {
    val context = LocalContext.current
    var showOverlayRationale by rememberSaveable { mutableStateOf(false) }
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }

    // Check overlay permission
    val hasOverlayPermission = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)

            } else {
                true
            }
        )
    }

    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showNotificationRationale = true
            onPermissionsDenied()
        } else {
            // Check if we have all permissions now
            if (hasOverlayPermission.value) {
                onAllPermissionsGranted()
            }
        }
    }

    // Check and request permissions on launch
    LaunchedEffect(Unit) {
        // First check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            showOverlayRationale = true
        } else {
            hasOverlayPermission.value = true

            // Then check notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // For older versions, notifications are enabled by default
                onAllPermissionsGranted()
            }
        }
    }

    // Monitor overlay permission changes when returning from settings
    DisposableEffect(Unit) {
        val checkPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasPermission = Settings.canDrawOverlays(context)
                if (hasPermission && !hasOverlayPermission.value) {
                    hasOverlayPermission.value = true

                    // Now request notification permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onAllPermissionsGranted()
                    }
                }
            }
        }

        onDispose {
            checkPermission()
        }
    }

    // Overlay Permission Rationale Dialog
    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false },
            title = { Text("Overlay Permission Required") },
            text = {
                Text("FloatMate needs permission to display over other apps to show the floating bubble. This is essential for the app to function.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayRationale = false
                        // Open overlay settings
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayRationale = false
                        onPermissionsDenied()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notification Permission Rationale Dialog
    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("Notification Permission Required") },
            text = {
                Text("FloatMate needs notification permission to keep the service running in the background. Without this, the app may stop unexpectedly.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        // Open app notification settings
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        onPermissionsDenied()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}