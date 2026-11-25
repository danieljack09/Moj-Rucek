package com.mojrucek.app.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mojrucek.app.MojRucekApplication
import com.mojrucek.app.session.RetryScheduler
import java.util.concurrent.TimeUnit

/** What the sync jobs do when they run; replaceable in tests. Returns how the run ended, or null when it was skipped. */
fun interface SyncRun {
    suspend fun run(): SyncOutcome?
}

object SyncWorkEnvProvider {
    @Volatile
    var provider: (Context) -> SyncRun = { context ->
        val service = (context.applicationContext as MojRucekApplication).container.syncService
        SyncRun { service.syncNow(manual = true) }
    }
}

/** What a background job should do after a run. Pure, so it can be tested on its own. */
object SyncWorkDecision {
    /** After this many tries the retry job stops; the next start, leaving the app or the 12-hour job tries again anyway. */
    const val MAX_ATTEMPTS = 8

    fun shouldRetry(outcome: SyncOutcome?, attempt: Int): Boolean =
        (outcome == SyncOutcome.Offline || outcome == SyncOutcome.TryLater) && attempt < MAX_ATTEMPTS
}

/** The one retry for a run that could not reach the server: waits for internet, backs off, then syncs whoever is signed in. */
class SyncRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = SyncWorkEnvProvider.provider(applicationContext).run()
        return if (SyncWorkDecision.shouldRetry(outcome, runAttemptCount)) Result.retry() else Result.success()
    }

    companion object { const val UNIQUE_NAME = "sync-retry" }
}

/** The regular run every 12 hours (with internet). Failures are not retried here: the next run, or the retry job, covers it. */
class SyncPeriodicWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SyncWorkEnvProvider.provider(applicationContext).run()
        return Result.success()
    }

    companion object { const val UNIQUE_NAME = "sync-periodic" }
}

class WorkManagerSyncScheduler(private val context: Context) : RetryScheduler {
    private val needsInternet = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<SyncRetryWorker>()
            .setConstraints(needsInternet)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SyncRetryWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelRetry() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncRetryWorker.UNIQUE_NAME)
    }

    /** Plans the 12-hour run once; calling it again at every start keeps the existing one. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncPeriodicWorker>(12, TimeUnit.HOURS)
            .setConstraints(needsInternet)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SyncPeriodicWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Calls [onBackground] when the last screen of the app goes away (the user left the app). */
class BackgroundTrigger(private val onBackground: () -> Unit) : Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        // A rotation stops and restarts the screen: only a real "left the app" counts.
        if (started == 0 && !activity.isChangingConfigurations) onBackground()
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
