package com.mojrucek.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.domain.usecase.WeekCalculator
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Schedules the Sunday planning reminder as a self-rescheduling one-time
 * WorkManager job, anchored to Europe/Skopje, rather than a plain periodic
 * request — that keeps it on the correct day and the user's chosen time of
 * day even across Doze/time-zone edge cases.
 */
object ReminderScheduler {

    const val UNIQUE_WORK_NAME = "mojrucek_sunday_reminder"

    fun scheduleWeeklyReminder(context: Context, settings: AppSettings) {
        if (!settings.sundayReminderEnabled) {
            cancel(context)
            return
        }

        val now = ZonedDateTime.now(WeekCalculator.ZONE)
        var nextSunday = now
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .withHour(settings.sundayReminderHour)
            .withMinute(settings.sundayReminderMinute)
            .withSecond(0)
            .withNano(0)

        if (nextSunday.isBefore(now) || nextSunday.isEqual(now)) {
            nextSunday = nextSunday.plusWeeks(1)
        }

        val delayMillis = java.time.Duration.between(now, nextSunday).toMillis()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
