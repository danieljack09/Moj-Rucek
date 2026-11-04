package com.mojrucek.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mojrucek.app.MojRucekApplication
import kotlinx.coroutines.flow.first

/**
 * Daily check for ingredients whose expiration date is coming up soon
 * (within [EXPIRING_WITHIN_DAYS] days). If any are found, shows one
 * notification naming them, so food doesn't quietly go to waste.
 */
class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val EXPIRING_WITHIN_DAYS = 3L
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? MojRucekApplication ?: return Result.success()
        val expiringSoon = app.container.ingredientRepository
            .observeExpiringSoon(withinDays = EXPIRING_WITHIN_DAYS)
            .first()

        if (expiringSoon.isNotEmpty()) {
            NotificationHelper.showExpiringIngredientsReminder(
                applicationContext,
                expiringSoon.map { it.name }
            )
        }

        return Result.success()
    }
}
