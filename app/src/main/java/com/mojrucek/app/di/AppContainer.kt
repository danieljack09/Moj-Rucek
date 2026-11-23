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

    /** Decides whether the data from before accounts is handed over at once or the user is asked first. */
    val ownershipCoordinator = OwnershipCoordinator(
        unowned = OwnershipClaimer(database),
        prefs = SharedPreferencesOwnershipPrefs(context),
        afterClaim = { shoppingListRepository.migrateLegacyPurchasedItems() }
    )
}
