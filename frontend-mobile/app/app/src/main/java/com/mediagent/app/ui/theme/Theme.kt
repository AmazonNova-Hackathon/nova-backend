package com.mediagent.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    secondary = BrandForest,
    tertiary = BrandPurpleMid,
    background = SurfaceSecondary,
    surface = SurfacePrimary,
    onPrimary = SurfacePrimary,
    onSecondary = SurfacePrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    secondary = BrandForest,
    tertiary = BrandPurpleMid,
    background = SurfaceSecondary,
    surface = SurfacePrimary,
    onPrimary = SurfacePrimary,
    onSecondary = SurfacePrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun MediAgentTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}