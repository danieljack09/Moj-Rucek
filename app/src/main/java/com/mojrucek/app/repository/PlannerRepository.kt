package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.PlannerDao
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.WeeklyPlan
import com.mojrucek.app.data.relation.WeeklyPlanWithMeals
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.ShoppingListGenerationResult
import com.mojrucek.app.domain.usecase.ShoppingListGeneratorUseCase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Owns the weekly planner and coordinates shopping-list generation, which
 * needs recipe ingredients, the home inventory, and the shopping list
 * itself — crossing repository boundaries is kept here rather than inside
 * any single repository or inside a ViewModel.
 */
class PlannerRepository(
    private val dao: PlannerDao,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val shoppingListRepository: ShoppingListRepository
) {

    fun observePlanForWeek(weekStart: LocalDate): Flow<WeeklyPlanWithMeals?> =
        dao.observeWithMealsByWeekStart(weekStart.toEpochDay())

    suspend fun getPlanForWeek(weekStart: LocalDate): WeeklyPlanWithMeals? =
        dao.getWithMealsByWeekStart(weekStart.toEpochDay())

    suspend fun getOrCreatePlan(weekStart: LocalDate, defaultServings: Int): WeeklyPlanWithMeals {
        val epoch = weekStart.toEpochDay()
        dao.getWithMealsByWeekStart(epoch)?.let { return it }
        val id = dao.insertPlan(WeeklyPlan(weekStartEpochDay = epoch, householdServings = defaultServings))
        return dao.getWithMealsById(id) ?: WeeklyPlanWithMeals(
            WeeklyPlan(id = id, weekStartEpochDay = epoch, householdServings = defaultServings),
            emptyList()
        )
    }

    suspend fun setMeal(planId: Long, dayOfWeek: Int, recipeId: Long?, servingsOverride: Int? = null) {
        if (recipeId == null) {
            dao.clearDay(planId, dayOfWeek)
        } else {
            dao.upsertMeal(
                PlannedMeal(weeklyPlanId = planId, dayOfWeek = dayOfWeek, recipeId = recipeId, servingsOverride = servingsOverride)
            )
        }
    }

    suspend fun copyMeal(planId: Long, fromDay: Int, toDay: Int) {
        val source = dao.getMealsForPlan(planId).firstOrNull { it.dayOfWeek == fromDay } ?: return
        if (source.recipeId == null) return
        setMeal(planId, toDay, source.recipeId, source.servingsOverride)
    }

    suspend fun setHouseholdServings(plan: WeeklyPlan, servings: Int) =
        dao.updatePlan(plan.copy(householdServings = servings.coerceAtLeast(1)))

    /** Days (1=Monday..7=Sunday) that still have no recipe assigned. */
    fun incompleteDays(planWithMeals: WeeklyPlanWithMeals): List<Int> =
        (1..7).filter { day -> planWithMeals.meals.none { it.dayOfWeek == day && it.recipeId != null } }

    /**
     * Computes the net shopping list for the week and saves it, replacing
     * whatever was previously auto-generated for this plan. Reading
     * inventory here does not modify it — see
     * [ShoppingListGeneratorUseCase] for why that matters.
     */
    suspend fun generateShoppingList(planWithMeals: WeeklyPlanWithMeals): ShoppingListGenerationResult {
        val plan = planWithMeals.weeklyPlan
        val requirements = mutableListOf<IngredientLine>()

        for (meal in planWithMeals.meals) {
            val recipeId = meal.recipeId ?: continue
            val recipe = recipeRepository.getWithIngredients(recipeId) ?: continue
            val desiredServings = meal.servingsOverride ?: plan.householdServings
            requirements += recipeRepository.scaledIngredients(recipe, desiredServings)
        }

        val inventory = ingredientRepository.getAllOnce()
        val inventoryLines = inventory.map { IngredientLine(it.name, it.normalizedName, it.quantity, it.unit) }
        val categoryByName = inventory.associate { it.normalizedName to it.category }

        val result = ShoppingListGeneratorUseCase.generate(requirements, inventoryLines, categoryByName)
        shoppingListRepository.replaceGeneratedItemsForPlan(plan.id, result.items)
        dao.setShoppingListGenerated(plan.id, true)
        return result
    }

    suspend fun clearAll() = dao.deleteAllPlans()
}
