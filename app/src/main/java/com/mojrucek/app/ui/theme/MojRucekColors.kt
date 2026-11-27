package com.mojrucek.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Spec colour tokens that have no Material 3 colour role, plus the container names the
 * current screens already use. Light and dark instances below; read them through
 * [MojRucekTheme.colors].
 *
 * Text on green, greenContainer, card and background uses colorScheme.onSurface (or
 * onSecondary on green); icons on darkOlive use [onDarkOlive].
 */
@Immutable
data class MojRucekColors(
    /** Home week strip background (greenContainer). */
    val weekStripContainer: Color,
    /** A week day with a planned meal (card). */
    val plannedDayContainer: Color,
    /** A week day without a meal (background). */
    val emptyDayContainer: Color,
    /** Today in the week strip (green). */
    val todayContainer: Color,
    /** Weekly reminder card (greenContainer). */
    val reminderContainer: Color,
    /** Text and icons laid over a photo with the dark scrim. Same in both modes. */
    val onImage: Color,
    /** Disabled primary button, error field fill. */
    val accentSoft: Color,
    /** Promo cards, selected single-choice chips, cooking-step chips. */
    val green: Color,
    /** Search, text fields, chips, list rows, nav bar pill, circle buttons. */
    val greenContainer: Color,
    /** Recipe image wells, section cards. */
    val card: Color,
    /** Active nav circle, selected outline, strong accents. */
    val darkOlive: Color,
    /** Icon on the active nav circle (on darkOlive). */
    val onDarkOlive: Color,
    /** Thin dividers inside cards. */
    val divider: Color,
    /**
     * Line icons on a [green] fill (category circles, recipe fallback, avatar). darkOlive in light
     * mode; in dark mode darkOlive turns light and only reaches 2.1:1 on green, so it uses onGreen
     * (5.9:1) instead.
     */
    val iconOnGreen: Color
)

val LightMojRucekColors = MojRucekColors(
    weekStripContainer = LightPalette.GreenContainer,
    plannedDayContainer = LightPalette.Card,
    emptyDayContainer = LightPalette.Background,
    todayContainer = LightPalette.Green,
    reminderContainer = LightPalette.GreenContainer,
    onImage = Color(0xFFF7F5F1),
    accentSoft = LightPalette.AccentSoft,
    green = LightPalette.Green,
    greenContainer = LightPalette.GreenContainer,
    card = LightPalette.Card,
    darkOlive = LightPalette.DarkOlive,
    onDarkOlive = LightPalette.OnDarkOlive,
    divider = LightPalette.Divider,
    iconOnGreen = LightPalette.DarkOlive
)

val DarkMojRucekColors = MojRucekColors(
    weekStripContainer = DarkPalette.GreenContainer,
    plannedDayContainer = DarkPalette.Card,
    emptyDayContainer = DarkPalette.Background,
    todayContainer = DarkPalette.Green,
    reminderContainer = DarkPalette.GreenContainer,
    onImage = Color(0xFFF7F5F1),
    accentSoft = DarkPalette.AccentSoft,
    green = DarkPalette.Green,
    greenContainer = DarkPalette.GreenContainer,
    card = DarkPalette.Card,
    darkOlive = DarkPalette.DarkOlive,
    onDarkOlive = DarkPalette.OnDarkOlive,
    divider = DarkPalette.Divider,
    iconOnGreen = DarkPalette.OnGreen
)

val LocalMojRucekColors = staticCompositionLocalOf { LightMojRucekColors }
