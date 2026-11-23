package com.mojrucek.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GuestRemindersTest {
    private val zone = ZoneId.of("Europe/Skopje")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 0) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private class Fake : GuestReminderScheduling {
        data class Item(val uid: String, val expires: Long, val days: Int, val at: Long)
        val items = mutableListOf<Item>()
        var cancels = 0
        override fun schedule(uid: String, expiresAtMs: Long, daysBefore: Int, atEpochMs: Long) { items += Item(uid, expiresAtMs, daysBefore, atEpochMs) }
        override fun cancelAll() { cancels++; items.clear() }
    }

    private fun session(kind: String = "GUEST", expires: Long? = ms(2026, 12, 10, 14), offset: Long = 0) =
        StoredSession("usr", kind, "GUEST", null, expires, offset, 0)

    private fun coordinator(f: Fake, now: Long) = GuestReminderCoordinator(f, { now }, { zone })

    @Test fun aRunningGuest_getsBothReminders_at10Local() {
        val f = Fake()
        coordinator(f, ms(2026, 11, 26, 9)).onSession("uid-A", session())

        assertEquals(listOf(3 to ms(2026, 12, 7, 10), 1 to ms(2026, 12, 9, 10)), f.items.map { it.days to it.at })
        assertTrue(f.items.all { it.uid == "uid-A" && it.expires == ms(2026, 12, 10, 14) })
    }

    @Test fun reminders_alreadyPast_areNotPlanned() {
        val f = Fake()
        coordinator(f, ms(2026, 12, 8, 12)).onSession("uid-A", session())
        assertEquals(listOf(1), f.items.map { it.days })
    }

    @Test fun theOldPlanIsAlwaysDroppedFirst() {
        val f = Fake()
        val c = coordinator(f, ms(2026, 11, 26, 9))
        c.onSession("uid-A", session())
        c.onSession("uid-A", session())
        assertEquals(2, f.items.size)       // not 4
        assertEquals(2, f.cancels)
    }

    @Test fun nothingIsPlanned_whenNotARunningGuest() {
        val now = ms(2026, 11, 26, 9)
        for ((uid, s) in listOf<Pair<String?, StoredSession?>>(null to session(), "u" to null, "u" to session("REGISTERED"),
            "u" to session(expires = null), "u" to session(expires = ms(2026, 11, 1)))) {
            val f = Fake()
            coordinator(f, now).onSession(uid, s)
            assertTrue("$uid $s", f.items.isEmpty())
            assertEquals(1, f.cancels)
        }
    }

    @Test fun aWrongPhoneClock_isCorrectedByTheMeasuredOffset() {
        // Phone is 2 hours ahead of the server: server instant X is reached when the phone shows X - offset.
        val f = Fake()
        val offset = -2L * 3600 * 1000
        coordinator(f, ms(2026, 11, 26, 9)).onSession("u", session(offset = offset))
        assertEquals(ms(2026, 12, 7, 10) - offset, f.items.first().at)
    }

    // ---------- at the moment of showing ----------
    private val exp = ms(2026, 12, 10, 14)
    private fun ok(cur: String? = "u", s: StoredSession? = session(), planned: Long = exp, now: Long = ms(2026, 12, 7, 10)) =
        GuestReminderDecision.shouldNotify(cur, "u", s, planned, now)

    @Test fun shows_onlyWhenEverythingStillMatches() {
        assertTrue(ok())
        assertFalse("another account", ok(cur = "other"))
        assertFalse("signed out", ok(cur = null))
        assertFalse("linked Google meanwhile", ok(s = session("REGISTERED")))
        assertFalse("nothing remembered", ok(s = null))
        assertFalse("plan out of date", ok(planned = exp + 1))
        assertFalse("already over", ok(now = ms(2026, 12, 11)))
    }
}
