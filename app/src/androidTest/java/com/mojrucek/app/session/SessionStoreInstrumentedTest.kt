package com.mojrucek.app.session

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.domain.session.GuestPeriod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The remembered server answers in the app's private preferences. Uses throw-away account ids and removes exactly
 * those entries afterwards, so the real accounts' entries on the phone are never touched.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raw = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val tag = "test-${System.nanoTime()}"
    private val a = "$tag-A"
    private val b = "$tag-B"

    @After fun cleanUp() {
        raw.edit().apply { for (uid in listOf(a, b)) { remove("s_$uid"); remove("d_$uid") } }.commit()
    }

    private fun store() = SharedPreferencesSessionStore(context)

    private fun session(kind: String = "GUEST", expires: Long? = 1_000_000_000_000L) =
        StoredSession("usr_1", kind, "GUEST", guestStartedAt = 5, guestExpiresAt = expires, clockOffsetMs = -1234, refreshedAtMs = 99)

    @Test fun nothingRemembered_isNull() {
        assertNull(store().load(a))
        assertNull(store().expiredDialogShownOn(a))
    }

    @Test fun anAnswerSurvivesANewStoreInstance_withEveryField() {
        store().save(a, session())

        assertEquals(session(), store().load(a))
    }

    @Test fun twoAccounts_keepTheirOwnAnswers_andTheirOwnDialogDay() {
        store().save(a, session(kind = "GUEST"))
        store().save(b, session(kind = "REGISTERED", expires = null))
        store().setExpiredDialogShownOn(a, LocalDate.of(2026, 12, 20))

        assertEquals("GUEST", store().load(a)!!.kind)
        assertEquals("REGISTERED", store().load(b)!!.kind)
        assertEquals(LocalDate.of(2026, 12, 20), store().expiredDialogShownOn(a))
        assertNull("B was never shown the dialog", store().expiredDialogShownOn(b))
    }

    @Test fun savingAgain_replacesTheOldAnswer() {
        store().save(a, session(kind = "GUEST"))
        store().save(a, session(kind = "REGISTERED", expires = null))

        assertEquals("REGISTERED", store().load(a)!!.kind)
        assertEquals(GuestPeriod.Status.NotAGuest, store().load(a)!!.guestStatus(1_700_000_000_000L))
    }

    @Test fun anUnreadableEntry_isTreatedAsNothingRemembered_neverACrash() {
        for (junk in listOf("", "not json", "[]", "{\"userId\":", "{\"kind\":\"GUEST\"}")) {
            raw.edit().putString("s_$a", junk).commit()
            assertNull("<$junk>", store().load(a))
        }
        store().save(a, session())   // and the next refresh simply replaces it
        assertEquals(session(), store().load(a))
    }

    @Test fun anUnreadableDialogDay_isTreatedAsNeverShown() {
        raw.edit().putString("d_$a", "yesterday-ish").commit()
        assertNull(store().expiredDialogShownOn(a))
        store().setExpiredDialogShownOn(a, LocalDate.of(2027, 1, 2))
        assertEquals(LocalDate.of(2027, 1, 2), store().expiredDialogShownOn(a))
    }

    @Test fun theStoredAnswerAppliesTheClockDifference() {
        val expires = 1_800_000_000_000L + 14L * 24 * 3600 * 1000
        val s = StoredSession("u", "GUEST", "GUEST", null, expires, clockOffsetMs = -2L * 24 * 3600 * 1000, refreshedAtMs = 0)
        // the phone reads 2 days ahead of the server: corrected, the full 14 days are left
        assertTrue(s.guestStatus(1_800_000_000_000L + 2L * 24 * 3600 * 1000) == GuestPeriod.Status.Running(14))
    }
}
