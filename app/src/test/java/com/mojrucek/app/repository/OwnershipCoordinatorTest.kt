package com.mojrucek.app.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeUnowned(var rows: Int) : UnownedData {
    val claimedBy = mutableListOf<String>()
    var failNextClaim = false
    override suspend fun count() = rows
    override suspend fun claimFor(uid: String): Int {
        if (failNextClaim) { failNextClaim = false; throw IllegalStateException("database error") }
        claimedBy += uid
        val n = rows
        rows = 0
        return n
    }
}

class OwnershipCoordinatorTest {

    private val A = "uid-A"
    private val B = "uid-B"

    private class Setup(rows: Int, state: LegacyState = LegacyState.UNSET) {
        val data = FakeUnowned(rows)
        val prefs = InMemoryOwnershipPrefs(state)
        var tidied = 0
        val coordinator = OwnershipCoordinator(data, prefs, afterClaim = { tidied++ })
    }

    // ---------- an account is signed in when the app first looks ----------

    @Test fun signedInAtTheFirstLook_getsTheDataAtOnce_withoutAQuestion() = runBlocking {
        val s = Setup(rows = 5)
        s.coordinator.onOwnerChanged(A)

        assertEquals(listOf(A), s.data.claimedBy)
        assertEquals(LegacyState.DONE, s.prefs.legacyState)
        assertFalse(s.coordinator.promptNeeded.value)
        assertEquals("the tidy-up that needs owned data runs after the claim", 1, s.tidied)
    }

    @Test fun signedInAtTheFirstLook_withNothingToClaim_isStillDone() = runBlocking {
        val s = Setup(rows = 0)
        s.coordinator.onOwnerChanged(A)

        assertEquals(LegacyState.DONE, s.prefs.legacyState)
        assertFalse(s.coordinator.promptNeeded.value)
    }

    // ---------- nobody is signed in when the app first looks ----------

    @Test fun nobodySignedIn_withOldData_remembersToAskLater_andClaimsNothing() = runBlocking {
        val s = Setup(rows = 3)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)

