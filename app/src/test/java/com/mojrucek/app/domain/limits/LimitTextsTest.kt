package com.mojrucek.app.domain.limits

import com.mojrucek.app.session.PermissionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitTextsTest {
    private val m = 60_000L
    private val h = 60 * m
    private val d = 24 * h
    private val now = 1_800_000_000_000L

    @Test fun countdown_isRoundedUp_andUsesTheRightUnits() {
        assertEquals("сега", LimitTexts.countdown(now, now))
        assertEquals("сега", LimitTexts.countdown(now - 5, now))
        assertEquals("1 мин", LimitTexts.countdown(now + 1, now))
        assertEquals("1 мин", LimitTexts.countdown(now + m, now))
        assertEquals("2 мин", LimitTexts.countdown(now + m + 1, now))
        assertEquals("59 мин", LimitTexts.countdown(now + 59 * m, now))
        assertEquals("1ч", LimitTexts.countdown(now + h, now))
        assertEquals("3ч 20 мин", LimitTexts.countdown(now + 3 * h + 20 * m, now))
        assertEquals("1д", LimitTexts.countdown(now + d, now))
        assertEquals("2д 5ч", LimitTexts.countdown(now + 2 * d + 5 * h, now))
        assertEquals("3д", LimitTexts.countdown(now + 72 * h, now))
    }

    @Test fun justUnder72Hours_isShownAs3Days_neverTooEarly() {
        // 71h59m30s left rounds up to 72h = 3 days: it never says "2д 23ч" when a minute still has to pass.
        assertEquals("3д", LimitTexts.countdown(now + 72 * h - 30_000, now))
    }

    @Test fun nextListLine_onlyWhileWaiting() {
        assertNull(LimitTexts.nextListLine(null, now))
        assertNull(LimitTexts.nextListLine(now, now))
        assertNull(LimitTexts.nextListLine(now - 1, now))
        assertEquals("Следна листа може за 2д 5ч", LimitTexts.nextListLine(now + 2 * d + 5 * h, now))
    }

    @Test fun noMessage_whenTheListMayGoAhead() {
        assertNull(LimitTexts.notice(PermissionResult.Allowed))
        assertNull(LimitTexts.notice(PermissionResult.NotNeeded))
    }

    @Test fun everyRefusal_hasAMessage_andOnlyAnExpiredGuestIsOfferedGoogle() {
        val all = listOf(
            PermissionResult.TooSoon(now + h, now), PermissionResult.GuestExpired, PermissionResult.TooMany(3),
            PermissionResult.NeedsInternet, PermissionResult.TryLater, PermissionResult.Problem("грешка")
        )
        for (r in all) {
            val n = LimitTexts.notice(r) ?: throw AssertionError("no message for $r")
            assertTrue("$r", n.title.isNotBlank() && n.text.isNotBlank())
            assertEquals("$r", r is PermissionResult.GuestExpired, n.linkGoogle)
        }
    }

    @Test fun tooSoon_namesTheWait_andTooMany_namesTheLimit() {
        assertTrue(LimitTexts.notice(PermissionResult.TooSoon(now + 2 * d, now))!!.text.contains("2д"))
        assertTrue(LimitTexts.notice(PermissionResult.TooMany(3))!!.title.contains("3"))
        assertFalse(LimitTexts.notice(PermissionResult.Problem("моја порака"))!!.text.isBlank())
        assertEquals("моја порака", LimitTexts.notice(PermissionResult.Problem("моја порака"))!!.text)
    }
}
