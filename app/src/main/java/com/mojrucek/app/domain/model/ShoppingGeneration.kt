package com.mojrucek.app.domain.model

import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit

data class GeneratedShoppingItem(
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val unit: MeasurementUnit,
    val category: IngredientCategory
)

/**
 * Raised instead of silently guessing when the same ingredient is required
 * (or held in inventory) in units that cannot be reliably combined, e.g.
 * "кромид" needed as both 2 парче and 300 g. The caller must ask the user
 * to resolve it (spec §9).
 */
data class UnitConflict(
    val name: String,
    val normalizedName: String,
    val conflictingUnits: List<MeasurementUnit>
)

data class ShoppingListGenerationResult(
    val items: List<GeneratedShoppingItem>,
    val conflicts: List<UnitConflict>
)
