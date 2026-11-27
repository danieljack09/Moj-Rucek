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
 * JSON export/import of the signed-in account's data (spec §12), so recipes and the rest
 * survive a phone change. A backup holds ONE account's data (plus the shared server recipes), never
 * another account's, and restoring replaces only that account's data. Import always validates the
 * file and relies on the caller to get the user's confirmation before [import] overwrites existing
 * data. Reading and checking a file lives in [BackupFormat].
 */
class BackupRepository(
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val plannerDao: PlannerDao,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val owner: CurrentOwner
) {

    private val json = BackupFormat.json

    /** The backup of [ownerKey] (default: the signed-in account). */
    suspend fun exportToJson(ownerKey: String = owner.key): String {
        val recipesWithIngredients = recipeRepository.getAllWithIngredientsOnce(ownerKey)
        val allPlans = plannerDao.getAllWithMealsOnce(ownerKey)

        val data = BackupData(
            exportedAtEpochMillis = System.currentTimeMillis(),
            ingredients = ingredientRepository.getAllOnce(ownerKey),
            recipes = recipesWithIngredients.map { it.recipe },
            recipeIngredients = recipesWithIngredients.flatMap { it.ingredients },
            shoppingListItems = shoppingListRepository.getAllOnce(ownerKey),
            weeklyPlans = allPlans.map { it.weeklyPlan },
            plannedMeals = allPlans.flatMap { it.meals },
            settings = settingsRepository.getOnce()
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    /**
     * The snapshot used by sync (see docs/sync-plan.md): the account's own pantry, own recipes, plans and shopping list, plus
     * the server recipes it marked as favourites or plans meals with, reduced to a stub (a server recipe is the same for everyone and changes by
     * itself, so its details and dates must not make the account look "changed"). The phone's reminder settings are NOT
     * included: they belong to the device.
     */
    suspend fun exportForSync(ownerKey: String = owner.key): String {
        val all = recipeRepository.getAllWithIngredientsOnce(ownerKey)
        val own = all.filter { it.recipe.serverId == null }
        val allPlans = plannerDao.getAllWithMealsOnce(ownerKey)
        // A server recipe travels as a stub when the account refers to it: as a favourite, or because a planned meal uses it
        // (otherwise the meal would lose its recipe on the other phone).
        val plannedRecipeIds = allPlans.flatMap { it.meals }.mapNotNull { it.recipeId }.toSet()
        val referencedServerStubs = all
            .filter { it.recipe.serverId != null && (it.recipe.isFavorite || it.recipe.id in plannedRecipeIds) }
            .map { it.recipe.copy(createdAt = 0, updatedAt = 0, imageUri = null) }   // dates and the picture address change on their own
        val data = BackupData(
            exportedAtEpochMillis = System.currentTimeMillis(),
            ingredients = ingredientRepository.getAllOnce(ownerKey),
            recipes = own.map { it.recipe } + referencedServerStubs,
            recipeIngredients = own.flatMap { it.ingredients },
            shoppingListItems = shoppingListRepository.getAllOnce(ownerKey),
            weeklyPlans = allPlans.map { it.weeklyPlan },
            plannedMeals = allPlans.flatMap { it.meals },
            settings = com.mojrucek.app.data.entity.AppSettings()
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    /** Counts of what [ownerKey] has right now, in the same shape as a backup's summary. */
    suspend fun currentSummary(ownerKey: String = owner.key): BackupSummary = BackupSummary(
        ingredientCount = ingredientRepository.getAllOnce(ownerKey).size,
        recipeCount = recipeRepository.getAllWithIngredientsOnce(ownerKey).size,
        shoppingItemCount = shoppingListRepository.getAllOnce(ownerKey).size,
        weeklyPlanCount = plannerDao.getAllWithMealsOnce(ownerKey).size
    )

    /** Parses and validates [jsonText] without changing anything, so the UI can show a confirmation first. */
    fun validate(jsonText: String): ImportResult = when (val parsed = BackupFormat.parse(jsonText)) {
        is BackupFormat.Parsed.Ok -> ImportResult.Success(parsed.data.summary())
        is BackupFormat.Parsed.Invalid -> ImportResult.InvalidFile(parsed.reason)
    }

    /**
     * Replaces the signed-in account's data with the contents of [jsonText]. Call only after the user confirmed.
     * All or nothing: everything happens in ONE database transaction, so if anything fails the
     * current data is exactly as it was before. Other accounts' data is never touched.
     */
    suspend fun import(jsonText: String): ImportResult = importInternal(jsonText, applySettings = true)

    /** Like [import] for a sync snapshot: the phone's own reminder settings are left exactly as they are. */
    suspend fun importForSync(jsonText: String): ImportResult = importInternal(jsonText, applySettings = false)

    private suspend fun importInternal(jsonText: String, applySettings: Boolean): ImportResult {
        val data = when (val parsed = BackupFormat.parse(jsonText)) {
            is BackupFormat.Parsed.Ok -> parsed.data
            is BackupFormat.Parsed.Invalid -> return ImportResult.InvalidFile(parsed.reason)
        }
        return try {
            database.withTransaction { restore(data, applySettings) }
            ImportResult.Success(data.summary())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.InvalidFile("Вчитувањето не успеа и ништо не е променето. Обиди се повторно или избери друга датотека.")
        }
    }

    private suspend fun restore(data: BackupData, applySettings: Boolean) {
        val ownerKey = owner.key
        ingredientRepository.restoreAll(data.ingredients)

        // The account's OWN recipes are replaced; the shared server catalog is not touched.
        recipeRepository.clearAll()
        val ingredientsByRecipe = data.recipeIngredients.groupBy { it.recipeId }
        val newRecipeIdByOldId = HashMap<Long, Long>()
        val favoriteRecipeIds = ArrayList<Long>()
        data.recipes.forEach { recipe ->
            val serverId = recipe.serverId
            if (serverId != null) {
                // A server catalog recipe is the same for every account: keep the one that is here already
                // (its favourite mark is restored below).
                val existing = recipeRepository.findByServerId(serverId)
                if (existing != null) {
                    newRecipeIdByOldId[recipe.id] = existing.id
                    if (recipe.isFavorite) favoriteRecipeIds += existing.id
                    return@forEach
                }
            }
            val newId = recipeRepository.upsert(
                recipe.copy(id = 0),
                ingredientsByRecipe[recipe.id].orEmpty().map { it.copy(id = 0, recipeId = 0) }
            )
            newRecipeIdByOldId[recipe.id] = newId
            if (recipe.isFavorite) favoriteRecipeIds += newId
        }
        // The account's favourites become exactly the backup's (other accounts' favourites are not touched).
        recipeRepository.replaceFavorites(favoriteRecipeIds)

        shoppingListRepository.restoreAll(data.shoppingListItems)

        plannerDao.deleteAllPlans(ownerKey)
        // Plans and meals get new ids too (the old ones may belong to another account's rows).
        val newPlanIdByOldId = HashMap<Long, Long>()
        data.weeklyPlans.forEach { plan ->
            newPlanIdByOldId[plan.id] = plannerDao.insertPlan(plan.copy(id = 0, ownerUid = ownerKey))
        }
        val meals = BackupFormat.remapMeals(data.plannedMeals, newPlanIdByOldId, newRecipeIdByOldId)
        if (meals.isNotEmpty()) plannerDao.insertMeals(meals)

        if (applySettings) settingsRepository.update { data.settings }
    }
}
