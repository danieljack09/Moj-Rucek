package com.mojrucek.app.data

import android.content.Context
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
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.LocalDataEraser
import com.mojrucek.app.repository.OwnerKeys
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Erasing one account's phone data must leave every other account's data, the unowned data and the catalog alone. */
@RunWith(AndroidJUnit4::class)
class LocalDataEraserInstrumentedTest {

    private lateinit var db: AppDatabase
    private val tables = listOf("ingredients", "shopping_list_items", "weekly_plans", "planned_meals", "recipes", "recipe_ingredients", "recipe_favorites")

    @Before fun setUp() {
        val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
    }

    @After fun tearDown() { db.close() }

    private fun count(sql: String): Int = db.query(sql, null).use { it.moveToFirst(); it.getInt(0) }
    private fun countFor(table: String, owner: String): Int =
        count("SELECT COUNT(*) FROM $table WHERE ownerUid = '$owner'")

    /** Gives [key] a full set of data: pantry, shopping item, own recipe + favourite, a plan with a meal. */
    private suspend fun seed(key: String) {
        val owner = CurrentOwner.following(MutableStateFlow(key))
        val recipes = RecipeRepository(db.recipeDao(), db.favoriteDao(), owner)
        val ingredients = IngredientRepository(db.ingredientDao(), owner)
        val shopping = ShoppingListRepository(db.shoppingListDao(), ingredients, owner)
        val planner = PlannerRepository(db.plannerDao(), recipes, ingredients, shopping, owner)
        val recipe = recipes.upsert(
            Recipe(name = "Рецепт $key"),
            listOf(RecipeIngredient(recipeId = 0, name = "Јајца", normalizedName = "јајца", quantity = 2.0, unit = MeasurementUnit.PIECE))
        )
        recipes.setFavorite(recipe, true)
        db.ingredientDao().insert(Ingredient(name = "Јајца", normalizedName = "јајца", category = IngredientCategory.DRUGO, quantity = 6.0, unit = MeasurementUnit.PIECE, ownerUid = key))
        db.shoppingListDao().insert(ShoppingListItem(name = "Млеко", normalizedName = "млеко", quantity = 1.0, unit = MeasurementUnit.PIECE, ownerUid = key))
        val plan = planner.getOrCreatePlan(LocalDate.of(2026, 11, 2), defaultServings = 2)
        planner.setMeal(plan.weeklyPlan.id, dayOfWeek = 1, recipeId = recipe)
    }

    private fun snapshotOf(owner: String): Map<String, Int> =
        listOf("ingredients", "shopping_list_items", "weekly_plans", "recipes", "recipe_favorites").associateWith { countFor(it, owner) }

    @Test fun erasingOneAccount_removesAllOfItsData_andNothingElse() = runBlocking {
        seed("uid-A"); seed("uid-B"); seed(OwnerKeys.UNOWNED)
        db.catalogDao().upsertIngredient(CatalogIngredient(serverId = 1, name = "Ориз", normalizedName = "ориз", category = IngredientCategory.DRUGO, defaultUnit = MeasurementUnit.GRAM))
        db.recipeDao().insertRecipe(Recipe(name = "Од сервер", isUserCreated = false, serverId = 5))
        val bBefore = snapshotOf("uid-B"); val unownedBefore = snapshotOf(OwnerKeys.UNOWNED)
        val mealsBefore = count("SELECT COUNT(*) FROM planned_meals")
        val serverRecipes = count("SELECT COUNT(*) FROM recipes WHERE serverId = 5")

        LocalDataEraser(db).eraseAccount("uid-A")

        for (table in listOf("ingredients", "shopping_list_items", "weekly_plans", "recipes", "recipe_favorites")) {
            assertEquals("$table of the erased account", 0, countFor(table, "uid-A"))
        }
        assertEquals(bBefore, snapshotOf("uid-B"))
        assertEquals(unownedBefore, snapshotOf(OwnerKeys.UNOWNED))
        assertEquals("only A's planned meal went", mealsBefore - 1, count("SELECT COUNT(*) FROM planned_meals"))
        assertEquals("the catalog is untouched", 1, count("SELECT COUNT(*) FROM catalog_ingredients"))
        assertEquals("shared server recipes are untouched", serverRecipes, count("SELECT COUNT(*) FROM recipes WHERE serverId = 5"))
    }

    @Test fun erasingAnAccountWithNoData_isHarmless() = runBlocking {
        seed("uid-B")
        val before = snapshotOf("uid-B")
        LocalDataEraser(db).eraseAccount("uid-never-had-data")
        assertEquals(before, snapshotOf("uid-B"))
    }

    @Test fun theUnownedBucketAndTheSignedOutKey_cannotBeErased() = runBlocking {
        seed(OwnerKeys.UNOWNED)
        for (key in listOf(OwnerKeys.UNOWNED, OwnerKeys.SIGNED_OUT)) {
            try { LocalDataEraser(db).eraseAccount(key); fail("must refuse $key") } catch (e: IllegalArgumentException) { }
        }
        assertTrue(countFor("ingredients", OwnerKeys.UNOWNED) > 0)
    }

    @Test fun erasing_isRepeatable() = runBlocking {
        seed("uid-A")
        LocalDataEraser(db).eraseAccount("uid-A")
        LocalDataEraser(db).eraseAccount("uid-A")
        assertEquals(0, tables.sumOf { t -> if (t == "planned_meals" || t == "recipe_ingredients") 0 else countFor(t, "uid-A") })
    }
}
