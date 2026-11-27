package com.mojrucek.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mojrucek.app.R

/**
 * Inter (SIL OFL, licenses/Inter-OFL.txt), static Regular / Medium / SemiBold
 * from the official v4.1 release. All three include full Cyrillic, so
 * Macedonian text (Ѓ Ѕ Ј Љ Њ Ќ Џ included) renders in Inter.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

/** Spec: -0.2sp tracking on 22sp and above, 0 elsewhere. */
private fun interStyle(size: Int, lineHeight: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = if (size >= 22) (-0.2).sp else 0.sp
)

/**
 * Type scale from docs/redesign/design-spec.md. Titles are Medium, never Bold.
 * The spec's table has 11 roles; displayMedium, displaySmall, headlineSmall and
 * titleSmall are not in it and are set between their neighbours.
 */
val AppTypography = Typography(
    displayLarge = interStyle(44, 50, FontWeight.Medium),
    displayMedium = interStyle(36, 42, FontWeight.Medium),
    displaySmall = interStyle(32, 38, FontWeight.Medium),
    headlineLarge = interStyle(28, 34, FontWeight.Medium),
    headlineMedium = interStyle(24, 30, FontWeight.Medium),
    headlineSmall = interStyle(22, 28, FontWeight.Medium),
    titleLarge = interStyle(22, 28, FontWeight.Medium),
    titleMedium = interStyle(17, 22, FontWeight.Medium),
    titleSmall = interStyle(15, 20, FontWeight.Medium),
    bodyLarge = interStyle(17, 24, FontWeight.Normal),
    bodyMedium = interStyle(15, 21, FontWeight.Normal),
    bodySmall = interStyle(13, 18, FontWeight.Normal),
    labelLarge = interStyle(16, 20, FontWeight.Medium),
    labelMedium = interStyle(13, 16, FontWeight.Medium),
    labelSmall = interStyle(11, 14, FontWeight.Medium)
)
