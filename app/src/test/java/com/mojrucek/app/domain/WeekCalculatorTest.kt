package com.mojrucek.app.domain

import com.mojrucek.app.domain.usecase.WeekCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekCalculatorTest {

    @Test
    fun `mondayOf returns the same date when already Monday`() {
        val monday = LocalDate.of(2026, 10, 26) // a Monday
        assertEquals(monday, WeekCalculator.mondayOf(monday))
    }

    @Test
    fun `mondayOf returns the Monday of the current week for any weekday`() {
        val wednesday = LocalDate.of(2026, 10, 28)
        assertEquals(LocalDate.of(2026, 10, 26), WeekCalculator.mondayOf(wednesday))

        val sunday = LocalDate.of(2026, 11, 1)
        assertEquals(LocalDate.of(2026, 10, 26), WeekCalculator.mondayOf(sunday))
    }

    @Test
    fun `nextWeekMonday is exactly seven days after this week's Monday, from any day of the week`() {
        val monday = LocalDate.of(2026, 10, 26)
        val sunday = LocalDate.of(2026, 11, 1)
        val expectedNextMonday = LocalDate.of(2026, 11, 2)

        assertEquals(expectedNextMonday, WeekCalculator.nextWeekMonday(monday))
        assertEquals(expectedNextMonday, WeekCalculator.nextWeekMonday(sunday))
    }

    @Test
    fun `isSunday correctly identifies Sunday and nothing else`() {
        assertTrue(WeekCalculator.isSunday(LocalDate.of(2026, 11, 1)))
        assertFalse(WeekCalculator.isSunday(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `datesOfWeek returns seven consecutive days starting Monday`() {
        val monday = LocalDate.of(2026, 10, 26)
        val week = WeekCalculator.datesOfWeek(monday)

        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 1), week.last())
    }
}
