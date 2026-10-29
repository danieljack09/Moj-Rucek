package com.mojrucek.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlannerShoppingListInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var ingredientRepository: IngredientRepository
    private lateinit var shoppingListRepository: ShoppingListRepository
    private lateinit var plannerRepository: PlannerRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recipeRepository = RecipeRepository(db.recipeDao())
        ingredientRepository = IngredientRepository(db.ingredientDao())
        shoppingListRepository = ShoppingListRepository(db.shoppingListDao(), ingredientRepository)
        plannerRepository = PlannerRepository(db.plannerDao(), recipeRepository, ingredientRepository, shoppingListRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun regeneratingShoppingList_afterChangingThePlan_doesNotDuplicateItems() = runBlocking {
        val recipeId = recipeRepository.upsert(
            Recipe(name = "Тест јадење", servings = 2),
            listOf(RecipeIngredient(recipeId = 0, name = "Ориз", normalizedName = "ориз", quantity = 200.0, unit = MeasurementUnit.GRAM))
        )

        val weekStart = LocalDate.of(2026, 11, 2) // a Monday
        val plan = plannerRepository.getOrCreatePlan(weekStart, defaultServings = 2)
        plannerRepository.setMeal(plan.weeklyPlan.id, dayOfWeek = 1, recipeId = recipeId)

        val planWithMeals1 = plannerRepository.getPlanForWeek(weekStart)!!
        plannerRepository.generateShoppingList(planWithMeals1)

        // Change the plan (add the same recipe to a second day) and regenerate.
        plannerRepository.setMeal(plan.weeklyPlan.id, dayOfWeek = 2, recipeId = recipeId)
        val planWithMeals2 = plannerRepository.getPlanForWeek(weekStart)!!
        plannerRepository.generateShoppingList(planWithMeals2)

        val items = shoppingListRepository.getAllOnce()
        val riceItems = items.filter { it.normalizedName == "ориз" }

        assertEquals("regenerating must not create a second row for the same ingredient", 1, riceItems.size)
        assertEquals(400.0, riceItems.single().quantity, 0.0001) // 200g x 2 days
    }
}
