package com.solobolo.floatmate.service.bubble

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BubbleView(
    isDragging: Boolean = false
) {
    // Animate scale and alpha based on dragging state
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubble_scale"
    )

    val shadowElevation by animateFloatAsState(
        targetValue = if (isDragging) 12f else 8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_shadow"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_alpha"
    )

    Box(
        modifier = Modifier
            .size(60.dp)
            .scale(scale)
            .alpha(alpha)
            .shadow(shadowElevation.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (isDragging) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ),
        // REMOVED .clickable modifier - touch handling is done natively in the service
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = "FloatMate",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}