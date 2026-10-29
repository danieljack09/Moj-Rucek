package com.mojrucek.app.domain

import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.MatchStatus
import com.mojrucek.app.domain.model.Readiness
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.RecipeMatchingUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMatchingUseCaseTest {

    private fun line(name: String, qty: Double, unit: MeasurementUnit) =
        IngredientLine(name, name.lowercase(), qty, unit)

    @Test
    fun `recipe with everything available can be cooked now`() {
        val required = listOf(
            line("пилешко месо", 500.0, MeasurementUnit.GRAM),
            line("ориз", 200.0, MeasurementUnit.GRAM)
        )
        val available = listOf(
            line("пилешко месо", 1.0, MeasurementUnit.KILOGRAM),
            line("ориз", 300.0, MeasurementUnit.GRAM)
        )
        val result = RecipeMatchingUseCase.match(1, "Пилешко со ориз", required, available)

        assertTrue(result.canCookNow)
        assertEquals(2, result.coveredIngredients)
        assertEquals(Readiness.CAN_COOK_NOW, result.readiness)
    }

    @Test
    fun `matches the spec example - missing kromid and morkov`() {
        val required = listOf(
            line("пилешко месо", 1.0, MeasurementUnit.PIECE),
            line("ориз", 1.0, MeasurementUnit.PACKAGE),
            line("кромид", 1.0, MeasurementUnit.PIECE),
            line("морков", 1.0, MeasurementUnit.PIECE),
            line("масло", 1.0, MeasurementUnit.PIECE)
        )
        val available = listOf(
            line("пилешко месо", 1.0, MeasurementUnit.PIECE),
            line("ориз", 1.0, MeasurementUnit.PACKAGE),
            line("масло", 1.0, MeasurementUnit.PIECE)
        )
        val result = RecipeMatchingUseCase.match(1, "Пилешко со ориз", required, available)

        assertEquals(3, result.coveredIngredients)
        assertEquals(5, result.totalIngredients)
        assertEquals(2, result.missing.size)
        assertFalse(result.canCookNow)
    }

    @Test
    fun `insufficient quantity is reported, not treated as missing`() {
        val required = listOf(line("брашно", 500.0, MeasurementUnit.GRAM))
        val available = listOf(line("брашно", 200.0, MeasurementUnit.GRAM))
        val result = RecipeMatchingUseCase.match(1, "Леб", required, available)

        assertEquals(MatchStatus.INSUFFICIENT, result.matches.single().status)
        assertEquals(200.0, result.matches.single().availableQuantity, 0.0001)
    }

    @Test
    fun `unit mismatch is never silently treated as available`() {
        val required = listOf(line("кромид", 300.0, MeasurementUnit.GRAM))
        val available = listOf(line("кромид", 2.0, MeasurementUnit.PIECE))
        val result = RecipeMatchingUseCase.match(1, "Нешто", required, available)

        assertEquals(MatchStatus.UNIT_MISMATCH, result.matches.single().status)
        assertFalse(result.canCookNow)
    }

    @Test
    fun `missing ingredient is reported as missing with zero available`() {
        val required = listOf(line("лук", 2.0, MeasurementUnit.PIECE))
        val result = RecipeMatchingUseCase.match(1, "Нешто", required, emptyList())

        assertEquals(MatchStatus.MISSING, result.matches.single().status)
        assertEquals(0.0, result.matches.single().availableQuantity, 0.0001)
    }
}
