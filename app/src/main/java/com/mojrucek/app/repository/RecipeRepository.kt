package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.FavoriteDao
import com.mojrucek.app.data.dao.RecipeDao
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeFavorite
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.newUuid
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.domain.usecase.RecipeMatchingUseCase
import com.mojrucek.app.domain.usecase.ServingScaler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Recipes the signed-in account can see: the shared server catalog plus its own recipes. Every call goes
 * through [owner]; server catalog recipes never get an owner, a user's own recipes always do.
 *
 * Favourites are per account: [Recipe.isFavorite] of every recipe handed out here is filled in from the signed-in
 * account's own favourites (the old flag stored on the recipe row is no longer read), so no screen needs to know.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRepository(
    private val dao: RecipeDao,
    private val favoriteDao: FavoriteDao,
    private val owner: CurrentOwner
) {

    private fun withFavorites(recipe: RecipeWithIngredients, favorites: Set<String>) =
        recipe.copy(recipe = recipe.recipe.copy(isFavorite = FavoriteRefs.of(recipe.recipe) in favorites))

    private fun withFavorites(recipes: List<RecipeWithIngredients>, favorites: Set<String>) =
        recipes.map { withFavorites(it, favorites) }

    fun observeAllWithIngredients(): Flow<List<RecipeWithIngredients>> = owner.flow.flatMapLatest { key ->
        combine(dao.observeAllWithIngredients(key), favoriteDao.observeRefs(key)) { recipes, refs ->
            withFavorites(recipes, refs.toSet())
        }
    }

    fun observeWithIngredients(id: Long): Flow<RecipeWithIngredients?> = owner.flow.flatMapLatest { key ->
        combine(dao.observeWithIngredients(id, key), favoriteDao.observeRefs(key)) { recipe, refs ->
            recipe?.let { withFavorites(it, refs.toSet()) }
        }
    }

    suspend fun getWithIngredients(id: Long): RecipeWithIngredients? {
        val key = owner.key
        return dao.getWithIngredients(id, key)?.let { withFavorites(it, favoriteDao.getRefs(key).toSet()) }
    }

    suspend fun upsert(recipe: Recipe, ingredients: List<RecipeIngredient>): Long {
        // A recipe from the server catalog is shared (no owner); the user's own recipe belongs to the signed-in account.
        val stamped = recipe.copy(ownerUid = if (recipe.serverId == null) owner.key else OwnerKeys.UNOWNED)
        val recipeId = if (stamped.id == 0L) dao.insertRecipe(stamped) else {
            dao.updateRecipe(stamped)
            stamped.id
        }
        val withIds = ingredients.mapIndexed { index, ri ->
            ri.copy(
                id = 0,
                recipeId = recipeId,
                normalizedName = RecipeIngredient.normalize(ri.name),
                sortOrder = index
            )
        }
        dao.replaceIngredients(recipeId, withIds)
        return recipeId
    }

    suspend fun delete(recipe: Recipe) {
        dao.deleteRecipe(recipe)
        favoriteDao.remove(owner.key, FavoriteRefs.of(recipe))   // no favourite mark for a recipe that is gone
    }

    /** Marks or unmarks a recipe as a favourite of the signed-in account only. */
    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        val key = owner.key
        val recipe = dao.getById(id, key) ?: return
        val ref = FavoriteRefs.of(recipe)
        if (isFavorite) favoriteDao.upsert(RecipeFavorite(ownerUid = key, recipeRef = ref)) else favoriteDao.remove(key, ref)
    }

    /** Makes exactly these recipes the signed-in account's favourites (used by restore). */
    suspend fun replaceFavorites(favoriteRecipeIds: Collection<Long>) {
        val key = owner.key
        favoriteDao.removeAll(key)
        favoriteRecipeIds.forEach { id -> dao.getById(id, key)?.let { favoriteDao.upsert(RecipeFavorite(ownerUid = key, recipeRef = FavoriteRefs.of(it))) } }
    }

    /** Creates a copy of [source] (and its ingredients) named "<name> (копија)", ready to edit. */
    suspend fun duplicate(source: RecipeWithIngredients): Long {
        val copy = source.recipe.copy(
            id = 0,
            name = "${source.recipe.name} (копија)",
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            uuid = newUuid(),
            // The copy is the user's own: a sync must never overwrite or delete it.
            isUserCreated = true,
            serverId = null
        )
        val copiedIngredients = source.ingredients.map { it.copy(id = 0, recipeId = 0) }
        return upsert(copy, copiedIngredients)
    }

    /** Scales a recipe's ingredients from its base servings to [desiredServings]. */
    fun scaledIngredients(recipe: RecipeWithIngredients, desiredServings: Int): List<IngredientLine> {
        val lines = recipe.ingredients.map {
            IngredientLine(it.name, it.normalizedName, it.quantity, it.unit)
        }
        return ServingScaler.scale(lines, recipe.recipe.servings.coerceAtLeast(1), desiredServings)
    }

    /** Matches every recipe against [inventory], each scaled to its own requested serving count (defaults to the recipe's own). */
    fun matchAll(
        recipes: List<RecipeWithIngredients>,
        inventory: List<Ingredient>,
        desiredServingsByRecipeId: Map<Long, Int> = emptyMap()
    ): List<RecipeMatchResult> {
        val inventoryLines = inventory.map { IngredientLine(it.name, it.normalizedName, it.quantity, it.unit) }
        return recipes.map { recipeWithIngredients ->
            val desired = desiredServingsByRecipeId[recipeWithIngredients.recipe.id]
                ?: recipeWithIngredients.recipe.servings
            val required = scaledIngredients(recipeWithIngredients, desired)
            RecipeMatchingUseCase.match(
                recipeId = recipeWithIngredients.recipe.id,
                recipeName = recipeWithIngredients.recipe.name,
                required = required,
                available = inventoryLines,
                isFavorite = recipeWithIngredients.recipe.isFavorite
            )
        }
    }

    suspend fun count(): Int = dao.count(owner.key)

    /** The catalog plus the own recipes of [ownerKey] (default: the signed-in account). */
    suspend fun getAllWithIngredientsOnce(ownerKey: String = owner.key): List<RecipeWithIngredients> =
        withFavorites(dao.getAllWithIngredientsOnce(ownerKey), favoriteDao.getRefs(ownerKey).toSet())

    /** A server catalog recipe by its server id (shared data, same for every account). */
    suspend fun findByServerId(serverId: Long): Recipe? = dao.findByServerId(serverId)

    /** Deletes the signed-in account's OWN recipes. The shared server catalog recipes stay. */
    suspend fun clearAll() = dao.deleteLocalOnly(owner.key)

    suspend fun restoreAll(recipes: List<RecipeWithIngredients>) {
        dao.deleteLocalOnly(owner.key)
        recipes.forEach { upsert(it.recipe.copy(id = 0), it.ingredients.map { ri -> ri.copy(id = 0, recipeId = 0) }) }
    }

    /** Deletes the sample recipes older app versions shipped with (built in, never synced from the server). */
    suspend fun deleteLegacyBuiltIn() = dao.deleteUnsyncedBuiltIn()

}
