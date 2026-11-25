package com.mojrucek.app.di

import android.content.Context
import com.mojrucek.app.auth.AuthRepository
import com.mojrucek.app.data.AppDatabase
import com.mojrucek.app.repository.AccountSwitchRepository
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.CatalogSyncRepository
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.OwnershipClaimer
import com.mojrucek.app.repository.OwnershipCoordinator
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository
import com.mojrucek.app.repository.SharedPreferencesOwnershipPrefs
import com.mojrucek.app.session.GuestReminderCoordinator
import com.mojrucek.app.session.HttpSessionServer
import com.mojrucek.app.session.WorkManagerGuestReminders
import com.mojrucek.app.session.SessionRepository
import com.mojrucek.app.session.SessionTriggers
import com.mojrucek.app.session.SharedPreferencesSessionStore
import com.mojrucek.app.session.WorkManagerRetryScheduler

/**
 * Simple hand-rolled dependency container (no DI framework) — appropriate
 * for a single-screen-graph personal app, and one less third-party plugin
 * version to keep in sync.
 */
class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)

    val authRepository = AuthRepository(context.applicationContext)

    /** Whose data the repositories below read and change: always the signed-in account. */
    val currentOwner = CurrentOwner.from(authRepository.state)

    val ingredientRepository = IngredientRepository(database.ingredientDao(), currentOwner)
    val recipeRepository = RecipeRepository(database.recipeDao(), database.favoriteDao(), currentOwner)
    val settingsRepository = SettingsRepository(database.appSettingsDao())
    val catalogSyncRepository = CatalogSyncRepository(context, database)
    val shoppingListRepository = ShoppingListRepository(database.shoppingListDao(), ingredientRepository, currentOwner)
    val plannerRepository = PlannerRepository(
        database.plannerDao(),
        recipeRepository,
        ingredientRepository,
        shoppingListRepository,
        currentOwner
    )
    val backupRepository = BackupRepository(
        ingredientRepository,
        recipeRepository,
        shoppingListRepository,
        database.plannerDao(),
        settingsRepository,
        database,
        currentOwner
    )
    val accountSwitchRepository = AccountSwitchRepository(context.applicationContext, database, backupRepository, currentOwner)

    /** Tells the server who is signed in and remembers its answer per account (the guest's 14 days start there). */
    private val sessionStore = SharedPreferencesSessionStore(context)
    val sessionRepository = SessionRepository(HttpSessionServer(), sessionStore, authRepository)

    /** Server time now: the phone's clock corrected by the difference measured at the last server answer. */
    private fun serverNowMs(): Long {
        val offset = authRepository.currentUid?.let { sessionRepository.cached(it)?.clockOffsetMs } ?: 0L
        return System.currentTimeMillis() + offset
    }

    /** Recipe details the server has unlocked for the signed-in account (empty until a list is made). */
    val unlockedDetailsRepository = com.mojrucek.app.repository.UnlockedDetailsRepository(database.unlockedDetailsDao(), currentOwner, ::serverNowMs)

    private val limitStateStore = com.mojrucek.app.session.SharedPreferencesLimitStateStore(context)
    private val pendingListRequests = com.mojrucek.app.session.SharedPreferencesPendingRequests(context)

    /** Asks the server before a shopping list that involves server recipes is made (the Guest/Free/Paid rules). */
    val listPermission = com.mojrucek.app.session.ListPermission(
        com.mojrucek.app.session.HttpGenerationServer(), authRepository, unlockedDetailsRepository, limitStateStore, pendingListRequests
    )

    // Sync (docs/sync-plan.md): one snapshot per account on the server, decided by the coordinator, run by the service.
    private val syncStateStore = com.mojrucek.app.sync.SharedPreferencesSyncStateStore(context)
    private val syncSafetyCopy = com.mojrucek.app.sync.FileSafetyCopy(context)
    private val syncOwnershipPrefs = SharedPreferencesOwnershipPrefs(context)
    val syncScheduler = com.mojrucek.app.sync.WorkManagerSyncScheduler(context.applicationContext)
    private val syncCoordinator = com.mojrucek.app.sync.SyncCoordinator(
        com.mojrucek.app.sync.HttpSyncServer(), authRepository, com.mojrucek.app.sync.BackupSnapshotPort(backupRepository), syncSafetyCopy, syncStateStore
    )
    val syncService = com.mojrucek.app.sync.SyncService(
        syncCoordinator, syncScheduler, { authRepository.currentUid },
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
        canSync = { syncOwnershipPrefs.legacyState == com.mojrucek.app.repository.LegacyState.DONE }
    )

    private val pendingErasure = com.mojrucek.app.session.SharedPreferencesPendingErasure(context)
    private val localDataEraser = com.mojrucek.app.repository.LocalDataEraser(database)

    /** Deleting the signed-in account. [activityContext] is the Activity (the Google picker needs it); null only for finishing an interrupted deletion. */
    fun accountDeletion(activityContext: Context?, password: String? = null) = com.mojrucek.app.session.AccountDeletion(
        com.mojrucek.app.session.RealDeletionSteps(authRepository, com.mojrucek.app.session.HttpAccountServer(), localDataEraser, sessionStore, activityContext, limitStateStore, pendingListRequests, password, syncStateStore, syncSafetyCopy),
        pendingErasure
    )
    val guestReminders = GuestReminderCoordinator(WorkManagerGuestReminders(context.applicationContext))
    val sessionTriggers = SessionTriggers(sessionRepository, WorkManagerRetryScheduler(context.applicationContext))

    /** Decides whether the data from before accounts is handed over at once or the user is asked first. */
    val ownershipCoordinator = OwnershipCoordinator(
        unowned = OwnershipClaimer(database),
        prefs = SharedPreferencesOwnershipPrefs(context),
        afterClaim = { shoppingListRepository.migrateLegacyPurchasedItems() }
    )
}
