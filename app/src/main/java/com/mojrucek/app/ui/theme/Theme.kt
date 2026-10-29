package com.mojrucek.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = DeepGreen,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = OnSageContainer,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = SoftYellow,
    onSecondaryContainer = OnSoftYellow,
    tertiary = SageGreen,
    onTertiary = Color.White,
    background = CreamBackground,
    onBackground = WarmBrownText,
    surface = CardWhite,
    onSurface = WarmBrownText,
    surfaceVariant = BeigeSurfaceVariant,
    onSurfaceVariant = MutedWarmGray,
    outline = WarmOutline,
    outlineVariant = WarmOutline,
    error = MutedError,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = LightSage,
    onPrimary = Color(0xFF16311C),
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = OnDarkGreenContainer,
    secondary = LightTerracotta,
    onSecondary = Color(0xFF3F2311),
    secondaryContainer = DarkYellowContainer,
    onSecondaryContainer = OnDarkYellowContainer,
    tertiary = LightSage,
    onTertiary = Color(0xFF16311C),
    background = DarkBackground,
    onBackground = WarmLightText,
    surface = DarkCard,
    onSurface = WarmLightText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MutedLightGray,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = LightError,
    onError = Color(0xFF3F1208)
)

@Composable
fun MojRucekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // The app ships a fixed, designed warm palette (light by default) rather
    // than wallpaper-derived Material You colors, so dynamic color is never
    // used here.
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
