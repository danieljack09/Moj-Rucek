package com.mojrucek.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mojrucek.app.domain.usecase.WeekCalculator
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules a daily background check (via [ExpiryCheckWorker]) for
 * ingredients that are about to expire. Independent of the weekly planning
 * reminder, and always on — there's no setting to disable it since an
 * about-to-expire ingredient is useful to know about regardless.
 */
object ExpiryReminderScheduler {

    const val UNIQUE_WORK_NAME = "mojrucek_expiry_check"
    private const val CHECK_HOUR = 9

    fun scheduleDailyCheck(context: Context) {
        val now = ZonedDateTime.now(WeekCalculator.ZONE)
        var firstRun = now.withHour(CHECK_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (!firstRun.isAfter(now)) firstRun = firstRun.plusDays(1)
        val initialDelayMillis = Duration.between(now, firstRun).toMillis()

        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
