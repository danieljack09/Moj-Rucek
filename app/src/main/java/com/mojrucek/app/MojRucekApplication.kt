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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

        // Whenever the signed-in account (or its type) changes, and once at start: tell the server who it is, when needed.
        // A newer change cancels an attempt still in progress.
        applicationScope.launch {
            container.authRepository.state
                .map { it.uid to it.kind }
                .distinctUntilChanged()
                .collectLatest { (uid, kind) -> container.sessionTriggers.onAccount(uid, kind) }
        }

        // A deletion that was interrupted after the server record was removed: finish erasing the phone data.
        applicationScope.launch { container.accountDeletion(null).finishInterrupted() }

        // Keep the guest-period reminders in step with the account and the remembered answer.
        applicationScope.launch {
            combine(container.authRepository.state.map { it.uid }.distinctUntilChanged(), container.sessionRepository.changes) { uid, _ -> uid }
                .collectLatest { uid -> container.guestReminders.onSession(uid, uid?.let { container.sessionRepository.cached(it) }) }
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
