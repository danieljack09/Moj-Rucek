package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.RecipeDao
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.data.seed.SampleRecipes
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.domain.usecase.RecipeMatchingUseCase
import com.mojrucek.app.domain.usecase.ServingScaler
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val dao: RecipeDao) {

    fun observeAllWithIngredients(): Flow<List<RecipeWithIngredients>> = dao.observeAllWithIngredients()

    fun observeWithIngredients(id: Long): Flow<RecipeWithIngredients?> = dao.observeWithIngredients(id)

    suspend fun getWithIngredients(id: Long): RecipeWithIngredients? = dao.getWithIngredients(id)

    suspend fun upsert(recipe: Recipe, ingredients: List<RecipeIngredient>): Long {
        val recipeId = if (recipe.id == 0L) dao.insertRecipe(recipe) else {
            dao.updateRecipe(recipe)
            recipe.id
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

    suspend fun delete(recipe: Recipe) = dao.deleteRecipe(recipe)

    suspend fun setFavorite(id: Long, isFavorite: Boolean) = dao.setFavorite(id, isFavorite)

    /** Creates a copy of [source] (and its ingredients) named "<name> (копија)", ready to edit. */
    suspend fun duplicate(source: RecipeWithIngredients): Long {
        val copy = source.recipe.copy(
            id = 0,
            name = "${source.recipe.name} (копија)",
            isFavorite = false,
            createdAt = System.currentTimeMillis()
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

    suspend fun count(): Int = dao.count()

    suspend fun getAllWithIngredientsOnce(): List<RecipeWithIngredients> = dao.getAllWithIngredientsOnce()

    suspend fun clearAll() = dao.deleteAll()

    suspend fun restoreAll(recipes: List<RecipeWithIngredients>) {
        dao.deleteAll()
        recipes.forEach { upsert(it.recipe.copy(id = 0), it.ingredients.map { ri -> ri.copy(id = 0, recipeId = 0) }) }
    }

    /** Populates the database with the built-in Macedonian sample recipes, only if it's currently empty. */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        SampleRecipes.all.forEach { seed ->
            val recipe = Recipe(
                name = seed.name,
                description = seed.description,
                servings = seed.servings,
                prepTimeMinutes = seed.prepTimeMinutes,
                cookTimeMinutes = seed.cookTimeMinutes,
                instructionSteps = seed.steps,
                category = seed.category,
                tags = seed.tags,
                isUserCreated = false
            )
            val ingredients = seed.ingredients.map {
                RecipeIngredient(
                    recipeId = 0,
                    name = it.name,
                    normalizedName = RecipeIngredient.normalize(it.name),
                    quantity = it.quantity,
                    unit = it.unit
                )
            }
            upsert(recipe, ingredients)
        }
    }
}
