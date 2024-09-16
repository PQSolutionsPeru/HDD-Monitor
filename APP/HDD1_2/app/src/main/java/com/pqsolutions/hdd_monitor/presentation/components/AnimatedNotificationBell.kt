package com.pqsolutions.hdd_monitor.presentation.components

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

@Composable
fun AnimatedNotificationBell(
    hasNewNotifications: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("AnimatedNotificationBell", "Composing AnimatedNotificationBell, hasNewNotifications: $hasNewNotifications")

    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    IconButton(
        onClick = {
            Log.d("AnimatedNotificationBell", "Notification bell clicked")
            onClick()
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Notificaciones",
            modifier = Modifier
                .size(24.dp)
                .rotate(if (hasNewNotifications) angle else 0f),
            tint = if (hasNewNotifications) Color.Red else MaterialTheme.colorScheme.onSurface
        )
    }

    Log.d("AnimatedNotificationBell", "AnimatedNotificationBell composition completed")
}