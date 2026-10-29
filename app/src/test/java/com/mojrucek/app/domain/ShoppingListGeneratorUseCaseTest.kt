package com.mojrucek.app.domain

import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.ShoppingListGeneratorUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListGeneratorUseCaseTest {

    private fun line(name: String, qty: Double, unit: MeasurementUnit) =
        IngredientLine(name, name.lowercase(), qty, unit)

    @Test
    fun `spec example - chicken, onion need buying, potatoes fully covered`() {
        val weekly = listOf(
            line("chicken", 1200.0, MeasurementUnit.GRAM),
            line("potatoes", 800.0, MeasurementUnit.GRAM),
            line("onions", 500.0, MeasurementUnit.GRAM)
        )
        val inventory = listOf(
            line("chicken", 500.0, MeasurementUnit.GRAM),
            line("potatoes", 1.0, MeasurementUnit.KILOGRAM),
            line("onions", 200.0, MeasurementUnit.GRAM)
        )

        val result = ShoppingListGeneratorUseCase.generate(weekly, inventory)

        assertTrue(result.conflicts.isEmpty())
        assertNull(result.items.find { it.normalizedName == "potatoes" })

        val chicken = result.items.first { it.normalizedName == "chicken" }
        assertEquals(700.0, chicken.quantity, 0.001)
        assertEquals(MeasurementUnit.GRAM, chicken.unit)

        val onions = result.items.first { it.normalizedName == "onions" }
        assertEquals(300.0, onions.quantity, 0.001)
    }

    @Test
    fun `requirements across multiple recipes for the same ingredient are combined`() {
        val weekly = listOf(
            line("ориз", 200.0, MeasurementUnit.GRAM),
            line("ориз", 150.0, MeasurementUnit.GRAM)
        )
        val result = ShoppingListGeneratorUseCase.generate(weekly, emptyList())

        assertEquals(1, result.items.size)
        assertEquals(350.0, result.items.single().quantity, 0.001)
    }

    @Test
    fun `large gram totals are shown in kilograms`() {
        val weekly = listOf(line("компир", 2500.0, MeasurementUnit.GRAM))
        val result = ShoppingListGeneratorUseCase.generate(weekly, emptyList())

        val item = result.items.single()
        assertEquals(MeasurementUnit.KILOGRAM, item.unit)
        assertEquals(2.5, item.quantity, 0.001)
    }

    @Test
    fun `incompatible units raise a conflict instead of guessing a quantity`() {
        val weekly = listOf(
            line("кромид", 2.0, MeasurementUnit.PIECE),
            line("кромид", 300.0, MeasurementUnit.GRAM)
        )
        val result = ShoppingListGeneratorUseCase.generate(weekly, emptyList())

        assertTrue(result.items.isEmpty())
        assertEquals(1, result.conflicts.size)
        assertEquals("кромид", result.conflicts.single().normalizedName)
    }

    @Test
    fun `fully covered ingredient is left off the list entirely`() {
        val weekly = listOf(line("масло", 100.0, MeasurementUnit.MILLILITER))
        val inventory = listOf(line("масло", 200.0, MeasurementUnit.MILLILITER))
        val result = ShoppingListGeneratorUseCase.generate(weekly, inventory)

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `generation never mutates its inputs (pure calculation)`() {
        val weekly = listOf(line("ориз", 200.0, MeasurementUnit.GRAM))
        val inventory = listOf(line("ориз", 50.0, MeasurementUnit.GRAM))
        val inventorySnapshot = inventory.map { it.copy() }

        ShoppingListGeneratorUseCase.generate(weekly, inventory)

        assertEquals(inventorySnapshot, inventory)
    }

    @Test
    fun `category is taken from inventory when present, otherwise defaults`() {
        val weekly = listOf(line("ориз", 500.0, MeasurementUnit.GRAM))
        val categories = mapOf("ориз" to IngredientCategory.ZITARICI_I_TESTENINI)
        val result = ShoppingListGeneratorUseCase.generate(weekly, emptyList(), categories)

        assertEquals(IngredientCategory.ZITARICI_I_TESTENINI, result.items.single().category)
    }
}
