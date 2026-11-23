package com.mojrucek.app.session

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The background retry job, with a test WorkManager (in memory, so the app's real scheduled jobs are untouched) and a
 * fake refresher (so no server is involved).
 */
@RunWith(AndroidJUnit4::class)
class SessionWorkInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val originalProvider = SessionWorkEnvProvider.provider

    private class FakeRefresher(var outcome: RefreshOutcome = RefreshOutcome.Updated(StoredSession("u", "GUEST", "GUEST", null, null, 0, 0))) : SessionRefresher {
        val refreshed = mutableListOf<String>()
        override suspend fun refresh(uid: String): RefreshOutcome { refreshed += uid; return outcome }
        override suspend fun refreshIfStale(uid: String, expectedKind: String?): RefreshOutcome { refreshed += "stale:$uid"; return outcome }
    }

    private val refresher = FakeRefresher()
    private var signedIn: String? = "uid-X"

    @Before fun setUp() {
        SessionWorkEnvProvider.provider = { SessionWorkEnv(refresher) { signedIn } }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @After fun tearDown() { SessionWorkEnvProvider.provider = originalProvider }

    private fun infos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(SessionRefreshWorker.UNIQUE_NAME).get()

    /** A job runs on a background thread, so its final status shows up a moment after it did its work: wait for it. */
    private fun awaitState(expected: WorkInfo.State, timeoutMs: Long = 5000): WorkInfo.State {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (infos().singleOrNull()?.state != expected && System.currentTimeMillis() < deadline) Thread.sleep(25)
        return infos().single().state
    }

    private fun doWork(attempt: Int = 0): ListenableWorker.Result = runBlocking {
        val worker = TestListenableWorkerBuilder<SessionRefreshWorker>(context).setRunAttemptCount(attempt).build()
        (worker as CoroutineWorker).doWork()
    }

    // ---------- planning the job ----------

    @Test fun planning_createsOneJob_thatWaitsForAConnection_andBacksOffExponentially() {
        WorkManagerRetryScheduler(context).scheduleRetry()

        val job = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        assertTrue("nothing ran yet: it waits for internet", refresher.refreshed.isEmpty())
    }

    @Test fun planningManyTimes_stillLeavesOneJob() {
        val scheduler = WorkManagerRetryScheduler(context)
        repeat(5) { scheduler.scheduleRetry() }

        assertEquals(1, infos().count { it.state == WorkInfo.State.ENQUEUED })
        assertEquals(1, infos().size)
    }

    @Test fun cancelling_removesThePendingJob() {
        val scheduler = WorkManagerRetryScheduler(context)
        scheduler.scheduleRetry()
        scheduler.cancelRetry()

        assertTrue(infos().all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(refresher.refreshed.isEmpty())
    }

    @Test fun whenTheConnectionComes_theJobRunsOnce_andRefreshesWhoIsSignedInThen() {
        WorkManagerRetryScheduler(context).scheduleRetry()
        val id = infos().single().id

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)

        assertEquals(WorkInfo.State.SUCCEEDED, awaitState(WorkInfo.State.SUCCEEDED))
        assertEquals(listOf("uid-X"), refresher.refreshed)
    }

    @Test fun afterAJobFinished_aNewOneCanBePlanned() {
        val scheduler = WorkManagerRetryScheduler(context)
        scheduler.scheduleRetry()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(infos().single().id)
        awaitState(WorkInfo.State.SUCCEEDED)

        scheduler.scheduleRetry()

        assertTrue(infos().any { it.state == WorkInfo.State.ENQUEUED })
    }

    // ---------- what the job does ----------

    @Test fun nobodySignedIn_theJobDoesNothingAndEnds() {
        signedIn = null

        assertEquals(ListenableWorker.Result.success(), doWork())
        assertTrue(refresher.refreshed.isEmpty())
    }

    @Test fun itRefreshesTheAccountThatIsSignedInWhenItRuns() {
        signedIn = "uid-SECOND"

        doWork()

        assertEquals(listOf("uid-SECOND"), refresher.refreshed)
    }

    @Test fun temporaryTrouble_makesTheJobTryAgainLater_untilTheLimit() {
        refresher.outcome = RefreshOutcome.Retry("no connection")

        assertEquals(ListenableWorker.Result.retry(), doWork(attempt = 0))
        assertEquals(ListenableWorker.Result.retry(), doWork(attempt = WorkDecision.MAX_ATTEMPTS - 1))
        assertEquals("after the limit it stops; the next start tries again anyway", ListenableWorker.Result.success(), doWork(attempt = WorkDecision.MAX_ATTEMPTS))
    }

    @Test fun everyOtherOutcome_endsTheJobSuccessfully() {
        for (outcome in listOf(RefreshOutcome.Updated(StoredSession("u", "GUEST", "GUEST", null, null, 0, 0)), RefreshOutcome.Fresh,
            RefreshOutcome.GaveUp("refused"), RefreshOutcome.NoToken, RefreshOutcome.AccountChanged)) {
            refresher.outcome = outcome
            assertEquals("$outcome", ListenableWorker.Result.success(), doWork())
        }
    }
}
