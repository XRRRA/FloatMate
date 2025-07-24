package com.solobolo.floatmate.service

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
    onDismiss: () -> Unit,
    sharedPrefs: FloatMateSharedPrefs
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    var currentFavorites by remember(favoriteApps) {
        mutableStateOf(cleanupUninstalledApps(favoriteApps, packageManager, sharedPrefs))
    }
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AppPickerContent(
            currentFavorites = currentFavorites,
            onBack = { showAppPicker = false },
            onAppsSelected = { selectedApps ->
                val cleanedApps = cleanupUninstalledApps(selectedApps, packageManager, sharedPrefs)
                sharedPrefs.favoriteApps = cleanedApps
                currentFavorites = cleanedApps
                showAppPicker = false
            }
        )
    } else {
        AppLauncherGrid(
            favoriteApps = currentFavorites,
            onAddAppsClick = { showAppPicker = true },
            onDismiss = onDismiss,
            sharedPrefs = sharedPrefs
        )
    }
}

private fun cleanupUninstalledApps(
    favoriteApps: Set<String>,
    packageManager: PackageManager,
    sharedPrefs: FloatMateSharedPrefs
): Set<String> {
    val validApps = favoriteApps.filter { packageName ->
        try {
            packageManager.getApplicationInfo(packageName, 0)
            packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }.toSet()

    if (validApps.size != favoriteApps.size) {
        sharedPrefs.favoriteApps = validApps
    }

    return validApps
}

@Composable
private fun AppLauncherGrid(
    favoriteApps: Set<String>,
    onAddAppsClick: () -> Unit,
    onDismiss: () -> Unit,
    sharedPrefs: FloatMateSharedPrefs
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

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
                    Button(onClick = onAddAppsClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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
                items(favoriteApps.toList()) { packageName ->
                    AppItem(
                        packageName = packageName,
                        packageManager = packageManager,
                        onAppNotFound = {
                            val updatedFavorites = favoriteApps - packageName
                            sharedPrefs.favoriteApps = updatedFavorites
                        },
                        onClick = {
                            try {
                                val launchIntent =
                                    packageManager.getLaunchIntentForPackage(packageName)
                                launchIntent?.let {
                                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(it)
                                    onDismiss()
                                }
                            } catch (_: Exception) {
                                sharedPrefs.removeFavoriteApp(packageName)
                            }
                        }
                    )
                }

                if (favoriteApps.size < 8) {
                    item {
                        AddAppButton(onClick = onAddAppsClick)
                    }
                }
            }

            if (favoriteApps.size >= 8) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddAppsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Choose Other Favorites")
                }
            }
        }
    }
}

@Composable
private fun AppPickerContent(
    currentFavorites: Set<String>,
    onBack: () -> Unit,
    onAppsSelected: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedApps by remember { mutableStateOf(currentFavorites.toList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { app ->
                        try {
                            packageManager.getLaunchIntentForPackage(app.packageName) != null &&
                                    (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                        } catch (_: Exception) {
                            false
                        }
                    }
                    .mapNotNull { app ->
                        try {
                            InstalledAppInfo(
                                packageName = app.packageName,
                                label = packageManager.getApplicationLabel(app).toString(),
                                icon = packageManager.getApplicationIcon(app).toBitmap()
                                    .asImageBitmap()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    .sortedBy { it.label.lowercase() }

                installedApps = apps

                selectedApps = selectedApps.filter { packageName ->
                    apps.any { it.packageName == packageName }
                }

                isLoading = false
            } catch (_: Exception) {
                isLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Select Apps",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TextButton(
                    onClick = { onAppsSelected(selectedApps.toSet()) }
                ) {
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${selectedApps.size}/8",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedApps.isNotEmpty()) {
                    TextButton(
                        onClick = { selectedApps = emptyList() }
                    ) {
                        Text("Clear All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Loading apps...")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(installedApps) { app ->
                        AppPickerItem(
                            app = app,
                            isSelected = selectedApps.contains(app.packageName),
                            onToggle = {
                                selectedApps = if (selectedApps.contains(app.packageName)) {
                                    selectedApps.filter { it != app.packageName }
                                } else if (selectedApps.size < 8) {
                                    selectedApps + app.packageName
                                } else {
                                    selectedApps
                                }
                            },
                            canSelect = selectedApps.size < 8 || selectedApps.contains(app.packageName)
                        )
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
    onAppNotFound: () -> Unit = {},
    onClick: () -> Unit
) {
    var appInfo by remember { mutableStateOf<AppInfo?>(null) }
    var appExists by remember { mutableStateOf(true) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(info).toString()
                val icon = packageManager.getApplicationIcon(packageName)
                appInfo = AppInfo(label, icon.toBitmap(96, 96).asImageBitmap())
                appExists = true
            } catch (_: Exception) {
                appExists = false
                onAppNotFound()
            }
        }
    }

    if (appExists && appInfo != null) {
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
private fun AppPickerItem(
    app: InstalledAppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    canSelect: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canSelect) { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(40.dp)
            )

            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Checkbox(
                checked = isSelected,
                onCheckedChange = { if (canSelect) onToggle() },
                enabled = canSelect
            )
        }
    }
}

private data class AppInfo(
    val label: String,
    val icon: ImageBitmap
)

private data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap
)