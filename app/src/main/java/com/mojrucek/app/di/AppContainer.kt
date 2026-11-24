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

    private val pendingErasure = com.mojrucek.app.session.SharedPreferencesPendingErasure(context)
    private val localDataEraser = com.mojrucek.app.repository.LocalDataEraser(database)

    /** Deleting the signed-in account. [activityContext] is the Activity (the Google picker needs it); null only for finishing an interrupted deletion. */
    fun accountDeletion(activityContext: Context?) = com.mojrucek.app.session.AccountDeletion(
        com.mojrucek.app.session.RealDeletionSteps(authRepository, com.mojrucek.app.session.HttpAccountServer(), localDataEraser, sessionStore, activityContext),
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
