package com.mojrucek.app.domain.usecase

import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.model.GeneratedShoppingItem
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.ShoppingListGenerationResult
import com.mojrucek.app.domain.model.UnitConflict
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.unit.UnitConverter
import com.mojrucek.app.domain.unit.UnitGroup

/**
 * Turns "everything the planned week's recipes require" plus "what's
 * already at home" into a net shopping list (spec §9-§10):
 *
 * 1. Required quantities for the same ingredient (across different days/
 *    recipes) are combined.
 * 2. The combined requirement is reduced by whatever is already in the
 *    inventory.
 * 3. An ingredient that is fully covered by inventory is left off the list
 *    entirely (the "potatoes" example in the spec).
 * 4. Ingredients whose required or available quantities use incompatible
 *    units (e.g. 2 парче vs 300 g of "кромид") are never guessed at — they
 *    come back as [UnitConflict]s for the UI to ask the user about.
 *
 * This function never reads or writes the inventory itself; it only
 * computes, so generating a list can never change inventory quantities
 * (spec §9, explicitly required).
 */
object ShoppingListGeneratorUseCase {

    fun generate(
        weeklyRequirements: List<IngredientLine>,
        inventory: List<IngredientLine>,
        categoryByNormalizedName: Map<String, IngredientCategory> = emptyMap()
    ): ShoppingListGenerationResult {
        val requiredByName = weeklyRequirements.groupBy { it.normalizedName }
        val availableByName = inventory.groupBy { it.normalizedName }

        val items = mutableListOf<GeneratedShoppingItem>()
        val conflicts = mutableListOf<UnitConflict>()

        for ((normalizedName, requirementLines) in requiredByName) {
            val requiredUnits = requirementLines.map { it.unit }.distinct()
            val availableLines = availableByName[normalizedName].orEmpty()
            val availableUnits = availableLines.map { it.unit }.distinct()
            val allUnitsInvolved = (requiredUnits + availableUnits).distinct()

            val allCompatible = allUnitsInvolved.zipWithNext().all { (a, b) -> UnitConverter.areCompatible(a, b) } ||
                allUnitsInvolved.size <= 1

            if (!allCompatible) {
                conflicts += UnitConflict(
                    name = requirementLines.first().name,
                    normalizedName = normalizedName,
                    conflictingUnits = allUnitsInvolved
                )
                continue
            }

            val group = allUnitsInvolved.first().group
            val baseUnit = UnitConverter.baseUnitFor(group, allUnitsInvolved.first())

            val totalRequiredInBase = requirementLines.sumOf { UnitConverter.toBase(it.quantity, it.unit) }
            val totalAvailableInBase = availableLines.sumOf { UnitConverter.toBase(it.quantity, it.unit) }

            val neededInBase = (totalRequiredInBase - totalAvailableInBase).coerceAtLeast(0.0)

            // Fully covered by what's already at home: leave it off the list.
            if (neededInBase <= 0.0001) continue

            val (displayQuantity, displayUnit) = toDisplayUnit(neededInBase, group, baseUnit)

            items += GeneratedShoppingItem(
                name = requirementLines.first().name,
                normalizedName = normalizedName,
                quantity = displayQuantity,
                unit = displayUnit,
                category = categoryByNormalizedName[normalizedName] ?: IngredientCategory.DRUGO
            )
        }

        return ShoppingListGenerationResult(items = items.sortedBy { it.name }, conflicts = conflicts)
    }

    /** Picks a friendlier unit for display: kg/l once the amount reaches 1000 of the base unit. */
    private fun toDisplayUnit(
        amountInBase: Double,
        group: UnitGroup,
        countUnit: MeasurementUnit
    ): Pair<Double, MeasurementUnit> = when (group) {
        UnitGroup.WEIGHT ->
            if (amountInBase >= 1000.0) (amountInBase / 1000.0) to MeasurementUnit.KILOGRAM
            else amountInBase to MeasurementUnit.GRAM

        UnitGroup.VOLUME ->
            if (amountInBase >= 1000.0) (amountInBase / 1000.0) to MeasurementUnit.LITER
            else amountInBase to MeasurementUnit.MILLILITER

        UnitGroup.COUNT -> amountInBase to countUnit
    }
}
