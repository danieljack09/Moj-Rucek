package com.mojrucek.app.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountDeletionTest {

    private class Steps : DeletionSteps {
        val log = mutableListOf<String>()
        override var currentUid: String? = "uid-A"
        var reauth: Step = Step.Done
        var server: Step = Step.Done
        var firebase: Step = Step.Done
        var eraseFails = false
        var throwAt: String? = null
        var uidAfterReauth: String? = "uid-A"

        private fun maybeThrow(at: String) { if (throwAt == at) throw IllegalStateException("boom at $at") }

        override suspend fun reauthenticate(): Step { log += "reauth"; maybeThrow("reauth"); currentUid = uidAfterReauth; return reauth }
        override suspend fun deleteOnServer(): Step { log += "server"; maybeThrow("server"); return server }
        override suspend fun deleteFirebaseAccount(): Step {
            log += "firebase"; maybeThrow("firebase")
            if (firebase == Step.Done) currentUid = null
            return firebase
        }
        override suspend fun eraseLocal(uid: String) { log += "erase:$uid"; maybeThrow("erase"); if (eraseFails) throw IllegalStateException("disk") }
    }

    private fun run(s: Steps, pending: PendingErasureStore = InMemoryPendingErasure()) =
        runBlocking { AccountDeletion(s, pending).run() } to pending

    @Test fun theHappyPath_runsTheStepsInOrder_andLeavesNothingPending() {
        val s = Steps()
        val (outcome, pending) = run(s)

        assertEquals(DeletionOutcome.Done, outcome)
        assertEquals(listOf("reauth", "server", "firebase", "erase:uid-A"), s.log)
        assertNull(pending.get())
    }

    @Test fun nobodySignedIn_doesNothing() {
        val s = Steps().apply { currentUid = null }
        val (outcome, _) = run(s)
        assertTrue(outcome is DeletionOutcome.Failed)
        assertTrue(s.log.isEmpty())
    }

    @Test fun backingOutOfTheOwnerCheck_changesNothing() {
        val s = Steps().apply { reauth = Step.Cancelled }
        val (outcome, pending) = run(s)
        assertEquals(DeletionOutcome.Cancelled, outcome)
        assertEquals(listOf("reauth"), s.log)
        assertNull(pending.get())
    }

    @Test fun aFailedOwnerCheck_changesNothing() {
        val s = Steps().apply { reauth = Step.Failed("wrong account") }
        val (outcome, pending) = run(s)
        assertEquals(DeletionOutcome.Failed("wrong account", nothingDeleted = true), outcome)
        assertEquals(listOf("reauth"), s.log)
        assertNull(pending.get())
    }

    @Test fun anotherAccountAppearingDuringTheOwnerCheck_stopsBeforeAnyDeletion() {
        val s = Steps().apply { uidAfterReauth = "uid-B" }
        val (outcome, _) = run(s)
        assertEquals(DeletionOutcome.Failed("Сметката се промени. Обиди се повторно.", nothingDeleted = true), outcome)
        assertEquals(listOf("reauth"), s.log)
    }

    @Test fun serverTroubleBeforeAnythingIsDeleted_changesNothing() {
        val s = Steps().apply { server = Step.Failed("no internet") }
        val (outcome, pending) = run(s)
        assertEquals(DeletionOutcome.Failed("no internet", nothingDeleted = true), outcome)
        assertEquals(listOf("reauth", "server"), s.log)
        assertNull("the point of no return was not reached", pending.get())
    }

    @Test fun firebaseFailingAfterTheServer_keepsThePhoneDataAndRemembersTheAccount() {
        val s = Steps().apply { firebase = Step.Failed("network") }
        val (outcome, pending) = run(s)
        assertEquals(DeletionOutcome.Failed("network", nothingDeleted = false), outcome)
        assertEquals(listOf("reauth", "server", "firebase"), s.log)
        assertEquals("uid-A", pending.get())
    }

    @Test fun afterThatFailure_aRetryFinishesTheJob() {
        val s = Steps().apply { firebase = Step.Failed("network") }
        val pending = InMemoryPendingErasure()
        run(s, pending)

        s.firebase = Step.Done
        s.log.clear()
        val outcome = runBlocking { AccountDeletion(s, pending).run() }

        assertEquals(DeletionOutcome.Done, outcome)
        assertEquals(listOf("reauth", "server", "firebase", "erase:uid-A"), s.log)
        assertNull(pending.get())
    }

    @Test fun anErrorWhileErasing_keepsTheAccountPending_forTheNextStart() {
        val s = Steps().apply { eraseFails = true }
        val (outcome, pending) = run(s)
        assertTrue(outcome is DeletionOutcome.Failed)
        assertEquals("uid-A", pending.get())
    }

    @Test fun anUnexpectedErrorBeforeTheServer_isReportedAsNothingDeleted() {
        val s = Steps().apply { throwAt = "reauth" }
        val (outcome, pending) = run(s)
        assertTrue((outcome as DeletionOutcome.Failed).nothingDeleted)
        assertNull(pending.get())
    }

    @Test fun cancellingTheWork_isNeverSwallowed() {
        val s = object : DeletionSteps by Steps() {
            override suspend fun reauthenticate(): Step = throw CancellationException("screen closed")
        }
        try {
            runBlocking { AccountDeletion(s, InMemoryPendingErasure()).run() }
            fail("must propagate")
        } catch (e: CancellationException) { }
    }

    // ---------- finishing an interrupted deletion at app start ----------

    @Test fun interrupted_andTheAccountIsGone_theLeftoverPhoneDataIsErased() {
        val s = Steps().apply { currentUid = null }
        val pending = InMemoryPendingErasure().apply { set("uid-A") }

        runBlocking { AccountDeletion(s, pending).finishInterrupted() }

        assertEquals(listOf("erase:uid-A"), s.log)
        assertNull(pending.get())
    }

    @Test fun interrupted_butThatAccountIsStillSignedIn_nothingIsErased() {
        val s = Steps().apply { currentUid = "uid-A" }
        val pending = InMemoryPendingErasure().apply { set("uid-A") }

        runBlocking { AccountDeletion(s, pending).finishInterrupted() }

        assertTrue(s.log.isEmpty())
        assertEquals("uid-A", pending.get())
    }

    @Test fun interrupted_whileAnotherAccountIsSignedIn_onlyTheDeletedOnesDataGoes() {
        val s = Steps().apply { currentUid = "uid-B" }
        val pending = InMemoryPendingErasure().apply { set("uid-A") }

        runBlocking { AccountDeletion(s, pending).finishInterrupted() }

        assertEquals(listOf("erase:uid-A"), s.log)
    }

    @Test fun nothingPending_nothingHappens() {
        val s = Steps()
        runBlocking { AccountDeletion(s, InMemoryPendingErasure()).finishInterrupted() }
        assertTrue(s.log.isEmpty())
    }

    @Test fun anErrorWhileFinishing_staysPending_andDoesNotCrash() {
        val s = Steps().apply { currentUid = null; eraseFails = true }
        val pending = InMemoryPendingErasure().apply { set("uid-A") }

        runBlocking { AccountDeletion(s, pending).finishInterrupted() }

        assertEquals("uid-A", pending.get())
    }
}
