package com.mojrucek.app.repository

import androidx.room.withTransaction
import com.mojrucek.app.data.AppDatabase

/**
 * Erases ONE account's data from this phone, in one transaction: plans (their meals cascade), shopping list, pantry,
 * the account's own recipes (ingredient lines cascade) and favourites. Rows of other accounts, data from before
 * accounts, settings and the server catalog are never touched.
 */
class LocalDataEraser(private val database: AppDatabase) {
    suspend fun eraseAccount(ownerKey: String) {
        require(OwnerKeys.isAccount(ownerKey)) { "only a real account can be erased" }
        database.withTransaction {
            database.plannerDao().deleteAllPlans(ownerKey)
            database.shoppingListDao().deleteAll(ownerKey)
            database.ingredientDao().deleteAll(ownerKey)
            database.recipeDao().deleteLocalOnly(ownerKey)
            database.favoriteDao().removeAll(ownerKey)
        }
    }
}
