package com.mojrucek.app.session

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mojrucek.app.MojRucekApplication
import com.mojrucek.app.notification.NotificationHelper
import java.util.concurrent.TimeUnit

/** What the reminder job needs; replaceable in tests. */
class GuestReminderEnv(val currentUid: () -> String?, val session: (String) -> StoredSession?, val notify: (daysBefore: Int) -> Unit)

object GuestReminderEnvProvider {
    @Volatile
    var provider: (Context) -> GuestReminderEnv = { context ->
        val container = (context.applicationContext as MojRucekApplication).container
        GuestReminderEnv(
            currentUid = { container.authRepository.currentUid },
            session = { container.sessionRepository.cached(it) },
            notify = { NotificationHelper.showGuestPeriodReminder(context.applicationContext, it) }
        )
    }
}

/** Shows one "guest period ends soon" notification, only if it is still right at that moment. */
class GuestReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.success()
        val expires = inputData.getLong(KEY_EXPIRES, 0)
        val days = inputData.getInt(KEY_DAYS, 0)
        val env = GuestReminderEnvProvider.provider(applicationContext)
        if (GuestReminderDecision.shouldNotify(env.currentUid(), uid, env.session(uid), expires, System.currentTimeMillis())) env.notify(days)
        return Result.success()
    }

    companion object {
        const val KEY_UID = "uid"
        const val KEY_EXPIRES = "expires"
        const val KEY_DAYS = "days"
        fun uniqueName(daysBefore: Int) = "guest-reminder-$daysBefore"
    }
}

class WorkManagerGuestReminders(private val context: Context, private val nowMs: () -> Long = System::currentTimeMillis) : GuestReminderScheduling {
    override fun schedule(uid: String, expiresAtMs: Long, daysBefore: Int, atEpochMs: Long) {
        val delay = (atEpochMs - nowMs()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<GuestReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(GuestReminderWorker.KEY_UID to uid, GuestReminderWorker.KEY_EXPIRES to expiresAtMs, GuestReminderWorker.KEY_DAYS to daysBefore))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(GuestReminderWorker.uniqueName(daysBefore), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelAll() {
        for (d in com.mojrucek.app.domain.session.GuestPeriod.REMINDER_DAYS_BEFORE) WorkManager.getInstance(context).cancelUniqueWork(GuestReminderWorker.uniqueName(d))
    }
}
