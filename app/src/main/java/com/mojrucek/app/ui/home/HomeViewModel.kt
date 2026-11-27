package com.mojrucek.app.ui.home

import java.time.LocalDate
import com.mojrucek.app.domain.usecase.RecipeMatchingUseCase
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.Readiness
import com.mojrucek.app.data.entity.RecipeCategory
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
import com.mojrucek.app.repository.ShoppingListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayMealUi(val recipeId: Long, val recipeName: String)

/**
 * What the Home meal preview sheet shows for a planned day. Built from data the ViewModel already
 * loads (the recipes, the plan and the pantry): no extra database reads.
 * [servings] is the day's planned servings (its override, else the plan's household servings), and
 * the coverage numbers are matched at that serving count, the same way the Planner does.
 */
data class MealPreviewUi(
    val imageUri: String?,
    val category: RecipeCategory?,
    val description: String?,
    val totalTimeMinutes: Int,
    val servings: Int,
    val coveredIngredients: Int,
    val totalIngredients: Int,
    val readiness: Readiness
)

/** One day of the Home "Оваа недела" preview. [meal] and [preview] are null when nothing is planned. */
data class WeekDayUi(
    val dayOfWeek: Int,
    val isToday: Boolean,
    val meal: TodayMealUi?,
    val date: LocalDate? = null,
    val preview: MealPreviewUi? = null
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val today: TodayMealUi? = null,
    val weekDays: List<WeekDayUi> = emptyList(),
    val unpurchasedShoppingCount: Int = 0,
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
    private val settingsRepository: SettingsRepository,
    private val shoppingListRepository: ShoppingListRepository
) : ViewModel() {

    /** Current date; re-checked every minute so the screen follows midnight and Monday rollovers. */
    private val todayFlow = flow {
        while (true) {
            emit(WeekCalculator.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = todayFlow.flatMapLatest { today ->
        val thisWeekStart = WeekCalculator.mondayOf(today)
        val nextWeekStart = WeekCalculator.nextWeekMonday(today)
        val todayDayOfWeek = today.dayOfWeek.value
        combine(
            ingredientRepository.observeAll(),
            recipeRepository.observeAllWithIngredients(),
            plannerRepository.observePlanForWeek(thisWeekStart),
            plannerRepository.observePlanForWeek(nextWeekStart),
            shoppingListRepository.observeAll()
        ) { ingredients, recipes, thisWeekPlan, nextWeekPlan, shoppingItems ->
        val todayMeal: PlannedMeal? = thisWeekPlan?.meals?.firstOrNull { it.dayOfWeek == todayDayOfWeek }
        val todayRecipe = todayMeal?.recipeId?.let { id -> recipes.firstOrNull { it.recipe.id == id } }
        val householdServings = thisWeekPlan?.weeklyPlan?.householdServings ?: 3
        val inventoryLines = ingredients.map { IngredientLine(it.name, it.normalizedName, it.quantity, it.unit) }
        val weekDays = (1..7).map { dow ->
            val meal = thisWeekPlan?.meals?.firstOrNull { it.dayOfWeek == dow }
            val recipe = meal?.recipeId?.let { id -> recipes.firstOrNull { it.recipe.id == id } }
            val preview = recipe?.let {
                val servings = meal.servingsOverride ?: householdServings
                val match = RecipeMatchingUseCase.match(
                    it.recipe.id, it.recipe.name, recipeRepository.scaledIngredients(it, servings), inventoryLines
                )
                MealPreviewUi(
                    imageUri = it.recipe.imageUri,
                    category = it.recipe.category,
                    description = it.recipe.description,
                    totalTimeMinutes = it.recipe.totalTimeMinutes,
                    servings = servings,
                    coveredIngredients = match.coveredIngredients,
                    totalIngredients = match.totalIngredients,
                    readiness = match.readiness
                )
            }
            WeekDayUi(
                dow,
                dow == todayDayOfWeek,
                recipe?.let { TodayMealUi(it.recipe.id, it.recipe.name) },
                date = thisWeekStart.plusDays((dow - 1).toLong()),
                preview = preview
            )
        }

        val matches = recipeRepository.matchAll(recipes, ingredients)
        val suggested = matches
            .sortedWith(compareByDescending<RecipeMatchResult> { it.canCookNow }.thenByDescending { it.coveragePercent })
            .take(5)

        HomeUiState(
            isLoading = false,
            today = todayRecipe?.let { TodayMealUi(it.recipe.id, it.recipe.name) },
            weekDays = weekDays,
            unpurchasedShoppingCount = shoppingItems.count { !it.isPurchased },
            ingredientCount = ingredients.size,
            lowStockIngredients = ingredients.filter { it.isLowStock },
            suggestedRecipes = suggested,
            nextWeekPlanned = nextWeekPlan?.meals?.count { it.recipeId != null } ?: 0,
            nextWeekShoppingGenerated = nextWeekPlan?.weeklyPlan?.shoppingListGenerated ?: false
        )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun recipesForPicker(onResult: (List<RecipeWithIngredients>) -> Unit) {
        viewModelScope.launch {
            onResult(recipeRepository.getAllWithIngredientsOnce())
        }
    }

    fun setTodayMeal(recipeId: Long?) {
        viewModelScope.launch {
            val defaultServings = settingsRepository.getOnce().defaultHouseholdServings
            val today = WeekCalculator.today()
            val plan = plannerRepository.getOrCreatePlan(WeekCalculator.mondayOf(today), defaultServings)
            plannerRepository.setMeal(plan.weeklyPlan.id, today.dayOfWeek.value, recipeId)
        }
    }
}
