package com.mojrucek.app.domain.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * All weekly-plan date math happens here, anchored to Europe/Skopje, so the
 * rest of the app never has to reason about weeks itself and the current
 * week is never confused with the following one (spec §10).
 */
object WeekCalculator {

    val ZONE: ZoneId = ZoneId.of("Europe/Skopje")

    fun today(): LocalDate = LocalDate.now(ZONE)

    /** The Monday that starts the ISO week containing [date]. */
    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The Monday of the week immediately after the one containing [date]. */
    fun nextWeekMonday(date: LocalDate = today()): LocalDate = mondayOf(date).plusWeeks(1)

    fun isSunday(date: LocalDate = today()): Boolean = date.dayOfWeek == DayOfWeek.SUNDAY

    /** The seven consecutive dates of the week starting [weekStart] (must be a Monday). */
    fun datesOfWeek(weekStart: LocalDate): List<LocalDate> = (0..6).map { weekStart.plusDays(it.toLong()) }
}
