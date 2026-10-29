package com.mojrucek.app.domain.model

import com.mojrucek.app.domain.unit.MeasurementUnit

enum class MatchStatus { AVAILABLE, INSUFFICIENT, MISSING, UNIT_MISMATCH }

data class IngredientMatch(
    val name: String,
    val normalizedName: String,
    val requiredQuantity: Double,
    val requiredUnit: MeasurementUnit,
    /** Quantity on hand, expressed in [requiredUnit]. 0 when missing or unit mismatch. */
    val availableQuantity: Double,
    val status: MatchStatus
)

data class RecipeMatchResult(
    val recipeId: Long,
    val recipeName: String,
    val matches: List<IngredientMatch>,
    val isFavorite: Boolean = false
) {
    val totalIngredients: Int get() = matches.size
    val coveredIngredients: Int get() = matches.count { it.status == MatchStatus.AVAILABLE }
    val missing: List<IngredientMatch> get() = matches.filter { it.status != MatchStatus.AVAILABLE }
    val canCookNow: Boolean get() = matches.isNotEmpty() && matches.all { it.status == MatchStatus.AVAILABLE }
    val coveragePercent: Double
        get() = if (totalIngredients == 0) 0.0 else (coveredIngredients.toDouble() / totalIngredients) * 100.0

    /**
     * Readiness bucket used to power the four Рецепти filters. The 60%
     * threshold for "речиси подготвено" is a reasonable product default,
     * not something the spec pins to an exact number.
     */
    val readiness: Readiness
        get() = when {
            canCookNow -> Readiness.CAN_COOK_NOW
            coveragePercent >= 60.0 -> Readiness.ALMOST_READY
            else -> Readiness.NEEDS_SHOPPING
        }
}

enum class Readiness { CAN_COOK_NOW, ALMOST_READY, NEEDS_SHOPPING }
