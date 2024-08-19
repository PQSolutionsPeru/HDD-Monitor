package com.pqsolutions.hdd_monitor.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = HddRed,
    secondary = HddGrey,
    tertiary = HddDarkRed,
    background = HddWhite,
    surface = HddLightGrey,
    onPrimary = HddWhite,
    onSecondary = HddWhite,
    onTertiary = HddWhite,
    onBackground = HddBlack,
    onSurface = HddBlack
)

private val DarkColorScheme = darkColorScheme(
    primary = HddRed,
    secondary = HddGrey,
    tertiary = HddDarkRed,
    background = HddGrey,
    surface = HddBlack,
    onPrimary = HddWhite,
    onSecondary = HddWhite,
    onTertiary = HddWhite,
    onBackground = HddWhite,
    onSurface = HddWhite
)

@Composable
fun HddMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}