        assertEquals(LegacyState.PENDING, s.prefs.legacyState)
        assertTrue(s.data.claimedBy.isEmpty())
        assertFalse("no question while nobody is signed in", s.coordinator.promptNeeded.value)
    }

    @Test fun nobodySignedIn_andNothingToDecide_isDone() = runBlocking {
        val s = Setup(rows = 0)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)

        assertEquals(LegacyState.DONE, s.prefs.legacyState)
        s.coordinator.onOwnerChanged(A)
        assertFalse(s.coordinator.promptNeeded.value)
    }

    @Test fun afterwards_theFirstAccountToSignIn_isAskedFirst_andClaimsNothingYet() = runBlocking {
        val s = Setup(rows = 3)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        s.coordinator.onOwnerChanged(A)

        assertTrue(s.coordinator.promptNeeded.value)
        assertTrue("the data is NOT handed over until the user says so", s.data.claimedBy.isEmpty())
        assertEquals(LegacyState.PENDING, s.prefs.legacyState)
    }

    @Test fun keep_handsTheDataToThatAccount_andNeverAsksAgain() = runBlocking {
        val s = Setup(rows = 3)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        s.coordinator.onOwnerChanged(A)

        s.coordinator.keep(A)

        assertEquals(listOf(A), s.data.claimedBy)
        assertEquals(LegacyState.DONE, s.prefs.legacyState)
        assertFalse(s.coordinator.promptNeeded.value)
        assertEquals(1, s.tidied)
        s.coordinator.onOwnerChanged(B)
        assertFalse("a later account is never asked and never receives it", s.coordinator.promptNeeded.value)
        assertEquals(listOf(A), s.data.claimedBy)
    }

    @Test fun later_changesNothing_andTheQuestionComesBackAtTheNextSignIn() = runBlocking {
        val s = Setup(rows = 3)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        s.coordinator.onOwnerChanged(A)
        assertTrue(s.coordinator.promptNeeded.value)

        s.coordinator.askLater()

        assertFalse(s.coordinator.promptNeeded.value)
        assertEquals(LegacyState.PENDING, s.prefs.legacyState)
        assertTrue(s.data.claimedBy.isEmpty())
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)   // signs out
        s.coordinator.onOwnerChanged(A)                      // and back in (or a restart)
        assertTrue(s.coordinator.promptNeeded.value)
    }

    @Test fun anotherPersonSigningInFirst_canSayLater_andTheOwnerIsAskedAtTheirOwnSignIn() = runBlocking {
        val s = Setup(rows = 3)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        s.coordinator.onOwnerChanged(B)       // not the person the data belongs to
        s.coordinator.askLater()
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        s.coordinator.onOwnerChanged(A)       // the real owner
        assertTrue(s.coordinator.promptNeeded.value)

        s.coordinator.keep(A)

        assertEquals(listOf(A), s.data.claimedBy)
    }

    @Test fun switchingAccountWhileTheQuestionIsShowing_asksAgainForTheNewAccount() = runBlocking {
        val s = Setup(rows = 3, state = LegacyState.PENDING)
        s.coordinator.onOwnerChanged(A)
        assertTrue(s.coordinator.promptNeeded.value)

        s.coordinator.onOwnerChanged(B)

        assertTrue("still pending, now for B", s.coordinator.promptNeeded.value)
        s.coordinator.keep(B)
        assertEquals(listOf(B), s.data.claimedBy)
    }

    @Test fun pending_butTheDataIsGoneByNow_isDoneWithoutAQuestion() = runBlocking {
        val s = Setup(rows = 0, state = LegacyState.PENDING)
        s.coordinator.onOwnerChanged(A)

        assertFalse(s.coordinator.promptNeeded.value)
        assertEquals(LegacyState.DONE, s.prefs.legacyState)
    }

    // ---------- the other cases ----------

    @Test fun done_meansNothingEverHappensAgain() = runBlocking {
        val s = Setup(rows = 4, state = LegacyState.DONE)
        s.coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        s.coordinator.onOwnerChanged(A)
        s.coordinator.keep(A)   // a stray call must not matter either

        assertFalse(s.coordinator.promptNeeded.value)
        assertTrue("nothing was ever handed over", s.data.claimedBy.isEmpty())
        assertEquals(LegacyState.DONE, s.prefs.legacyState)
    }

    @Test fun keep_doesNothing_whenNoQuestionIsPending() = runBlocking {
        val s = Setup(rows = 4)   // state UNSET: nobody has looked yet
        s.coordinator.keep(A)

        assertTrue(s.data.claimedBy.isEmpty())
        assertEquals(LegacyState.UNSET, s.prefs.legacyState)
    }

    @Test fun aBuildWithoutAccounts_neverClaimsAndNeverAsks() = runBlocking {
        val s = Setup(rows = 4)
        s.coordinator.onOwnerChanged(OwnerKeys.UNOWNED)

        assertEquals(LegacyState.UNSET, s.prefs.legacyState)
        assertTrue(s.data.claimedBy.isEmpty())
        assertFalse(s.coordinator.promptNeeded.value)
        s.coordinator.keep(OwnerKeys.UNOWNED)
        s.coordinator.keep(OwnerKeys.SIGNED_OUT)
        assertTrue("keep() for something that is not an account does nothing", s.data.claimedBy.isEmpty())
    }

    @Test fun aFailedClaim_leavesTheStateAsItWas_soItIsTriedAgain() = runBlocking {
        val s = Setup(rows = 3)
        s.data.failNextClaim = true
        try { s.coordinator.onOwnerChanged(A) } catch (expected: IllegalStateException) { }

        assertEquals("not marked as done", LegacyState.UNSET, s.prefs.legacyState)
        assertEquals(0, s.tidied)
        s.coordinator.onOwnerChanged(A)               // the next start succeeds
        assertEquals(listOf(A), s.data.claimedBy)
        assertEquals(LegacyState.DONE, s.prefs.legacyState)
    }
}
