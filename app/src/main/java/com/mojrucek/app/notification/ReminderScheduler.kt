package com.mojrucek.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.domain.usecase.WeekCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Schedules the planning reminder as a self-rescheduling one-time
 * WorkManager job, anchored to Europe/Skopje, rather than a plain periodic
 * request — that keeps it on the correct day(s) and the user's chosen time
 * of day even across Doze/time-zone edge cases.
 */
object ReminderScheduler {

    const val UNIQUE_WORK_NAME = "mojrucek_sunday_reminder"

    fun scheduleReminder(context: Context, settings: AppSettings) {
        if (!settings.reminderEnabled) {
            cancel(context)
            return
        }

        val now = ZonedDateTime.now(WeekCalculator.ZONE)
        val next = when (settings.reminderMode) {
            AppSettings.ReminderMode.DAILY -> nextDailyOccurrence(now, settings)
            else -> nextOccurrence(now, DayOfWeek.of(settings.reminderWeeklyDay), settings)
        }

        if (next == null) {
            cancel(context)
            return
        }

        val delayMillis = Duration.between(now, next).toMillis()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun nextDailyOccurrence(now: ZonedDateTime, settings: AppSettings): ZonedDateTime? {
        if (settings.reminderDays.isEmpty()) return null
        return settings.reminderDays
            .map { nextOccurrence(now, DayOfWeek.of(it), settings) }
            .minByOrNull { it.toEpochSecond() }
    }

    private fun nextOccurrence(now: ZonedDateTime, day: DayOfWeek, settings: AppSettings): ZonedDateTime {
        var next = now
            .with(TemporalAdjusters.nextOrSame(day))
            .withHour(settings.reminderHour)
            .withMinute(settings.reminderMinute)
            .withSecond(0)
            .withNano(0)

        if (next.isBefore(now) || next.isEqual(now)) {
            next = next.plusWeeks(1)
        }

        return next
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
