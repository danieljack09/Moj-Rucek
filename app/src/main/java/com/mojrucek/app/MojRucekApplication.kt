package com.mojrucek.app

import android.app.Application
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.notification.ExpiryReminderScheduler
import com.mojrucek.app.notification.NotificationHelper
import com.mojrucek.app.notification.ReminderScheduler
import com.mojrucek.app.repository.OwnerKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MojRucekApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        ExpiryReminderScheduler.scheduleDailyCheck(this)

        applicationScope.launch {
            container.settingsRepository.ensureInitialized()
            // Recipes come only from the server now; drop the sample recipes older
            // versions of the app built in. The user's own recipes are kept.
            container.recipeRepository.deleteLegacyBuiltIn()
            container.catalogSyncRepository.sync()
            val settings = container.settingsRepository.getOnce()
            if (settings.reminderEnabled) {
                ReminderScheduler.scheduleReminder(this@MojRucekApplication, settings)
            }
        }

        // Whenever the current account changes: decide about the data from before accounts (hand it over or ask
        // first), then tidy up shopping items left over from older versions.
        applicationScope.launch {
            container.currentOwner.flow.collect { owner ->
                container.ownershipCoordinator.onOwnerChanged(owner)
                if (OwnerKeys.isAccount(owner)) container.shoppingListRepository.migrateLegacyPurchasedItems()
            }
        }
    }
}
