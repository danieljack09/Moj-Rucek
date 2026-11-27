package com.mojrucek.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii from the spec: small badges 8dp, cards and image wells 20dp (large),
 * dialogs 28dp (extraLarge). Pills and sheets use [PillShape] and [SheetShape].
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Fully rounded: buttons, search, text fields, chips, list rows, nav bar, meta pills. */
val PillShape = RoundedCornerShape(50)

/** Bottom sheets: 28dp top corners, square bottom. */
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
