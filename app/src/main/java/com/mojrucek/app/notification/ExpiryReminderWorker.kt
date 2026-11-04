package com.mojrucek.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mojrucek.app.MojRucekApplication
import com.mojrucek.app.domain.usecase.WeekCalculator
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs once a day and notifies about ingredients that are already expired or
 * expire within [WARN_DAYS] days.
 */
class ExpiryReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MojRucekApplication ?: return Result.success()
        val today = WeekCalculator.today().toEpochDay()

        val lines = app.container.ingredientRepository.getAllOnce()
            .mapNotNull { ingredient ->
                val exp = ingredient.expirationDateEpochDay ?: return@mapNotNull null
                val days = exp - today
                if (days > WARN_DAYS) return@mapNotNull null
                days to ingredient.name
            }
            .sortedBy { it.first }
            .map { (days, name) ->
                val status = when {
                    days < 0 -> "истечено"
                    days == 0L -> "истекува денес"
                    days == 1L -> "истекува утре"
                    else -> "истекува за $days дена"
                }
                "$name – $status"
            }

        NotificationHelper.showExpiryReminder(applicationContext, lines)
        return Result.success()
    }

    companion object {
        const val WARN_DAYS = 2L
    }
}

object ExpiryReminderScheduler {

    private const val UNIQUE_WORK_NAME = "mojrucek_expiry_reminder"
    private const val HOUR_OF_DAY = 9

    /** Daily check at roughly [HOUR_OF_DAY]:00 local time; KEEP leaves an existing schedule alone. */
    fun schedule(context: Context) {
        val now = ZonedDateTime.now(WeekCalculator.ZONE)
        var next = now.withHour(HOUR_OF_DAY).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
