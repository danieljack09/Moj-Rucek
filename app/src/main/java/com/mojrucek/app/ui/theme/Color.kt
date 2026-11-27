package com.mojrucek.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens from docs/redesign/design-spec.md ("Colour tokens"), one object per mode.
 * Both objects use the spec's token names, so `LightPalette.Accent` and `DarkPalette.Accent`
 * are the light and dark values of the same token. They are wrapped in objects so names such
 * as `Text`, `Card` and `Error` never clash with Compose's `Text` or Kotlin's `Error`.
 *
 * Screens should not read these directly: use MaterialTheme.colorScheme for tokens with a
 * Material role, and MojRucekTheme.colors for the rest.
 */
object LightPalette {
    val Accent = Color(0xFFF16F33)
    val AccentSoft = Color(0xFFF7B899)
    val OnAccent = Color(0xFFFFFFFF)
    val Green = Color(0xFFACBD6F)
    val OnGreen = Color(0xFF1B1B1B)
    val GreenContainer = Color(0xFFEAECCA)
    val Background = Color(0xFFFFFAE7)
    val Card = Color(0xFFE3E5C4)
    val DarkOlive = Color(0xFF434724)
    val OnDarkOlive = Color(0xFFFFFAE7)
    val Text = Color(0xFF1B1B1B)
    val TextSecondary = Color(0xFF6E6E5A)
    val Divider = Color(0xFFD9DBB6)
    val Error = Color(0xFFD9481C)
}

object DarkPalette {
    val Accent = Color(0xFFF2834F)
    val AccentSoft = Color(0xFF7A4329)
    val OnAccent = Color(0xFF1C1D12)
    val Green = Color(0xFF8E9F55)
    val OnGreen = Color(0xFF1C1D12)
    val GreenContainer = Color(0xFF33361F)
    val Background = Color(0xFF1C1D12)
    val Card = Color(0xFF2A2C1C)
    val DarkOlive = Color(0xFFDDE0B8)
    val OnDarkOlive = Color(0xFF1C1D12)
    val Text = Color(0xFFF4F1DE)
    val TextSecondary = Color(0xFFB5B39A)
    val Divider = Color(0xFF3D402A)
    val Error = Color(0xFFFF8A65)
}
