package com.mojrucek.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Material 3 roles from the spec's "Material 3 mapping". Every role is set so no M3 default leaks in. */
private val LightColors = lightColorScheme(
    primary = LightPalette.Accent,
    onPrimary = LightPalette.OnAccent,
    primaryContainer = LightPalette.AccentSoft,
    onPrimaryContainer = LightPalette.Text,
    inversePrimary = DarkPalette.Accent,
    secondary = LightPalette.Green,
    onSecondary = LightPalette.OnGreen,
    secondaryContainer = LightPalette.GreenContainer,
    onSecondaryContainer = LightPalette.Text,
    tertiary = LightPalette.DarkOlive,
    onTertiary = LightPalette.OnDarkOlive,
    tertiaryContainer = LightPalette.Card,
    onTertiaryContainer = LightPalette.Text,
    background = LightPalette.Background,
    onBackground = LightPalette.Text,
    surface = LightPalette.Background,
    onSurface = LightPalette.Text,
    surfaceVariant = LightPalette.GreenContainer,
    onSurfaceVariant = LightPalette.TextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = LightPalette.Text,
    inverseOnSurface = LightPalette.Background,
    error = LightPalette.Error,
    onError = LightPalette.OnAccent,
    errorContainer = LightPalette.AccentSoft,
    onErrorContainer = LightPalette.Text,
    outline = LightPalette.DarkOlive.copy(alpha = 0.4f),
    outlineVariant = LightPalette.Divider,
    scrim = Color.Black,
    surfaceBright = LightPalette.Background,
    surfaceDim = LightPalette.Card,
    surfaceContainerLowest = LightPalette.GreenContainer,
    surfaceContainerLow = LightPalette.GreenContainer,
    surfaceContainer = LightPalette.GreenContainer,
    surfaceContainerHigh = LightPalette.Card,
    surfaceContainerHighest = LightPalette.GreenContainer
)

private val DarkColors = darkColorScheme(
    primary = DarkPalette.Accent,
    onPrimary = DarkPalette.OnAccent,
    primaryContainer = DarkPalette.AccentSoft,
    onPrimaryContainer = DarkPalette.Text,
    inversePrimary = LightPalette.Accent,
    secondary = DarkPalette.Green,
    onSecondary = DarkPalette.OnGreen,
    secondaryContainer = DarkPalette.GreenContainer,
    onSecondaryContainer = DarkPalette.Text,
    tertiary = DarkPalette.DarkOlive,
    onTertiary = DarkPalette.OnDarkOlive,
    tertiaryContainer = DarkPalette.Card,
    onTertiaryContainer = DarkPalette.Text,
    background = DarkPalette.Background,
    onBackground = DarkPalette.Text,
    surface = DarkPalette.Background,
    onSurface = DarkPalette.Text,
    surfaceVariant = DarkPalette.GreenContainer,
    onSurfaceVariant = DarkPalette.TextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkPalette.Text,
    inverseOnSurface = DarkPalette.Background,
    error = DarkPalette.Error,
    onError = DarkPalette.OnAccent,
    errorContainer = DarkPalette.AccentSoft,
    onErrorContainer = DarkPalette.Text,
    outline = DarkPalette.DarkOlive.copy(alpha = 0.4f),
    outlineVariant = DarkPalette.Divider,
    scrim = Color.Black,
    surfaceBright = DarkPalette.Card,
    surfaceDim = DarkPalette.Background,
    surfaceContainerLowest = DarkPalette.GreenContainer,
    surfaceContainerLow = DarkPalette.GreenContainer,
    surfaceContainer = DarkPalette.GreenContainer,
    surfaceContainerHigh = DarkPalette.Card,
    surfaceContainerHighest = DarkPalette.GreenContainer
)

@Composable
fun MojRucekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // A fixed, designed palette: dynamic (wallpaper) colour is never used.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val appColors = if (darkTheme) DarkMojRucekColors else LightMojRucekColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: the app draws behind transparent status and navigation bars,
            // and Scaffolds/TopAppBars/NavigationBar pad themselves with the system insets.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Otherwise Android adds a translucent scrim behind 3-button navigation.
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMojRucekColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/** App colours with no Material role, for the current light/dark mode. */
object MojRucekTheme {
    val colors: MojRucekColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMojRucekColors.current
}
