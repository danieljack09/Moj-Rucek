package com.mojrucek.app.domain.text

import com.mojrucek.app.domain.unit.MeasurementUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class MkPluralTest {

    @Test
    fun singularOnlyForNumbersEndingInOneButNotEleven() {
        for (n in listOf(1, 21, 31, 101, 121)) assertEquals("$n намирница", MkPlural.ingredients(n))
        for (n in listOf(0, 2, 3, 5, 11, 12, 111, 20)) assertEquals("$n намирници", MkPlural.ingredients(n))
    }

    @Test
    fun masculineNounsUseCountForm() {
        assertEquals("1 ден", MkPlural.days(1))
        assertEquals("3 дена", MkPlural.days(3))
        assertEquals("1 рецепт", MkPlural.recipes(1))
        assertEquals("4 рецепти", MkPlural.recipes(4))
    }

    @Test
    fun countUnitsAgreeWithAmount() {
        assertEquals("парче", MkPlural.unitLabel(1.0, MeasurementUnit.PIECE))
        assertEquals("парчиња", MkPlural.unitLabel(2.0, MeasurementUnit.PIECE))
        assertEquals("парчиња", MkPlural.unitLabel(3.0, MeasurementUnit.PIECE))
        assertEquals("парчиња", MkPlural.unitLabel(1.5, MeasurementUnit.PIECE))
        assertEquals("пакувања", MkPlural.unitLabel(2.0, MeasurementUnit.PACKAGE))
        assertEquals("конзерва", MkPlural.unitLabel(1.0, MeasurementUnit.CAN))
        assertEquals("конзерви", MkPlural.unitLabel(5.0, MeasurementUnit.CAN))
        assertEquals("g", MkPlural.unitLabel(300.0, MeasurementUnit.GRAM))
    }
}
