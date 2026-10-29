package com.mojrucek.app.domain.usecase

import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.unit.UnitConverter

/**
 * Decides what happens when an ingredient with a given quantity/unit is
 * added while one with the same (normalized) name already exists in the
 * inventory — used by the "offer to update quantity instead of duplicating"
 * rule in spec §5, and by shopping-list "combine duplicate items" (§6).
 */
object IngredientMergeUseCase {

    sealed interface Result {
        /** Combine into a single entry at [combinedQuantity], expressed in the existing entry's unit. */
        data class Merged(val combinedQuantity: Double, val unit: MeasurementUnit) : Result

        /** Units cannot be reliably combined (e.g. 2 парче vs 300 g) — must stay as separate entries. */
        data object UnitMismatch : Result
    }

    fun mergeQuantity(
        existingQuantity: Double,
        existingUnit: MeasurementUnit,
        incomingQuantity: Double,
        incomingUnit: MeasurementUnit
    ): Result {
        val incomingInExistingUnit =
            UnitConverter.convert(incomingQuantity, incomingUnit, existingUnit) ?: return Result.UnitMismatch
        return Result.Merged(existingQuantity + incomingInExistingUnit, existingUnit)
    }
}
