package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Hands data that has no owner yet (ownerUid = "": everything created before accounts owned data) to the
 * first account that signs in. Only rows with NO owner are touched; another account's rows never are.
 * Each function returns how many rows it handed over.
 *
 * "OR IGNORE" on the tables with a per-owner uniqueness rule: if the new owner already has a row for the
 * same week / the same favourite, the unowned duplicate is left behind instead of failing the whole claim.
 */
@Dao
interface OwnershipDao {

    @Query("UPDATE ingredients SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimIngredients(uid: String): Int

    @Query("UPDATE shopping_list_items SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimShoppingItems(uid: String): Int

    @Query("UPDATE OR IGNORE weekly_plans SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimWeeklyPlans(uid: String): Int

    /** Only the user's own recipes; the shared server catalog recipes never get an owner. */
    @Query("UPDATE recipes SET ownerUid = :uid WHERE ownerUid = '' AND serverId IS NULL")
    suspend fun claimOwnRecipes(uid: String): Int

    @Query("UPDATE OR IGNORE recipe_favorites SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimFavorites(uid: String): Int

    @Query(
        "SELECT (SELECT COUNT(*) FROM ingredients WHERE ownerUid = '') + " +
            "(SELECT COUNT(*) FROM shopping_list_items WHERE ownerUid = '') + " +
            "(SELECT COUNT(*) FROM weekly_plans WHERE ownerUid = '') + " +
            "(SELECT COUNT(*) FROM recipes WHERE ownerUid = '' AND serverId IS NULL) + " +
            "(SELECT COUNT(*) FROM recipe_favorites WHERE ownerUid = '')"
    )
    suspend fun countUnowned(): Int
}
