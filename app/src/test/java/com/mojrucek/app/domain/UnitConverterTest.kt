package com.mojrucek.app.domain

import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.unit.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UnitConverterTest {

    @Test
    fun `grams and kilograms are compatible and convert correctly`() {
        assertTrue(UnitConverter.areCompatible(MeasurementUnit.GRAM, MeasurementUnit.KILOGRAM))
        assertEquals(500.0, UnitConverter.convert(0.5, MeasurementUnit.KILOGRAM, MeasurementUnit.GRAM)!!, 0.0001)
        assertEquals(1.0, UnitConverter.convert(1000.0, MeasurementUnit.GRAM, MeasurementUnit.KILOGRAM)!!, 0.0001)
    }

    @Test
    fun `milliliters and liters are compatible and convert correctly`() {
        assertEquals(1500.0, UnitConverter.convert(1.5, MeasurementUnit.LITER, MeasurementUnit.MILLILITER)!!, 0.0001)
    }

    @Test
    fun `same count unit is compatible with itself`() {
        assertTrue(UnitConverter.areCompatible(MeasurementUnit.PIECE, MeasurementUnit.PIECE))
        assertEquals(3.0, UnitConverter.convert(3.0, MeasurementUnit.PIECE, MeasurementUnit.PIECE)!!, 0.0001)
    }

    @Test
    fun `different count units are never compatible`() {
        assertFalse(UnitConverter.areCompatible(MeasurementUnit.PIECE, MeasurementUnit.PACKAGE))
        assertNull(UnitConverter.convert(2.0, MeasurementUnit.PIECE, MeasurementUnit.PACKAGE))
    }

    @Test
    fun `weight is never compatible with volume or count`() {
        assertFalse(UnitConverter.areCompatible(MeasurementUnit.GRAM, MeasurementUnit.MILLILITER))
        assertFalse(UnitConverter.areCompatible(MeasurementUnit.GRAM, MeasurementUnit.PIECE))
        assertNull(UnitConverter.convert(100.0, MeasurementUnit.GRAM, MeasurementUnit.PIECE))
    }

    @Test
    fun `toBase expresses weight in grams and volume in milliliters`() {
        assertEquals(2000.0, UnitConverter.toBase(2.0, MeasurementUnit.KILOGRAM), 0.0001)
        assertEquals(250.0, UnitConverter.toBase(0.25, MeasurementUnit.LITER), 0.0001)
        assertEquals(4.0, UnitConverter.toBase(4.0, MeasurementUnit.PIECE), 0.0001)
    }

    @Test
    fun `formatQuantity trims trailing zeros`() {
        assertEquals("3", UnitConverter.formatQuantity(3.0))
        assertEquals("2.5", UnitConverter.formatQuantity(2.5))
        assertEquals("0.33", UnitConverter.formatQuantity(1.0 / 3.0))
    }
}
