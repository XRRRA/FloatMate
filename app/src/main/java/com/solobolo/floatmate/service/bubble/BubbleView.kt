package com.solobolo.floatmate.service.bubble

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.solobolo.floatmate.R

@Composable
fun BubbleView(
    isDragging: Boolean = false
) {
    // Animate scale and alpha based on dragging state
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.9f,
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
        targetValue = if (isDragging) 0.8f else 0.9f,
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
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.floatmate),
            contentDescription = "FloatMate",
            tint = Color.Unspecified,
            modifier = Modifier.size(64.dp)
        )
    }
}