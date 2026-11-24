package com.mojrucek.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.WeeklyPlan
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.limits.LimitNotice
import com.mojrucek.app.domain.limits.LimitTexts
import com.mojrucek.app.domain.model.ShoppingListGenerationResult
import com.mojrucek.app.session.ListPermission
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
    val lastGenerationResult: ShoppingListGenerationResult? = null,
    /** Why a list could not be made right now (too soon, guest period over, ...); null when nothing to say. */
    val limitNotice: LimitNotice? = null,
    /** "Следна листа може за 2д 5ч", or null when a list can be made now. */
    val nextListLine: String? = null,
    val generating: Boolean = false
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
    private val settingsRepository: SettingsRepository,
    private val listPermission: ListPermission,
    private val currentUid: () -> String?
) : ViewModel() {

    private val weekStart = MutableStateFlow(initialWeekStart())
    private val generationResult = MutableStateFlow<ShoppingListGenerationResult?>(null)
    private val limitNotice = MutableStateFlow<LimitNotice?>(null)
    private val nextListLine = MutableStateFlow<String?>(null)
    private val generating = MutableStateFlow(false)

    init {
        updateNextLine()
        viewModelScope.launch {
            // Quiet: learns the countdown and restores unlocked recipes (for example on a new phone).
            if (listPermission.refreshState()) updateNextLine()
        }
    }

    private fun updateNextLine() {
        val uid = currentUid()
        val remembered = uid?.let { listPermission.remembered(it) }
        nextListLine.value = remembered?.let {
            LimitTexts.nextListLine(it.nextAllowedAt, System.currentTimeMillis() + it.clockOffsetMs)
        }
    }

    private fun initialWeekStart(): LocalDate {
        val today = WeekCalculator.today()
        // On/after Sunday it's natural to default to planning the upcoming week.
        return if (WeekCalculator.isSunday(today)) WeekCalculator.nextWeekMonday(today) else WeekCalculator.mondayOf(today)
    }

    private val baseState: kotlinx.coroutines.flow.Flow<PlannerUiState> = weekStart.flatMapLatest { ws ->
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
    }

    val uiState: StateFlow<PlannerUiState> = combine(baseState, limitNotice, nextListLine, generating) { base, notice, line, busy ->
        base.copy(limitNotice = notice, nextListLine = line, generating = busy)
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

    /**
     * First asks the server (only when the plan involves server recipes that are not unlocked yet): it applies the
     * Guest/Free/Paid rules. Only when it says yes (or nothing needs asking) the list is built, as before.
     */
    fun generateShoppingList() {
        if (generating.value) return
        generating.value = true                  // set before launching: a double tap cannot start two runs
        viewModelScope.launch {
            try {
                val plan = plannerRepository.getOrCreatePlan(weekStart.value, settingsRepository.getOnce().defaultHouseholdServings)
                val recipes = recipeRepository.getAllWithIngredientsOnce().associateBy { it.recipe.id }
                val serverIds = plan.meals.mapNotNull { it.recipeId }.mapNotNull { recipes[it]?.recipe?.serverId }.toSet()
                val permission = listPermission.request(serverIds)
                updateNextLine()
                val notice = LimitTexts.notice(permission)
                if (notice != null) {
                    limitNotice.value = notice
                    return@launch
                }
                generationResult.value = plannerRepository.generateShoppingList(plan)
            } finally {
                generating.value = false
            }
        }
    }

    fun dismissLimitNotice() { limitNotice.value = null }

    fun clearGenerationResult() { generationResult.value = null }
}
