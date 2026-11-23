package com.mojrucek.app.session

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mojrucek.app.MojRucekApplication
import java.util.concurrent.TimeUnit

/** What the retry job should do after a refresh attempt. Pure, so it can be tested on its own. */
object WorkDecision {
    /** After this many tries the job stops; the next app start or account change tries again anyway. */
    const val MAX_ATTEMPTS = 8

    /** [attempt] is how many times the job has already run (0 on the first run). */
    fun shouldRetry(outcome: RefreshOutcome, attempt: Int): Boolean = outcome is RefreshOutcome.Retry && attempt < MAX_ATTEMPTS
}

/** What the job needs: the refresh logic and "who is signed in now". Replaceable in tests. */
class SessionWorkEnv(val refresher: SessionRefresher, val currentUid: () -> String?)

object SessionWorkEnvProvider {
    @Volatile
    var provider: (Context) -> SessionWorkEnv = { context ->
        val container = (context.applicationContext as MojRucekApplication).container
        SessionWorkEnv(container.sessionRepository) { container.authRepository.currentUid }
    }
}

/**
 * The background retry for a session refresh that could not be done when it was wanted (no internet yet, server
 * busy). It only runs when the phone has a connection, backs off between tries, and always refreshes whoever is
 * signed in at that moment (a previous account is never refreshed by mistake). Nothing is shown to the user.
 */
class SessionRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val env = SessionWorkEnvProvider.provider(applicationContext)
        val uid = env.currentUid() ?: return Result.success()   // nobody signed in: nothing to do
        val outcome = env.refresher.refresh(uid)
        return if (WorkDecision.shouldRetry(outcome, runAttemptCount)) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "session-refresh-retry"
    }
}

/** Plans the one retry job: only with a connection, exponential backoff, and never more than one waiting. */
class WorkManagerRetryScheduler(private val context: Context) : RetryScheduler {

    override fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<SessionRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SessionRefreshWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelRetry() {
        WorkManager.getInstance(context).cancelUniqueWork(SessionRefreshWorker.UNIQUE_NAME)
    }
}
