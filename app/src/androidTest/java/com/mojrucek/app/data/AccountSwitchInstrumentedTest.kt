package com.mojrucek.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.CatalogIngredient
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.AccountSwitchRepository
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository
import com.mojrucek.app.repository.SnapshotResult
import com.mojrucek.app.repository.VerifiedSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * The guest -> existing account data flow: snapshot verification and the guarded delete.
 * In-memory database; snapshots go to a temporary folder, never to the app's real files.
 */
@RunWith(AndroidJUnit4::class)
class AccountSwitchInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var tempFiles: File
    private lateinit var repo: AccountSwitchRepository
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var ingredientRepository: IngredientRepository
    private lateinit var plannerRepository: PlannerRepository

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        tempFiles = File(target.cacheDir, "account-switch-test-${System.nanoTime()}").apply { mkdirs() }
        val context: Context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = tempFiles
        }
        db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        recipeRepository = RecipeRepository(db.recipeDao())
        ingredientRepository = IngredientRepository(db.ingredientDao())
        val shoppingListRepository = ShoppingListRepository(db.shoppingListDao(), ingredientRepository)
        plannerRepository = PlannerRepository(db.plannerDao(), recipeRepository, ingredientRepository, shoppingListRepository)
        val backup = BackupRepository(
            ingredientRepository, recipeRepository, shoppingListRepository, db.plannerDao(), SettingsRepository(db.appSettingsDao()), db
        )
        repo = AccountSwitchRepository(context, db, backup)
    }

    @After
    fun tearDown() {
        db.close()
        tempFiles.deleteRecursively()
    }

    private fun count(sql: String): Int = db.query(sql, null).use { it.moveToFirst(); it.getInt(0) }

    private suspend fun seed() {
        db.catalogDao().upsertIngredient(
            CatalogIngredient(serverId = 1, name = "Ориз", normalizedName = "ориз", category = IngredientCategory.DRUGO, defaultUnit = MeasurementUnit.GRAM)
        )
        val serverRecipe = recipeRepository.upsert(
            Recipe(name = "Сервер рецепт", isUserCreated = false, serverId = 77, isFavorite = true),
            listOf(RecipeIngredient(recipeId = 0, name = "Ориз", normalizedName = "ориз", quantity = 100.0, unit = MeasurementUnit.GRAM))
        )
        val ownRecipe = recipeRepository.upsert(
            Recipe(name = "Мој рецепт", isFavorite = true),
            listOf(RecipeIngredient(recipeId = 0, name = "Јајца", normalizedName = "јајца", quantity = 2.0, unit = MeasurementUnit.PIECE))
        )
        db.ingredientDao().insert(
            Ingredient(name = "Јајца", normalizedName = "јајца", category = IngredientCategory.DRUGO, quantity = 6.0, unit = MeasurementUnit.PIECE)
        )
        db.shoppingListDao().insert(
            ShoppingListItem(name = "Млеко", normalizedName = "млеко", quantity = 1.0, unit = MeasurementUnit.PIECE)
        )
        val plan = plannerRepository.getOrCreatePlan(LocalDate.of(2026, 11, 2), defaultServings = 2)
        plannerRepository.setMeal(plan.weeklyPlan.id, dayOfWeek = 1, recipeId = ownRecipe)
        plannerRepository.setMeal(plan.weeklyPlan.id, dayOfWeek = 2, recipeId = serverRecipe)
    }

    private suspend fun snapshot(): VerifiedSnapshot =
        (repo.createVerifiedSnapshot() as SnapshotResult.Ok).snapshot

    @Test
    fun snapshot_isWrittenVerifiedAndMatchesTheDatabase() = runBlocking {
        seed()
        val s = snapshot()
        assertTrue(s.file.exists())
        assertTrue(s.file.parentFile!!.path.startsWith(tempFiles.path))
        assertEquals(2, s.summary.recipeCount)
        assertEquals(1, s.summary.ingredientCount)
        assertEquals(1, s.summary.shoppingItemCount)
        assertEquals(1, s.summary.weeklyPlanCount)
        assertTrue(repo.matchesSnapshot(s))
        assertTrue(tempFiles.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun clear_removesOnlyLocalUserData_keepsCatalogServerRecipesAndSettings() = runBlocking {
        seed()
        val s = snapshot()
        assertTrue(repo.clearLocalUserData(s))

        assertEquals(0, count("SELECT COUNT(*) FROM ingredients"))
        assertEquals(0, count("SELECT COUNT(*) FROM shopping_list_items"))
        assertEquals(0, count("SELECT COUNT(*) FROM weekly_plans"))
        assertEquals(0, count("SELECT COUNT(*) FROM planned_meals"))
        assertEquals(0, count("SELECT COUNT(*) FROM recipes WHERE serverId IS NULL"))
        assertEquals(0, count("SELECT COUNT(*) FROM recipes WHERE isFavorite = 1"))
        // Kept: the server catalog, server recipes (with their ingredient lines).
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_ingredients"))
        assertEquals(1, count("SELECT COUNT(*) FROM recipes WHERE serverId = 77"))
        assertEquals(1, count("SELECT COUNT(*) FROM recipe_ingredients"))
        // The snapshot is never deleted.
        assertTrue(s.file.exists())
    }

    @Test
    fun clear_deletesNothing_whenDataChangedAfterTheSnapshot() = runBlocking {
        seed()
        val s = snapshot()
        db.ingredientDao().insert(
            Ingredient(name = "Нова", normalizedName = "нова", category = IngredientCategory.DRUGO, quantity = 1.0, unit = MeasurementUnit.PIECE)
        )
        assertFalse(repo.matchesSnapshot(s))
        assertFalse(repo.clearLocalUserData(s))
        assertEquals(2, count("SELECT COUNT(*) FROM ingredients"))
        assertEquals(1, count("SELECT COUNT(*) FROM shopping_list_items"))
        assertEquals(1, count("SELECT COUNT(*) FROM weekly_plans"))
        assertEquals(1, count("SELECT COUNT(*) FROM recipes WHERE serverId IS NULL"))
    }

    @Test
    fun clear_deletesNothing_whenTheSnapshotFileWasAltered() = runBlocking {
        seed()
        val s = snapshot()
        s.file.writeText(s.file.readText().replace("Јајца", "Друго"))
        assertFalse(repo.matchesSnapshot(s))
        assertFalse(repo.clearLocalUserData(s))
        assertEquals(1, count("SELECT COUNT(*) FROM ingredients"))
        assertEquals(1, count("SELECT COUNT(*) FROM recipes WHERE serverId IS NULL"))
    }
}
