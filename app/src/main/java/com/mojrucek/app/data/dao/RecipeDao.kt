package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.relation.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

/**
 * Recipes come in two kinds: the shared server catalog (serverId set, the same for every account) and a
 * user's own (serverId null, owned). "Visible" below means: the catalog plus that owner's own recipes.
 */
@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes WHERE serverId IS NOT NULL OR ownerUid = :ownerUid ORDER BY name COLLATE NOCASE ASC")
    fun observeAllWithIngredients(ownerUid: String): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id AND (serverId IS NOT NULL OR ownerUid = :ownerUid)")
    fun observeWithIngredients(id: Long, ownerUid: String): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id AND (serverId IS NOT NULL OR ownerUid = :ownerUid)")
    suspend fun getWithIngredients(id: Long, ownerUid: String): RecipeWithIngredients?

    @Query("SELECT * FROM recipes WHERE id = :id AND (serverId IS NOT NULL OR ownerUid = :ownerUid)")
    suspend fun getById(id: Long, ownerUid: String): Recipe?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredients(ingredients: List<RecipeIngredient>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Long)

    @Transaction
    suspend fun replaceIngredients(recipeId: Long, ingredients: List<RecipeIngredient>) {
        deleteIngredientsForRecipe(recipeId)
        insertIngredients(ingredients)
    }

    /** Server catalog recipe lookup used by the catalog sync: shared data, so not owner-scoped. */
    @Query("SELECT * FROM recipes WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: Long): Recipe?

    @Query("DELETE FROM recipes WHERE serverId IS NULL AND isUserCreated = 0")
    suspend fun deleteUnsyncedBuiltIn()

    @Query("SELECT COUNT(*) FROM recipes WHERE serverId IS NOT NULL OR ownerUid = :ownerUid")
    suspend fun count(ownerUid: String): Int

    @Transaction
    @Query("SELECT * FROM recipes WHERE serverId IS NOT NULL OR ownerUid = :ownerUid")
    suspend fun getAllWithIngredientsOnce(ownerUid: String): List<RecipeWithIngredients>

    /** The owner's own recipes (their ingredient lines go with them). Server catalog recipes are kept. */
    @Query("DELETE FROM recipes WHERE serverId IS NULL AND ownerUid = :ownerUid")
    suspend fun deleteLocalOnly(ownerUid: String)
}
