package com.mojrucek.app.di

import android.content.Context
import com.mojrucek.app.data.AppDatabase
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository

/**
 * Simple hand-rolled dependency container (no DI framework) — appropriate
 * for a single-screen-graph personal app, and one less third-party plugin
 * version to keep in sync.
 */
class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)

    val ingredientRepository = IngredientRepository(database.ingredientDao())
    val recipeRepository = RecipeRepository(database.recipeDao())
    val settingsRepository = SettingsRepository(database.appSettingsDao())
    val shoppingListRepository = ShoppingListRepository(database.shoppingListDao(), ingredientRepository)
    val plannerRepository = PlannerRepository(
        database.plannerDao(),
        recipeRepository,
        ingredientRepository,
        shoppingListRepository
    )
    val backupRepository = BackupRepository(
        ingredientRepository,
        recipeRepository,
        shoppingListRepository,
        database.plannerDao(),
        settingsRepository
    )
}
