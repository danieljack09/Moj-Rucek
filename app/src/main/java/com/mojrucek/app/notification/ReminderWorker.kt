package com.mojrucek.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mojrucek.app.MojRucekApplication

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.showSundayReminder(applicationContext)

        val app = applicationContext as? MojRucekApplication
        if (app != null) {
            val settings = app.container.settingsRepository.getOnce()
            // Reschedule for next Sunday so the reminder keeps recurring.
            ReminderScheduler.scheduleWeeklyReminder(applicationContext, settings)
        }

        return Result.success()
    }
}
