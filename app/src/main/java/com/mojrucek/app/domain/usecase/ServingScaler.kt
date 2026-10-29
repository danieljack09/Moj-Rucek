package com.mojrucek.app.domain.usecase

import com.mojrucek.app.domain.model.IngredientLine

/**
 * Scales a recipe's ingredient quantities from its base serving count to a
 * desired serving count. Pure linear scaling, which is "mathematically
 * appropriate" for quantities (spec §7) — it does not attempt to round
 * whole-item ingredients (e.g. "1 egg") to a realistic whole number, since
 * that would require ingredient-specific knowledge the app doesn't have;
 * the UI shows the exact scaled figure and the user applies judgement.
 */
object ServingScaler {

    fun scale(lines: List<IngredientLine>, baseServings: Int, desiredServings: Int): List<IngredientLine> {
        require(baseServings > 0) { "baseServings must be positive" }
        require(desiredServings >= 0) { "desiredServings must not be negative" }
        if (baseServings == desiredServings) return lines

        val factor = desiredServings.toDouble() / baseServings.toDouble()
        return lines.map { it.copy(quantity = it.quantity * factor) }
    }
}
