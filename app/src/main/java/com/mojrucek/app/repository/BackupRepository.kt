package com.mojrucek.app.repository

import androidx.room.withTransaction
import com.mojrucek.app.data.AppDatabase
import com.mojrucek.app.data.BackupData
import com.mojrucek.app.data.BackupSummary
import com.mojrucek.app.data.dao.PlannerDao
import com.mojrucek.app.data.summary
import kotlinx.coroutines.CancellationException

sealed interface ImportResult {
    data class Success(val summary: BackupSummary) : ImportResult
    data class InvalidFile(val reason: String) : ImportResult
}

/**
 * JSON export/import for the whole app (spec §12), so recipes and the rest
 * survive a phone change. Import always validates the file and relies on
 * the caller to get the user's confirmation before [import] overwrites
 * existing data. Reading and checking a file lives in [BackupFormat].
 */
class BackupRepository(
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val plannerDao: PlannerDao,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase
) {

    private val json = BackupFormat.json

    suspend fun exportToJson(): String {
        val recipesWithIngredients = recipeRepository.getAllWithIngredientsOnce()
        val allPlans = plannerDao.getAllWithMealsOnce()

        val data = BackupData(
            exportedAtEpochMillis = System.currentTimeMillis(),
            ingredients = ingredientRepository.getAllOnce(),
            recipes = recipesWithIngredients.map { it.recipe },
            recipeIngredients = recipesWithIngredients.flatMap { it.ingredients },
            shoppingListItems = shoppingListRepository.getAllOnce(),
            weeklyPlans = allPlans.map { it.weeklyPlan },
            plannedMeals = allPlans.flatMap { it.meals },
            settings = settingsRepository.getOnce()
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    /** Counts of what is in the database right now, in the same shape as a backup's summary. */
    suspend fun currentSummary(): BackupSummary = BackupSummary(
        ingredientCount = ingredientRepository.getAllOnce().size,
        recipeCount = recipeRepository.getAllWithIngredientsOnce().size,
        shoppingItemCount = shoppingListRepository.getAllOnce().size,
        weeklyPlanCount = plannerDao.getAllWithMealsOnce().size
    )

    /** Parses and validates [jsonText] without changing anything, so the UI can show a confirmation first. */
    fun validate(jsonText: String): ImportResult = when (val parsed = BackupFormat.parse(jsonText)) {
        is BackupFormat.Parsed.Ok -> ImportResult.Success(parsed.data.summary())
        is BackupFormat.Parsed.Invalid -> ImportResult.InvalidFile(parsed.reason)
    }

    /**
     * Overwrites all existing data with the contents of [jsonText]. Call only after the user confirmed.
     * All or nothing: everything happens in ONE database transaction, so if anything fails the
     * current data is exactly as it was before.
     */
    suspend fun import(jsonText: String): ImportResult {
        val data = when (val parsed = BackupFormat.parse(jsonText)) {
            is BackupFormat.Parsed.Ok -> parsed.data
            is BackupFormat.Parsed.Invalid -> return ImportResult.InvalidFile(parsed.reason)
        }
        return try {
            database.withTransaction { restore(data) }
            ImportResult.Success(data.summary())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.InvalidFile("Вчитувањето не успеа и ништо не е променето. Обиди се повторно или избери друга датотека.")
        }
    }

    private suspend fun restore(data: BackupData) {
        ingredientRepository.restoreAll(data.ingredients)

        recipeRepository.clearAll()
        val ingredientsByRecipe = data.recipeIngredients.groupBy { it.recipeId }
        val newIdByOldId = HashMap<Long, Long>()
        data.recipes.forEach { recipe ->
            val newId = recipeRepository.upsert(
                recipe.copy(id = 0),
                ingredientsByRecipe[recipe.id].orEmpty().map { it.copy(id = 0, recipeId = 0) }
            )
            newIdByOldId[recipe.id] = newId
        }

        shoppingListRepository.restoreAll(data.shoppingListItems)

        plannerDao.deleteAllPlans()
        if (data.weeklyPlans.isNotEmpty()) {
            plannerDao.insertPlans(data.weeklyPlans)
            // Recipes got new ids above: the planned meals' recipe links must follow them.
            plannerDao.insertMeals(BackupFormat.remapMealRecipes(data.plannedMeals, newIdByOldId))
        }

        settingsRepository.update { data.settings }
    }
}
