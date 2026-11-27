package com.mojrucek.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.unit.UnitConverter
import com.mojrucek.app.domain.text.MkPlural

fun formatQuantityWithUnit(quantity: Double, unit: MeasurementUnit): String =
    "${UnitConverter.formatQuantity(quantity)} ${MkPlural.unitLabel(quantity, unit)}"

/**
 * A horizontally scrollable row of category chips (line icon + Macedonian name),
 * with an optional "Сите" (all) option and optional per-category counts.
 * Single choice, so the selected chip is filled green ([AppChip], spec item 6).
 * [contentPadding] insets the chips inside the scrolling row, so the row can run
 * edge to edge while the first chip lines up with the screen content.
 * Shared by the ingredient list's category filter and the add/edit ingredient form.
 */
@Composable
fun CategoryChipsRow(
    categories: List<IngredientCategory>,
    selected: IngredientCategory?,
    onSelect: (IngredientCategory?) -> Unit,
    modifier: Modifier = Modifier,
    showAllOption: Boolean = true,
    counts: Map<IngredientCategory, Int> = emptyMap(),
    totalCount: Int? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    ChipRow(modifier = modifier, contentPadding = contentPadding) {
        if (showAllOption) {
            AppChip(
                label = if (totalCount != null) "Сите ($totalCount)" else "Сите",
                selected = selected == null,
                onClick = { onSelect(null) },
                selectedStyle = ChipSelectionStyle.Filled
            )
        }
        categories.forEach { category ->
            val count = counts[category]
            AppChip(
                label = if (count != null) "${category.displayName} ($count)" else category.displayName,
                selected = selected == category,
                onClick = { onSelect(category) },
                selectedStyle = ChipSelectionStyle.Filled,
                // Line icon in place of the category emoji (spec item 14); the emoji stays in the data.
                leadingIcon = category.icon()
            )
        }
    }
}
