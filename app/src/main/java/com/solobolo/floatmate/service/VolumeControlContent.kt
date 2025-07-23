package com.solobolo.floatmate.service

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun VolumeControlContent() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Media Volume
        VolumeSlider(
            label = "Media",
            icon = Icons.Default.MusicNote,
            streamType = AudioManager.STREAM_MUSIC,
            audioManager = audioManager
        )

        // Ring Volume
        VolumeSlider(
            label = "Ring",
            icon = Icons.Default.ArrowCircleUp,
            streamType = AudioManager.STREAM_RING,
            audioManager = audioManager
        )

        // Notification Volume
        VolumeSlider(
            label = "Notifications",
            icon = Icons.Default.Notifications,
            streamType = AudioManager.STREAM_NOTIFICATION,
            audioManager = audioManager
        )

        // Alarm Volume
        VolumeSlider(
            label = "Alarm",
            icon = Icons.Default.Alarm,
            streamType = AudioManager.STREAM_ALARM,
            audioManager = audioManager
        )
    }
}

@Composable
private fun VolumeSlider(
    label: String,
    icon: ImageVector,
    streamType: Int,
    audioManager: AudioManager
) {
    val maxVolume = audioManager.getStreamMaxVolume(streamType)
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(streamType))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(currentVolume * 100 / maxVolume)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = currentVolume.toFloat(),
                onValueChange = { newValue ->
                    currentVolume = newValue.toInt()
                    audioManager.setStreamVolume(streamType, currentVolume, 0)
                },
                valueRange = 0f..maxVolume.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        IconButton(
            onClick = {
                val isMuted = currentVolume == 0
                currentVolume = if (isMuted) {
                    maxVolume / 2
                } else {
                    0
                }
                audioManager.setStreamVolume(streamType, currentVolume, 0)
            }
        ) {
            Icon(
                imageVector = if (currentVolume == 0)
                    Icons.AutoMirrored.Filled.VolumeOff
                else
                    Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (currentVolume == 0) "Unmute" else "Mute"
            )
        }
    }
}
