package com.mojrucek.app.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** The sync jobs with an in-memory test WorkManager (the app's real jobs are untouched) and a fake sync run. */
@RunWith(AndroidJUnit4::class)
class SyncWorkInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val originalProvider = SyncWorkEnvProvider.provider
    private var runs = 0
    private var answer: SyncOutcome? = SyncOutcome.AlreadyInSync
    private val tempDirs = mutableListOf<File>()

    @Before fun setUp() {
        SyncWorkEnvProvider.provider = { SyncRun { runs++; answer } }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @After fun tearDown() { SyncWorkEnvProvider.provider = originalProvider; tempDirs.forEach { it.deleteRecursively() } }

    private fun infos(name: String) = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private inline fun <reified W : CoroutineWorker> doWork(attempt: Int = 0): ListenableWorker.Result = runBlocking {
        (TestListenableWorkerBuilder<W>(context).setRunAttemptCount(attempt).build() as CoroutineWorker).doWork()
    }

    // ---------- planning ----------

    @Test fun theRetry_isOneJob_thatWaitsForInternet() {
        val scheduler = WorkManagerSyncScheduler(context)
        repeat(4) { scheduler.scheduleRetry() }
        val job = infos(SyncRetryWorker.UNIQUE_NAME).single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        assertEquals("nothing ran yet", 0, runs)
    }

    @Test fun cancellingTheRetry_removesIt() {
        val scheduler = WorkManagerSyncScheduler(context)
        scheduler.scheduleRetry(); scheduler.cancelRetry()
        assertTrue(infos(SyncRetryWorker.UNIQUE_NAME).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test fun theRegularRun_isPlannedOnce_every12Hours_withInternet() {
        val scheduler = WorkManagerSyncScheduler(context)
        repeat(3) { scheduler.schedulePeriodic() }            // every app start asks again
        val job = infos(SyncPeriodicWorker.UNIQUE_NAME).single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        val spec = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncPeriodicWorker.UNIQUE_NAME).get().single()
        assertTrue(spec.id != null)
    }

    @Test fun whenTheConnectionComes_theRetryRuns_andFinishes() {
        WorkManagerSyncScheduler(context).scheduleRetry()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(infos(SyncRetryWorker.UNIQUE_NAME).single().id)
        val deadline = System.currentTimeMillis() + 5000
        while (infos(SyncRetryWorker.UNIQUE_NAME).single().state != WorkInfo.State.SUCCEEDED && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertEquals(WorkInfo.State.SUCCEEDED, infos(SyncRetryWorker.UNIQUE_NAME).single().state)
        assertEquals(1, runs)
    }

    // ---------- what the jobs do ----------

    @Test fun theRetry_triesAgainForTroubleThatMayPass_untilTheLimit() {
        answer = SyncOutcome.Offline
        assertEquals(ListenableWorker.Result.retry(), doWork<SyncRetryWorker>(0))
        assertEquals(ListenableWorker.Result.retry(), doWork<SyncRetryWorker>(SyncWorkDecision.MAX_ATTEMPTS - 1))
        assertEquals(ListenableWorker.Result.success(), doWork<SyncRetryWorker>(SyncWorkDecision.MAX_ATTEMPTS))
    }

    @Test fun everyOtherOutcome_endsTheRetrySuccessfully() {
        for (o in listOf(SyncOutcome.Uploaded, SyncOutcome.Downloaded, SyncOutcome.AlreadyInSync, SyncOutcome.NeedsChoice(2), SyncOutcome.TooLarge, SyncOutcome.Problem("x"), SyncOutcome.NotSignedIn, null)) {
            answer = o
            assertEquals("$o", ListenableWorker.Result.success(), doWork<SyncRetryWorker>())
        }
    }

    @Test fun theRegularRun_alwaysSucceeds_evenWhenTheServerCouldNotBeReached() {
        answer = SyncOutcome.Offline
        assertEquals(ListenableWorker.Result.success(), doWork<SyncPeriodicWorker>())
        assertEquals(1, runs)
    }

    // ---------- leaving the app ----------

    @Test fun leavingTheApp_isNoticedOnlyWhenTheLastScreenGoesAway() {
        var left = 0
        // An Activity can only be created on the main thread.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val trigger = BackgroundTrigger { left++ }
            val a = Activity(); val b = Activity()
            trigger.onActivityStarted(a)
            trigger.onActivityStarted(b)          // a second screen opens
            trigger.onActivityStopped(a)
            assertEquals("one screen is still showing", 0, left)
            trigger.onActivityStopped(b)
            assertEquals(1, left)
            trigger.onActivityStarted(a)          // the user comes back and leaves again
            trigger.onActivityStopped(a)
            assertEquals(2, left)
        }
    }

    // ---------- safety copies belong to an account ----------

    private fun safety(): Pair<FileSafetyCopy, File> {
        val dir = File(context.cacheDir, "sync-work-safety-${System.nanoTime()}").apply { mkdirs() }.also { tempDirs += it }
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir(): File = dir }
        return FileSafetyCopy(wrapped, keep = 2) to File(dir, FileSafetyCopy.DIR)
    }

    @Test fun deletingAnAccountsCopies_leavesOtherAccountsCopies() {
        val (copy, dir) = safety()
        assertTrue(copy.save("uid-A", """{"formatVersion":1,"who":"A1"}"""))
        Thread.sleep(5)
        assertTrue(copy.save("uid-B", """{"formatVersion":1,"who":"B1"}"""))
        copy.deleteAll("uid-A")
        val left = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.readText() }
        assertEquals(listOf("""{"formatVersion":1,"who":"B1"}"""), left)
        copy.deleteAll("uid-never-had-any")
        assertEquals(1, dir.listFiles { f -> f.name.endsWith(".json") }!!.size)
    }

    @Test fun pruning_isPerAccount_soBusyAccountsDoNotPushOutAnotherOnesCopies() {
        val (copy, dir) = safety()
        copy.save("uid-B", """{"formatVersion":1,"who":"B"}""")
        repeat(5) { i -> Thread.sleep(5); copy.save("uid-A", """{"formatVersion":1,"a":$i}""") }
        val all = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.readText() }
        assertTrue("B's only copy is still there", all.any { it.contains("\"who\":\"B\"") })
        assertEquals("A keeps only the newest two", 2, all.count { it.contains("\"a\":") })
        assertFalse(all.any { it.contains("\"a\":0") })
    }
}
