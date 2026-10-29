package com.mojrucek.app.repository

import com.mojrucek.app.data.BackupData
import com.mojrucek.app.data.BackupSummary
import com.mojrucek.app.data.dao.PlannerDao
import com.mojrucek.app.data.summary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ImportResult {
    data class Success(val summary: BackupSummary) : ImportResult
    data class InvalidFile(val reason: String) : ImportResult
}

/**
 * JSON export/import for the whole app (spec §12), so recipes and the rest
 * survive a phone change. Import always validates the file and relies on
 * the caller to get the user's confirmation before [import] overwrites
 * existing data.
 */
class BackupRepository(
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val plannerDao: PlannerDao,
    private val settingsRepository: SettingsRepository
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    /** Parses and validates [jsonText] without changing anything, so the UI can show a confirmation first. */
    fun validate(jsonText: String): ImportResult = try {
        val data = json.decodeFromString(BackupData.serializer(), jsonText)
        if (data.formatVersion > BackupData.CURRENT_FORMAT_VERSION) {
            ImportResult.InvalidFile("Датотеката е зачувана со понова верзија на апликацијата.")
        } else {
            ImportResult.Success(data.summary())
        }
    } catch (e: SerializationException) {
        ImportResult.InvalidFile("Датотеката не е валидна резервна копија на Мој Ручек.")
    } catch (e: IllegalArgumentException) {
        ImportResult.InvalidFile("Датотеката не е валидна резервна копија на Мој Ручек.")
    }

    /** Overwrites all existing data with the contents of [jsonText]. Call only after the user confirmed. */
    suspend fun import(jsonText: String): ImportResult {
        val data = try {
            json.decodeFromString(BackupData.serializer(), jsonText)
        } catch (e: SerializationException) {
            return ImportResult.InvalidFile("Датотеката не е валидна резервна копија на Мој Ручек.")
        }

        ingredientRepository.restoreAll(data.ingredients)

        recipeRepository.clearAll()
        val ingredientsByRecipe = data.recipeIngredients.groupBy { it.recipeId }
        data.recipes.forEach { recipe ->
            recipeRepository.upsert(recipe.copy(id = 0), ingredientsByRecipe[recipe.id].orEmpty().map { it.copy(id = 0, recipeId = 0) })
        }

        shoppingListRepository.restoreAll(data.shoppingListItems)

        plannerDao.deleteAllPlans()
        if (data.weeklyPlans.isNotEmpty()) {
            plannerDao.insertPlans(data.weeklyPlans)
            plannerDao.insertMeals(data.plannedMeals)
        }

        settingsRepository.update { data.settings }

        return ImportResult.Success(data.summary())
    }
}
