package com.mojrucek.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.domain.usecase.WeekCalculator
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayMealUi(val recipeId: Long, val recipeName: String)

data class HomeUiState(
    val isLoading: Boolean = true,
    val today: TodayMealUi? = null,
    val ingredientCount: Int = 0,
    val lowStockIngredients: List<Ingredient> = emptyList(),
    val suggestedRecipes: List<RecipeMatchResult> = emptyList(),
    val nextWeekPlanned: Int = 0,
    val nextWeekShoppingGenerated: Boolean = false
)

class HomeViewModel(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val plannerRepository: PlannerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val today = WeekCalculator.today()
    private val thisWeekStart = WeekCalculator.mondayOf(today)
    private val nextWeekStart = WeekCalculator.nextWeekMonday(today)
    private val todayDayOfWeek = today.dayOfWeek.value

    val uiState: StateFlow<HomeUiState> = combine(
        ingredientRepository.observeAll(),
        recipeRepository.observeAllWithIngredients(),
        plannerRepository.observePlanForWeek(thisWeekStart),
        plannerRepository.observePlanForWeek(nextWeekStart)
    ) { ingredients, recipes, thisWeekPlan, nextWeekPlan ->
        val todayMeal: PlannedMeal? = thisWeekPlan?.meals?.firstOrNull { it.dayOfWeek == todayDayOfWeek }
        val todayRecipe = todayMeal?.recipeId?.let { id -> recipes.firstOrNull { it.recipe.id == id } }

        val matches = recipeRepository.matchAll(recipes, ingredients)
        val suggested = matches
            .sortedWith(compareByDescending<RecipeMatchResult> { it.canCookNow }.thenByDescending { it.coveragePercent })
            .take(5)

        HomeUiState(
            isLoading = false,
            today = todayRecipe?.let { TodayMealUi(it.recipe.id, it.recipe.name) },
            ingredientCount = ingredients.size,
            lowStockIngredients = ingredients.filter { it.isLowStock },
            suggestedRecipes = suggested,
            nextWeekPlanned = nextWeekPlan?.meals?.count { it.recipeId != null } ?: 0,
            nextWeekShoppingGenerated = nextWeekPlan?.weeklyPlan?.shoppingListGenerated ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun recipesForPicker(onResult: (List<RecipeWithIngredients>) -> Unit) {
        viewModelScope.launch {
            onResult(recipeRepository.getAllWithIngredientsOnce())
        }
    }

    fun setTodayMeal(recipeId: Long?) {
        viewModelScope.launch {
            val defaultServings = settingsRepository.getOnce().defaultHouseholdServings
            val plan = plannerRepository.getOrCreatePlan(thisWeekStart, defaultServings)
            plannerRepository.setMeal(plan.weeklyPlan.id, todayDayOfWeek, recipeId)
        }
    }
}
