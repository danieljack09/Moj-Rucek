package com.mojrucek.app.domain.usecase

import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.IngredientMatch
import com.mojrucek.app.domain.model.MatchStatus
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.domain.unit.UnitConverter

/**
 * Compares a recipe's required ingredients against the home inventory.
 * Pure function, no Android/Room dependency, so it is directly unit-testable.
 */
object RecipeMatchingUseCase {

    fun match(
        recipeId: Long,
        recipeName: String,
        required: List<IngredientLine>,
        available: List<IngredientLine>,
        isFavorite: Boolean = false
    ): RecipeMatchResult {
        val availableByName = available.associateBy { it.normalizedName }

        val matches = required.map { requirement ->
            val onHand = availableByName[requirement.normalizedName]
            when {
                onHand == null -> IngredientMatch(
                    name = requirement.name,
                    normalizedName = requirement.normalizedName,
                    requiredQuantity = requirement.quantity,
                    requiredUnit = requirement.unit,
                    availableQuantity = 0.0,
                    status = MatchStatus.MISSING
                )

                !UnitConverter.areCompatible(onHand.unit, requirement.unit) -> IngredientMatch(
                    name = requirement.name,
                    normalizedName = requirement.normalizedName,
                    requiredQuantity = requirement.quantity,
                    requiredUnit = requirement.unit,
                    availableQuantity = 0.0,
                    status = MatchStatus.UNIT_MISMATCH
                )

                else -> {
                    val availableInRequiredUnit =
                        UnitConverter.convert(onHand.quantity, onHand.unit, requirement.unit) ?: 0.0
                    val status = if (availableInRequiredUnit >= requirement.quantity) {
                        MatchStatus.AVAILABLE
                    } else {
                        MatchStatus.INSUFFICIENT
                    }
                    IngredientMatch(
                        name = requirement.name,
                        normalizedName = requirement.normalizedName,
                        requiredQuantity = requirement.quantity,
                        requiredUnit = requirement.unit,
                        availableQuantity = availableInRequiredUnit,
                        status = status
                    )
                }
            }
        }

        return RecipeMatchResult(
            recipeId = recipeId,
            recipeName = recipeName,
            matches = matches,
            isFavorite = isFavorite
        )
    }
}
