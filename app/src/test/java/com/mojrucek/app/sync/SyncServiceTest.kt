package com.mojrucek.app.sync

import com.mojrucek.app.domain.sync.SyncTexts
import com.mojrucek.app.session.RetryScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SyncServiceTest {

    private class FakeSyncer : Syncer {
        var outcome: SyncOutcome = SyncOutcome.AlreadyInSync
        var calls = 0
        var keep = 0
        var cloud = 0
        var last: Long? = null
        var gate: CompletableDeferred<Unit>? = null
        var failWith: Throwable? = null
        override suspend fun sync(): SyncOutcome { val n = ++calls; failWith?.let { throw it }; if (n == 1) gate?.await(); return outcome }   // only the FIRST run waits at the gate
        override suspend fun resolveKeepThisPhone(): SyncOutcome { keep++; return outcome }
        override suspend fun resolveUseCloud(): SyncOutcome { cloud++; return outcome }
        override fun lastSyncedAt(uid: String): Long? = last
    }

    private class FakeRetry : RetryScheduler {
        var scheduled = 0; var cancelled = 0
        override fun scheduleRetry() { scheduled++ }
        override fun cancelRetry() { cancelled++ }
    }

    private class Rig(val syncer: FakeSyncer = FakeSyncer(), val retry: FakeRetry = FakeRetry(), var uid: String? = "uid-A", var now: Long = 1_000_000, var allowed: Boolean = true) {
        val service = SyncService(syncer, retry, { uid }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { allowed }, { now }, minGapMs = 60_000)
    }

    private fun run(r: Rig, manual: Boolean = false) = runBlocking { r.service.syncNow(manual) }

    // ---------- outcomes become what the screens show ----------

    @Test fun finishedRuns_showInSync_withTheLastSyncTime_andCancelARetry() {
        for (o in listOf(SyncOutcome.Uploaded, SyncOutcome.Downloaded, SyncOutcome.AlreadyInSync)) {
            val r = Rig().also { it.syncer.outcome = o; it.syncer.last = 777 }
            assertEquals(o, run(r))
            assertEquals(SyncUiStatus.InSync, r.service.state.value.status)
            assertEquals(777L, r.service.state.value.lastSyncedAt)
            assertEquals(1, r.retry.cancelled); assertEquals(0, r.retry.scheduled)
        }
    }

    @Test fun noConnection_orABusyServer_planOneRetry() {
        val a = Rig().also { it.syncer.outcome = SyncOutcome.Offline }
        run(a)
        assertEquals(SyncUiStatus.Offline, a.service.state.value.status)
        assertEquals(1, a.retry.scheduled)
        val b = Rig().also { it.syncer.outcome = SyncOutcome.TryLater }
        run(b)
        assertEquals(SyncUiStatus.TryLater, b.service.state.value.status)
        assertEquals(1, b.retry.scheduled)
    }

    @Test fun aConflict_waitsForTheUser_noRetry_andShowsTheDialog() {
        val r = Rig().also { it.syncer.outcome = SyncOutcome.NeedsChoice(4) }
        run(r)
        assertEquals(SyncUiStatus.NeedsChoice(4), r.service.state.value.status)
        assertFalse(r.service.state.value.choiceDismissed)
        assertEquals(0, r.retry.scheduled); assertEquals(1, r.retry.cancelled)
    }

    @Test fun laterHidesTheDialog_untilANewConflictOrTheUserReopensIt() {
        val r = Rig().also { it.syncer.outcome = SyncOutcome.NeedsChoice(4) }
        run(r)
        r.service.dismissChoice()
        assertTrue(r.service.state.value.choiceDismissed)
        r.service.reopenChoice()
        assertFalse(r.service.state.value.choiceDismissed)
        r.service.dismissChoice()
        r.now += 120_000
        run(r)                                            // the next run finds the conflict again
        assertFalse("a new run shows the question again", r.service.state.value.choiceDismissed)
    }

    @Test fun otherOutcomes_areShownWithoutAnyRawError() {
        val cases = mapOf<SyncOutcome, SyncUiStatus>(
            SyncOutcome.TooLarge to SyncUiStatus.TooLarge,
            SyncOutcome.Problem("текст") to SyncUiStatus.Problem("текст")
        )
        for ((outcome, status) in cases) {
            val r = Rig().also { it.syncer.outcome = outcome }
            run(r)
            assertEquals(status, r.service.state.value.status)
            assertEquals("no retry for these", 0, r.retry.scheduled)
        }
        val signedOut = Rig().also { it.syncer.outcome = SyncOutcome.NotSignedIn }
        run(signedOut)
        assertEquals(SyncUiState(), signedOut.service.state.value)
    }

    // ---------- when a run happens ----------

    @Test fun automaticRuns_areSpacedOut_theButtonIsNot() {
        val r = Rig()
        assertEquals(SyncOutcome.AlreadyInSync, run(r))
        r.now += 30_000
        assertNull("too soon after the last automatic run", run(r))
        assertEquals(SyncOutcome.AlreadyInSync, run(r, manual = true))
        assertEquals(2, r.syncer.calls)
        r.now += 60_000
        assertEquals(SyncOutcome.AlreadyInSync, run(r))
        assertEquals(3, r.syncer.calls)
    }

    @Test fun aManualRun_doesNotStartTheSpacingClock() {
        val r = Rig()
        run(r, manual = true)
        assertEquals("the first automatic run is not held back by a manual one", SyncOutcome.AlreadyInSync, run(r))
    }

    @Test fun whileSomethingMustSettleFirst_noRunStarts() {
        val r = Rig(allowed = false)
        assertNull(run(r)); assertNull(run(r, manual = true))
        assertEquals(0, r.syncer.calls)
        r.allowed = true
        assertEquals(SyncOutcome.AlreadyInSync, run(r))
    }

    @Test fun aSecondRunWhileOneIsActive_isSkipped() = runBlocking {
        val r = Rig()
        r.syncer.gate = CompletableDeferred()
        val first = async(Dispatchers.Default) { r.service.syncNow(manual = true) }
        while (r.syncer.calls == 0) kotlinx.coroutines.yield()
        assertEquals(SyncUiStatus.Syncing, r.service.state.value.status)
        assertNull(r.service.syncNow(manual = true))
        r.syncer.gate!!.complete(Unit)
        assertEquals(SyncOutcome.AlreadyInSync, first.await())
        assertEquals(1, r.syncer.calls)
    }

    @Test fun cancellingARun_isNeverSwallowed_andNothingStaysStuckOnSyncing() {
        val r = Rig().also { it.syncer.failWith = CancellationException("screen closed") }
        try { run(r, manual = true); fail("must propagate") } catch (e: CancellationException) { }
        assertEquals(SyncUiStatus.Idle, r.service.state.value.status)
        r.syncer.failWith = null
        assertEquals("and a later run is possible", SyncOutcome.AlreadyInSync, run(r, manual = true))
    }

    // ---------- the user's choice and the account ----------

    @Test fun theUsersChoices_goToTheSyncer() {
        val r = Rig().also { it.syncer.outcome = SyncOutcome.Uploaded }
        r.service.chooseKeepThisPhone()
        assertEquals(1, r.syncer.keep)
        r.syncer.outcome = SyncOutcome.Downloaded
        r.service.chooseUseCloud()
        assertEquals(1, r.syncer.cloud)
        assertEquals(SyncUiStatus.InSync, r.service.state.value.status)
    }

    @Test fun whenTheAccountChanges_whatWasShownIsForgotten() {
        val r = Rig().also { it.syncer.outcome = SyncOutcome.NeedsChoice(2); it.syncer.last = 5 }
        run(r)
        r.uid = "uid-B"; r.syncer.last = null
        r.service.onAccountChanged()
        assertEquals(SyncUiState(), r.service.state.value)
        r.now += 1       // and the spacing clock starts afresh for the new account
        assertEquals(SyncOutcome.NeedsChoice(2), run(r))
    }

    // ---------- texts and the job rules ----------

    @Test fun theLastSyncLine_saysHowLongAgo_neverALieAboutTheFuture() {
        val zone = ZoneId.of("Europe/Skopje")
        val now = ZonedDateTime.of(2026, 11, 25, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(SyncTexts.NEVER, SyncTexts.lastSynced(null, now, zone))
        assertEquals("Последна синхронизација: пред малку", SyncTexts.lastSynced(now - 10_000, now, zone))
        assertEquals("Последна синхронизација: пред малку", SyncTexts.lastSynced(now + 5 * 60_000, now, zone))
        assertEquals("Последна синхронизација: пред 5 мин", SyncTexts.lastSynced(now - 5 * 60_000, now, zone))
        assertEquals("Последна синхронизација: пред 3 ч", SyncTexts.lastSynced(now - 3 * 3600_000, now, zone))
        assertEquals("Последна синхронизација: 23.11.2026 14:00", SyncTexts.lastSynced(now - 2 * 24 * 3600_000, now, zone))
    }

    @Test fun theRetryJob_triesAgainOnlyForTroubleThatMayPass_andOnlyUntilTheLimit() {
        for (attempt in 0 until SyncWorkDecision.MAX_ATTEMPTS) {
            assertTrue(SyncWorkDecision.shouldRetry(SyncOutcome.Offline, attempt))
            assertTrue(SyncWorkDecision.shouldRetry(SyncOutcome.TryLater, attempt))
        }
        assertFalse(SyncWorkDecision.shouldRetry(SyncOutcome.Offline, SyncWorkDecision.MAX_ATTEMPTS))
        for (o in listOf(SyncOutcome.Uploaded, SyncOutcome.Downloaded, SyncOutcome.AlreadyInSync, SyncOutcome.NeedsChoice(1), SyncOutcome.TooLarge, SyncOutcome.NotSignedIn, SyncOutcome.Problem("x"), null)) {
            for (attempt in listOf(0, 3, 100)) assertFalse("$o at $attempt", SyncWorkDecision.shouldRetry(o, attempt))
        }
    }
}
