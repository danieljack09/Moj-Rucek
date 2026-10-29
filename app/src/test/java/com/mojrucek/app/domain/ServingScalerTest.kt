package com.mojrucek.app.domain

import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.ServingScaler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServingScalerTest {

    @Test
    fun `doubling servings doubles every quantity`() {
        val lines = listOf(
            IngredientLine("пилешко месо", "пилешко месо", 500.0, MeasurementUnit.GRAM),
            IngredientLine("ориз", "ориз", 200.0, MeasurementUnit.GRAM)
        )
        val scaled = ServingScaler.scale(lines, baseServings = 4, desiredServings = 8)

        assertEquals(1000.0, scaled[0].quantity, 0.0001)
        assertEquals(400.0, scaled[1].quantity, 0.0001)
    }

    @Test
    fun `scaling down to one third of servings divides quantities`() {
        val lines = listOf(IngredientLine("ориз", "ориз", 300.0, MeasurementUnit.GRAM))
        val scaled = ServingScaler.scale(lines, baseServings = 3, desiredServings = 1)

        assertEquals(100.0, scaled[0].quantity, 0.0001)
    }

    @Test
    fun `same serving count returns equivalent quantities unchanged`() {
        val lines = listOf(IngredientLine("ориз", "ориз", 300.0, MeasurementUnit.GRAM))
        val scaled = ServingScaler.scale(lines, baseServings = 4, desiredServings = 4)

        assertEquals(300.0, scaled[0].quantity, 0.0001)
    }

    @Test
    fun `zero base servings is rejected`() {
        val lines = listOf(IngredientLine("ориз", "ориз", 300.0, MeasurementUnit.GRAM))
        assertThrows(IllegalArgumentException::class.java) {
            ServingScaler.scale(lines, baseServings = 0, desiredServings = 4)
        }
    }
}
