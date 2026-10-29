package com.mojrucek.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.WeeklyPlan
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.ShoppingListGenerationResult
import com.mojrucek.app.domain.usecase.RecipeMatchingUseCase
import com.mojrucek.app.domain.usecase.WeekCalculator
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayPlanUi(
    val dayOfWeek: Int,
    val date: LocalDate,
    val recipeId: Long?,
    val recipeName: String?,
    val servings: Int,
    val canPrepareNow: Boolean,
    val missingCount: Int
)

data class PlannerUiState(
    val isLoading: Boolean = true,
    val weekStart: LocalDate = WeekCalculator.today(),
    val householdServings: Int = 3,
    val days: List<DayPlanUi> = emptyList(),
    val incompleteDays: List<Int> = emptyList(),
    val shoppingListGenerated: Boolean = false,
    val lastGenerationResult: ShoppingListGenerationResult? = null
)

private val MACEDONIAN_DAY_NAMES = mapOf(
    1 to "Понеделник", 2 to "Вторник", 3 to "Среда", 4 to "Четврток",
    5 to "Петок", 6 to "Сабота", 7 to "Недела"
)

fun dayName(dayOfWeek: Int): String = MACEDONIAN_DAY_NAMES[dayOfWeek] ?: ""

class PlannerViewModel(
    private val plannerRepository: PlannerRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val weekStart = MutableStateFlow(initialWeekStart())
    private val generationResult = MutableStateFlow<ShoppingListGenerationResult?>(null)

    private fun initialWeekStart(): LocalDate {
        val today = WeekCalculator.today()
        // On/after Sunday it's natural to default to planning the upcoming week.
        return if (WeekCalculator.isSunday(today)) WeekCalculator.nextWeekMonday(today) else WeekCalculator.mondayOf(today)
    }

    val uiState: StateFlow<PlannerUiState> = weekStart.flatMapLatest { ws ->
        combine(
            plannerRepository.observePlanForWeek(ws),
            recipeRepository.observeAllWithIngredients(),
            ingredientRepository.observeAll(),
            generationResult
        ) { planWithMeals, recipes, inventory, lastResult ->
            val recipesById = recipes.associateBy { it.recipe.id }
            val defaultServings = planWithMeals?.weeklyPlan?.householdServings ?: 3
            val mealsByDay = planWithMeals?.meals?.associateBy { it.dayOfWeek } ?: emptyMap()
            val inventoryLines = inventory.map { IngredientLine(it.name, it.normalizedName, it.quantity, it.unit) }

            val days = WeekCalculator.datesOfWeek(ws).mapIndexed { idx, date ->
                val dow = idx + 1
                val meal = mealsByDay[dow]
                val recipe = meal?.recipeId?.let { recipesById[it] }
                val servings = meal?.servingsOverride ?: defaultServings
                var canPrepare = false
                var missing = 0
                if (recipe != null) {
                    val required = recipeRepository.scaledIngredients(recipe, servings)
                    val match = RecipeMatchingUseCase.match(recipe.recipe.id, recipe.recipe.name, required, inventoryLines)
                    canPrepare = match.canCookNow
                    missing = match.missing.size
                }
                DayPlanUi(
                    dayOfWeek = dow,
                    date = date,
                    recipeId = recipe?.recipe?.id,
                    recipeName = recipe?.recipe?.name,
                    servings = servings,
                    canPrepareNow = canPrepare,
                    missingCount = missing
                )
            }

            PlannerUiState(
                isLoading = false,
                weekStart = ws,
                householdServings = defaultServings,
                days = days,
                incompleteDays = days.filter { it.recipeId == null }.map { it.dayOfWeek },
                shoppingListGenerated = planWithMeals?.weeklyPlan?.shoppingListGenerated ?: false,
                lastGenerationResult = lastResult
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerUiState())

    fun goToPreviousWeek() { weekStart.value = weekStart.value.minusWeeks(1) }
    fun goToNextWeek() { weekStart.value = weekStart.value.plusWeeks(1) }
    fun goToThisWeek() { weekStart.value = WeekCalculator.mondayOf(WeekCalculator.today()) }

    fun recipesForPicker(onResult: (List<RecipeWithIngredients>) -> Unit) {
        viewModelScope.launch { onResult(recipeRepository.getAllWithIngredientsOnce()) }
    }

    private suspend fun ensurePlan(): WeeklyPlan {
        val defaults = settingsRepository.getOnce().defaultHouseholdServings
        return plannerRepository.getOrCreatePlan(weekStart.value, defaults).weeklyPlan
    }

    fun setMeal(dayOfWeek: Int, recipeId: Long?) {
        viewModelScope.launch {
            val plan = ensurePlan()
            plannerRepository.setMeal(plan.id, dayOfWeek, recipeId)
        }
    }

    fun copyMeal(fromDay: Int, toDay: Int) {
        viewModelScope.launch {
            val plan = ensurePlan()
            plannerRepository.copyMeal(plan.id, fromDay, toDay)
        }
    }

    fun setHouseholdServings(servings: Int) {
        viewModelScope.launch {
            val plan = ensurePlan()
            plannerRepository.setHouseholdServings(plan, servings)
        }
    }

    fun generateShoppingList() {
        viewModelScope.launch {
            val plan = plannerRepository.getOrCreatePlan(weekStart.value, settingsRepository.getOnce().defaultHouseholdServings)
            val result = plannerRepository.generateShoppingList(plan)
            generationResult.value = result
        }
    }

    fun clearGenerationResult() { generationResult.value = null }
}
