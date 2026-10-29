package com.mojrucek.app.domain

import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientMergeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientMergeUseCaseTest {

    @Test
    fun `merging compatible units combines into the existing unit`() {
        val result = IngredientMergeUseCase.mergeQuantity(
            existingQuantity = 200.0, existingUnit = MeasurementUnit.GRAM,
            incomingQuantity = 0.3, incomingUnit = MeasurementUnit.KILOGRAM
        )
        assertTrue(result is IngredientMergeUseCase.Result.Merged)
        result as IngredientMergeUseCase.Result.Merged
        assertEquals(500.0, result.combinedQuantity, 0.0001)
        assertEquals(MeasurementUnit.GRAM, result.unit)
    }

    @Test
    fun `merging incompatible units is refused rather than guessed`() {
        val result = IngredientMergeUseCase.mergeQuantity(
            existingQuantity = 2.0, existingUnit = MeasurementUnit.PIECE,
            incomingQuantity = 300.0, incomingUnit = MeasurementUnit.GRAM
        )
        assertEquals(IngredientMergeUseCase.Result.UnitMismatch, result)
    }

    @Test
    fun `merging same count unit adds plainly`() {
        val result = IngredientMergeUseCase.mergeQuantity(
            existingQuantity = 2.0, existingUnit = MeasurementUnit.PIECE,
            incomingQuantity = 3.0, incomingUnit = MeasurementUnit.PIECE
        )
        assertTrue(result is IngredientMergeUseCase.Result.Merged)
        assertEquals(5.0, (result as IngredientMergeUseCase.Result.Merged).combinedQuantity, 0.0001)
    }
}
