package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MeteoroColorScheme = lightColorScheme(
    primary = MeteoroOrange,
    onPrimary = Color.White,
    primaryContainer = MeteoroOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = MeteoroBlue,
    onSecondary = Color.White,
    secondaryContainer = MeteoroSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = MeteoroCyan,
    onTertiary = Color.Black,
    background = MeteoroBackground,
    onBackground = TextPrimary,
    surface = MeteoroSurface,
    onSurface = TextPrimary,
    surfaceVariant = MeteoroSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = MeteoroBorder,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MeteoroTheme(
    content: @Composable () -> Unit
) {
    // Meteoro uses the same light background as its logo.
    MaterialTheme(
        colorScheme = MeteoroColorScheme,
        typography = Typography,
        content = content
    )
}
