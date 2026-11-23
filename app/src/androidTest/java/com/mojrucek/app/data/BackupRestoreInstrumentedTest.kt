package com.mojrucek.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.dao.PlannerDao
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Restoring a backup against a real (in-memory) Room database: planned meals must stay on their
 * recipes although recipes get new ids, and a restore that fails halfway must change nothing.
 * Never touches the app's real data.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var ingredients: IngredientRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var planner: PlannerRepository
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupRepository

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
        ingredients = IngredientRepository(db.ingredientDao())
        shopping = ShoppingListRepository(db.shoppingListDao(), ingredients)
        planner = PlannerRepository(db.plannerDao(), recipes, ingredients, shopping)
        settings = SettingsRepository(db.appSettingsDao())
        backup = BackupRepository(ingredients, recipes, shopping, db.plannerDao(), settings, db)
    }

    @After
    fun tearDown() = db.close()

    private fun line(name: String) = RecipeIngredient(
        recipeId = 0, name = name, normalizedName = name.lowercase(), quantity = 1.0, unit = MeasurementUnit.PIECE
    )

    /** Two recipes, one pantry item, one shopping item and a plan with Monday -> A, Tuesday -> B. */
    private suspend fun seedOriginal() {
        val a = recipes.upsert(Recipe(name = "Тавче гравче"), listOf(line("грав")))
        val b = recipes.upsert(Recipe(name = "Мусака"), listOf(line("компир")))
        db.ingredientDao().insert(
            Ingredient(name = "Јајца", normalizedName = "јајца", category = IngredientCategory.DRUGO, quantity = 6.0, unit = MeasurementUnit.PIECE)
        )
        db.shoppingListDao().insert(ShoppingListItem(name = "Млеко", normalizedName = "млеко", quantity = 1.0, unit = MeasurementUnit.PIECE))
        val plan = planner.getOrCreatePlan(LocalDate.of(2026, 11, 2), defaultServings = 2)
        planner.setMeal(plan.weeklyPlan.id, dayOfWeek = 1, recipeId = a)
        planner.setMeal(plan.weeklyPlan.id, dayOfWeek = 2, recipeId = b)
    }

    /** day of week -> name of the recipe the planned meal points at. */
    private suspend fun mealRecipeNames(): Map<Int, String?> {
        val nameById = recipes.getAllWithIngredientsOnce().associate { it.recipe.id to it.recipe.name }
        return db.plannerDao().getAllWithMealsOnce().flatMap { it.meals }.associate { it.dayOfWeek to it.recipeId?.let(nameById::get) }
    }

    private suspend fun worldSummary() = listOf(
        recipes.getAllWithIngredientsOnce().map { it.recipe.name }.sorted(),
        ingredients.getAllOnce().map { it.name }.sorted(),
        shopping.getAllOnce().map { it.name }.sorted(),
        db.plannerDao().getAllWithMealsOnce().size
    )

    @Test
    fun restore_keepsPlannedMealsOnTheirRecipes_evenThoughRecipesGetNewIds() = runBlocking {
        seedOriginal()
        val file = backup.exportToJson()

        // The recipes now have different ids than when the backup was made.
        recipes.clearAll()
        recipes.upsert(Recipe(name = "Нешто друго"), listOf(line("x")))
        recipes.upsert(Recipe(name = "Уште нешто"), listOf(line("y")))

        val result = backup.import(file)

        assertTrue("restore should succeed: $result", result is ImportResult.Success)
        assertEquals(mapOf(1 to "Тавче гравче", 2 to "Мусака"), mealRecipeNames())
    }

    @Test
    fun restore_failingHalfway_changesNothing() = runBlocking {
        seedOriginal()
        val file = backup.exportToJson()

        // Different current data, so a half-applied restore would be visible.
        recipes.clearAll()
        recipes.upsert(Recipe(name = "Тековен рецепт"), listOf(line("z")))
        db.shoppingListDao().deleteAll()
        val before = worldSummary()

        // The last restore step (planned meals) blows up, after the earlier steps already ran.
        val failingPlanner = object : PlannerDao by db.plannerDao() {
            override suspend fun insertMeals(meals: List<PlannedMeal>) = throw IllegalStateException("simulated failure")
        }
        val failing = BackupRepository(ingredients, recipes, shopping, failingPlanner, settings, db)

        val result = failing.import(file)

        assertTrue("a failed restore must be reported: $result", result is ImportResult.InvalidFile)
        assertEquals("a failed restore must leave the data exactly as it was", before, worldSummary())
    }

    @Test
    fun restore_ofAnInvalidFile_changesNothing() = runBlocking {
        seedOriginal()
        val before = worldSummary()

        val result = backup.import("{ this is not a backup")

        assertTrue(result is ImportResult.InvalidFile)
        assertEquals(before, worldSummary())
    }
}
