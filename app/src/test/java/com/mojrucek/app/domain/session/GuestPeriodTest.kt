package com.mojrucek.app.domain.session

import com.mojrucek.app.domain.session.GuestPeriod.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class GuestPeriodTest {

    private val skopje = ZoneId.of("Europe/Skopje")
    private val day = 24L * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = skopje): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun local(ms: Long, zone: ZoneId = skopje): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    // ---------- status ----------

    @Test fun noEndDate_orANonsensicalOne_isNeverTreatedAsExpired() {
        val now = at(2026, 12, 1, 12, 0)
        assertEquals(Status.NotAGuest, GuestPeriod.status(null, now))
        assertEquals(Status.NotAGuest, GuestPeriod.status(0, now))
        assertEquals(Status.NotAGuest, GuestPeriod.status(-1, now))
        assertEquals(Status.NotAGuest, GuestPeriod.status(Long.MIN_VALUE, now))
    }

    @Test fun daysLeft_countsEveryStartedDay_atTheBoundaries() {
        val end = at(2026, 12, 15, 12, 0)
        fun days(remaining: Long) = (GuestPeriod.status(end, end - remaining) as Status.Running).daysLeft

        assertEquals(14, days(14 * day))                // the very start of a 14-day period
        assertEquals(14, days(14 * day - 1))            // a millisecond later: still "14"
        assertEquals(14, days(13 * day + 1))
        assertEquals(13, days(13 * day))                // exactly 13 days left
        assertEquals(2, days(day + 1))
        assertEquals(1, days(day))
        assertEquals(1, days(day - 1))
        assertEquals(1, days(1))                        // one millisecond left is still a day left
    }

    @Test fun theMomentTheEndIsReached_orPassed_isExpired() {
        val end = at(2026, 12, 15, 12, 0)
        assertEquals(Status.Expired, GuestPeriod.status(end, end))
        assertEquals(Status.Expired, GuestPeriod.status(end, end + 1))
        assertEquals(Status.Expired, GuestPeriod.status(end, end + 400 * day))
    }

    @Test fun endingSoon_isThreeDaysOrFewer() {
        val end = at(2026, 12, 15, 12, 0)
        fun soon(remaining: Long) = (GuestPeriod.status(end, end - remaining) as Status.Running).endingSoon

        assertFalse(soon(14 * day))
        assertFalse(soon(3 * day + 1))                  // just over 3 days: counts as 4 days left
        assertTrue(soon(3 * day))
        assertTrue(soon(2 * day))
        assertTrue(soon(1))
    }

    @Test fun veryFarEndDates_doNotOverflow() {
        val now = at(2026, 12, 1, 12, 0)
        assertEquals(400, (GuestPeriod.status(now + 400 * day, now) as Status.Running).daysLeft)
        val huge = GuestPeriod.status(Long.MAX_VALUE / 2, now) as Status.Running
        assertEquals(Int.MAX_VALUE, huge.daysLeft)
    }

    @Test fun daysLeft_isNotMovedByADaylightSavingChange() {
        // The period spans the clock change on 25 Oct 2026 in Skopje; real elapsed time is all that counts.
        val start = at(2026, 10, 20, 12, 0)
        val end = start + 14 * day
        assertEquals(14, (GuestPeriod.status(end, start) as Status.Running).daysLeft)
        assertEquals(7, (GuestPeriod.status(end, start + 7 * day) as Status.Running).daysLeft)
        assertEquals(1, (GuestPeriod.status(end, start + 13 * day) as Status.Running).daysLeft)
    }

    // ---------- reminders ----------

    @Test fun theRules_areThreeAndOneDaysBefore_atTenOClock() {
        assertEquals(listOf(3, 1), GuestPeriod.REMINDER_DAYS_BEFORE)
        assertEquals(10, GuestPeriod.REMINDER_HOUR)
    }

    @Test fun remindersAreOnTheCalendarDaysBeforeTheEnd_atTenLocal() {
        val end = at(2026, 12, 7, 22, 0)
        val now = at(2026, 12, 1, 9, 0)

        assertEquals(
            listOf(Reminder(3, at(2026, 12, 4, 10, 0)), Reminder(1, at(2026, 12, 6, 10, 0))),
            GuestPeriod.reminders(end, now, skopje)
        )
    }

    @Test fun anEndEarlyInTheMorning_stillRemindsOnTheRightDays_andBeforeTheEnd() {
        for (end in listOf(at(2026, 12, 7, 0, 30), at(2026, 12, 7, 8, 0), at(2026, 12, 7, 23, 59))) {
            val reminders = GuestPeriod.reminders(end, at(2026, 12, 1, 9, 0), skopje)
            assertEquals(listOf(local(at(2026, 12, 4, 10, 0)).toLocalDate(), local(at(2026, 12, 6, 10, 0)).toLocalDate()),
                reminders.map { local(it.atEpochMs).toLocalDate() })
            assertTrue("all reminders come before the end", reminders.all { it.atEpochMs < end })
            assertTrue(reminders.all { local(it.atEpochMs).hour == 10 && local(it.atEpochMs).minute == 0 })
        }
    }

    @Test fun remindersWhoseTimeHasPassed_areLeftOut() {
        val end = at(2026, 12, 7, 22, 0)
        val three = at(2026, 12, 4, 10, 0)
        val one = at(2026, 12, 6, 10, 0)

        assertEquals(listOf(3, 1), GuestPeriod.reminders(end, three - 1, skopje).map { it.daysBefore })
        assertEquals("exactly at the reminder time it is no longer upcoming", listOf(1), GuestPeriod.reminders(end, three, skopje).map { it.daysBefore })
        assertEquals(listOf(1), GuestPeriod.reminders(end, three + 1, skopje).map { it.daysBefore })
        assertEquals(listOf(1), GuestPeriod.reminders(end, one - 1, skopje).map { it.daysBefore })
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(end, one, skopje))
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(end, end - 1, skopje))
    }

    @Test fun nothingIsPlannedOnceThePeriodIsOver_orForNonsense() {
        val end = at(2026, 12, 7, 22, 0)
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(end, end, skopje))
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(end, end + 5 * day, skopje))
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(0, 0, skopje))
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(-5, 10, skopje))
    }

    @Test fun aPeriodThatEndsInLessThanADay_hasNoReminders() {
        val now = at(2026, 12, 6, 23, 0)
        assertEquals(emptyList<Reminder>(), GuestPeriod.reminders(now + 5 * hour, now, skopje))
    }

    @Test fun remindersAreInTimeOrder() {
        val reminders = GuestPeriod.reminders(at(2026, 12, 20, 15, 0), at(2026, 12, 1, 0, 0), skopje)
        assertEquals(reminders.map { it.atEpochMs }.sorted(), reminders.map { it.atEpochMs })
        assertEquals(listOf(3, 1), reminders.map { it.daysBefore })
    }

    @Test fun aDaylightSavingChangeBetweenTheReminders_stillGivesTenOClockLocal() {
        // Clocks go back on 25 Oct 2026 in Skopje (CEST -> CET): 24 Oct is UTC+2, 26 Oct is UTC+1.
        val end = at(2026, 10, 27, 22, 0)
        val (three, one) = GuestPeriod.reminders(end, at(2026, 10, 20, 0, 0), skopje)

        assertEquals(3, three.daysBefore); assertEquals(1, one.daysBefore)
        assertEquals(LocalDate.of(2026, 10, 24), local(three.atEpochMs).toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 26), local(one.atEpochMs).toLocalDate())
        assertTrue(local(three.atEpochMs).hour == 10 && local(one.atEpochMs).hour == 10)
        assertEquals("49 real hours apart because of the extra hour", 49 * hour, one.atEpochMs - three.atEpochMs)
    }

    @Test fun itWorksInOtherTimeZonesToo_usingTheUsersLocalCalendarDay() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 23:30 UTC on 7 Dec is already 8 Dec, 08:30 in Tokyo.
        val tokyoEnd = at(2026, 12, 7, 23, 30, ZoneId.of("UTC"))
        val tokyoReminders = GuestPeriod.reminders(tokyoEnd, at(2026, 12, 1, 0, 0, tokyo), tokyo)
        assertEquals(listOf(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 7)), tokyoReminders.map { local(it.atEpochMs, tokyo).toLocalDate() })
        assertTrue(tokyoReminders.all { local(it.atEpochMs, tokyo).hour == 10 })

        val ny = ZoneId.of("America/New_York")
        val nyEnd = at(2026, 12, 7, 22, 0, ny)
        val nyReminders = GuestPeriod.reminders(nyEnd, at(2026, 12, 1, 0, 0, ny), ny)
        assertEquals(listOf(LocalDate.of(2026, 12, 4), LocalDate.of(2026, 12, 6)), nyReminders.map { local(it.atEpochMs, ny).toLocalDate() })
        assertTrue(nyReminders.all { local(it.atEpochMs, ny).hour == 10 })
    }

    // ---------- the "period has ended" dialog ----------

    private val today = LocalDate.of(2026, 12, 20)

    @Test fun theDialog_showsOnlyOnceExpired_andAtMostOncePerCalendarDay() {
        assertTrue("never shown before", GuestPeriod.shouldShowExpiredDialog(Status.Expired, null, today))
        assertFalse("already shown today", GuestPeriod.shouldShowExpiredDialog(Status.Expired, today, today))
        assertTrue("shown yesterday: today is a new day", GuestPeriod.shouldShowExpiredDialog(Status.Expired, today.minusDays(1), today))
        assertTrue(GuestPeriod.shouldShowExpiredDialog(Status.Expired, today.minusDays(30), today))
    }

    @Test fun aRememberedDayInTheFuture_cannotSilenceTheDialogForGood() {
        // e.g. the phone date was wrongly set far ahead once: it shows (and then remembers today), never stays silent.
        assertTrue(GuestPeriod.shouldShowExpiredDialog(Status.Expired, LocalDate.of(2030, 1, 1), today))
    }

    @Test fun theDialog_neverShowsWhileTheGuestPeriodRunsOrForRegisteredAccounts() {
        for (lastShown in listOf(null, today, today.minusDays(5))) {
            assertFalse(GuestPeriod.shouldShowExpiredDialog(Status.Running(10), lastShown, today))
            assertFalse(GuestPeriod.shouldShowExpiredDialog(Status.Running(1), lastShown, today))
            assertFalse(GuestPeriod.shouldShowExpiredDialog(Status.NotAGuest, lastShown, today))
        }
    }

    // ---------- the server clock ----------

    @Test fun serverClock_appliesTheMeasuredDifference() {
        assertEquals(0, ServerClock().offsetMs)
        assertEquals(500, ServerClock().now(500))
        val phoneBehind = ServerClock.measuredAt(serverTimeMs = 1000, deviceNowMs = 400)
        assertEquals(600, phoneBehind.offsetMs)
        assertEquals(1500, phoneBehind.now(900))
        val phoneAhead = ServerClock.measuredAt(serverTimeMs = 1000, deviceNowMs = 5000)
        assertEquals(-4000, phoneAhead.offsetMs)
        assertEquals(1000, phoneAhead.now(5000))
    }

    @Test fun aWronglySetPhoneClock_doesNotSkewTheDaysLeft_whenCorrectedWithTheServerClock() {
        val serverNow = at(2026, 12, 1, 12, 0)
        val end = serverNow + 14 * day                       // the server just started a 14-day period
        val phoneNow = serverNow + 2 * day                   // the phone's date is two days ahead

        assertEquals("uncorrected, the phone would claim 12 days", 12, (GuestPeriod.status(end, phoneNow) as Status.Running).daysLeft)
        val clock = ServerClock.measuredAt(serverNow, phoneNow)
        assertEquals("corrected, it is the true 14", 14, (GuestPeriod.status(end, clock.now(phoneNow)) as Status.Running).daysLeft)
        // five real days later the (still wrong) phone clock reads 5 days further, and the correction keeps it right
        assertEquals(9, (GuestPeriod.status(end, clock.now(phoneNow + 5 * day)) as Status.Running).daysLeft)
    }
}
