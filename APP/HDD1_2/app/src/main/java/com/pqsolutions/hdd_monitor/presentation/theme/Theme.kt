package com.pqsolutions.hdd_monitor.presentation.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = HddRed,
    onPrimary = HddWhite,
    primaryContainer = HddDarkRed,
    onPrimaryContainer = HddWhite,
    secondary = HddGrey,
    onSecondary = HddWhite,
    secondaryContainer = HddLightGrey,
    onSecondaryContainer = HddBlack,
    tertiary = HddBlue,
    onTertiary = HddWhite,
    tertiaryContainer = HddLightBlue,
    onTertiaryContainer = HddWhite,
    error = HddRed,
    onError = HddWhite,
    errorContainer = HddDarkRed,
    onErrorContainer = HddWhite,
    background = HddWhite,
    onBackground = HddBlack,
    surface = HddWhite,
    onSurface = HddBlack
)

@Composable
fun HDD1_2Theme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme  // Siempre usar el esquema de color claro

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}