package com.mojrucek.app.domain.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One reminder to schedule: [daysBefore] the end of the guest period, at [atEpochMs] (ms since 1970). */
data class Reminder(val daysBefore: Int, val atEpochMs: Long)

/**
 * The rules around a guest's 14 days, as plain date logic (no Android, no clock of its own: every function is
 * given "now"). The server decides when the period ends (`guestExpiresAt`); this only turns that moment into
 * what the user sees and when the app reminds them. Nothing here restricts anything.
 */
object GuestPeriod {

    /** Reminders go out this many days before the end of the period... */
    val REMINDER_DAYS_BEFORE: List<Int> = listOf(3, 1)

    /** ...at this local hour, the same default hour as the app's other reminders. */
    const val REMINDER_HOUR = 10

    /** "Ending soon" means this many days or fewer are left. */
    const val ENDING_SOON_DAYS = 3

    private const val DAY_MS = 24L * 60 * 60 * 1000

    sealed interface Status {
        /** No end date known: a registered account, or the server has not answered yet. Nothing to show. */
        data object NotAGuest : Status

        /** Still running. [daysLeft] counts every started day (just under 14 days left is still "14"). */
        data class Running(val daysLeft: Int) : Status {
            val endingSoon: Boolean get() = daysLeft <= ENDING_SOON_DAYS
        }

        /** The end moment has been reached. */
        data object Expired : Status
    }

    /**
     * Where the guest period stands at [nowMs]. [nowMs] should be server-corrected time (see [ServerClock]).
     * A missing or nonsensical end date (null, zero or negative) is "not a guest": it is never trusted as "expired".
     */
    fun status(expiresAtMs: Long?, nowMs: Long): Status {
        if (expiresAtMs == null || expiresAtMs <= 0) return Status.NotAGuest
        val remaining = expiresAtMs - nowMs
        if (remaining <= 0) return Status.Expired
        // Counted in real 24-hour days from the exact end moment, so a daylight-saving change cannot move it.
        val days = remaining / DAY_MS + if (remaining % DAY_MS == 0L) 0 else 1
        return Status.Running(daysLeft = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /**
     * The reminders still to come: 3 days and 1 day before the end, each at 10:00 on that CALENDAR day in the
     * user's [zone] (so the text "3 days" is never late). Reminders whose time has already passed are left out,
     * and nothing is planned once the period is over.
     */
    fun reminders(expiresAtMs: Long, nowMs: Long, zone: ZoneId): List<Reminder> {
        if (expiresAtMs <= 0 || expiresAtMs <= nowMs) return emptyList()
        val endDay = Instant.ofEpochMilli(expiresAtMs).atZone(zone).toLocalDate()
        return REMINDER_DAYS_BEFORE.sortedDescending().mapNotNull { daysBefore ->
            val at = endDay.minusDays(daysBefore.toLong()).atTime(REMINDER_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            if (at > nowMs && at < expiresAtMs) Reminder(daysBefore, at) else null
        }
    }

    /**
     * Whether to show the "the guest period has ended" invitation now: only once it has ended, and at most once
     * per calendar day. A remembered day that is not today (including one in the future after a clock change)
     * allows it, so a wrong date can never silence it for good.
     */
    fun shouldShowExpiredDialog(status: Status, lastShown: LocalDate?, today: LocalDate): Boolean =
        status is Status.Expired && lastShown != today
}

/**
 * How far the phone's clock is from the server's, measured when the server last answered. Applying it gives
 * "server time" even if the phone's date was wrong at that moment.
 */
class ServerClock(val offsetMs: Long = 0) {
    fun now(deviceNowMs: Long): Long = deviceNowMs + offsetMs

    companion object {
        /** The clock difference seen when the server said it was [serverTimeMs] and the phone said [deviceNowMs]. */
        fun measuredAt(serverTimeMs: Long, deviceNowMs: Long) = ServerClock(serverTimeMs - deviceNowMs)
    }
}
