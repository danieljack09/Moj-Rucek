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
        // Sync also when the user leaves the app, and every 12 hours with internet.
        registerActivityLifecycleCallbacks(com.mojrucek.app.sync.BackgroundTrigger { container.syncService.requestSync(manual = false) })
        container.syncScheduler.schedulePeriodic()
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
                .map { Triple(it.uid, it.kind, it.emailVerified) }
                .distinctUntilChanged()
                .collectLatest { (uid, kind, verified) ->
                    container.syncService.onAccountChanged()
                    container.sessionTriggers.onAccount(uid, kind, verified)
                    // Quietly learn where the account stands with the list rules and restore what is unlocked.
                    if (uid != null) {
                        container.unlockedDetailsRepository.pruneExpired()
                        container.listPermission.refreshState()
                        // Then bring the account's data in step with the server (quiet; a conflict is only reported).
                        container.syncService.syncNow(manual = false)
                    }
                }
        }

        // A deletion that was interrupted after the server record was removed: finish erasing the phone data.
        applicationScope.launch { container.accountDeletion(null).finishInterrupted() }

        // An email account whose address was confirmed meanwhile (the link was opened on another device): notice it quietly.
        applicationScope.launch {
            val account = container.authRepository.state.value
            if (account.kind == com.mojrucek.app.auth.AccountKind.EMAIL && !account.emailVerified) {
                container.authRepository.refreshEmailVerification()
            }
        }

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
                // Sync waits until the data from before accounts has been settled; now it may run.
                container.syncService.syncNow(manual = false)
                if (OwnerKeys.isAccount(owner)) container.shoppingListRepository.migrateLegacyPurchasedItems()
            }
        }
    }
}
