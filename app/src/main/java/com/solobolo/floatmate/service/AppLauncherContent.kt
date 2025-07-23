package com.solobolo.floatmate.service

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppLauncherContent(
    favoriteApps: Set<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AppPickerDialog(
            currentFavorites = favoriteApps,
            onDismiss = { showAppPicker = false },
            onAppSelected = { /* Handle in parent */ }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (favoriteApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No favorite apps yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showAppPicker = true }) {
                        Text("Add Apps")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
//                items(favoriteApps.toList()) { packageName ->
//                    AppItem(
//                        packageName = packageName,
//                        packageManager = packageManager,
//                        onClick = {
//                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
//                            launchIntent?.let {
//                                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                                context.startActivity(it)
//                                onDismiss()
//                            }
//                        }
//                    )
//                }

                if (favoriteApps.size < 8) {
                    item {
                        AddAppButton(onClick = { showAppPicker = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    packageName: String,
    packageManager: PackageManager,
    onClick: () -> Unit
) {
    var appInfo by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(info).toString()
                val icon = packageManager.getApplicationIcon(packageName)
                appInfo = AppInfo(label, icon.toBitmap().asImageBitmap())
            } catch (e: Exception) {
                // App not found
            }
        }
    }

    appInfo?.let { info ->
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                bitmap = info.icon,
                contentDescription = info.label,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = info.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddAppButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add App",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = "Add",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppPickerDialog(
    currentFavorites: Set<String>,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    // Implementation for app picker dialog
    // This would show a list of installed apps to choose from
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("Select Apps") },
//        text = {
//            Text("App picker implementation goes here")
//        },
//        confirmButton = {
//            TextButton(onClick = onDismiss) {
//                Text("Done")
//            }
//        }
//    )
}

private data class AppInfo(
    val label: String,
    val icon: ImageBitmap
)