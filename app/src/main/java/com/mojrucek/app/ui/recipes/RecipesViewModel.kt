package com.mojrucek.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.Readiness
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReadinessFilter { ALL, CAN_COOK_NOW, ALMOST_READY, NEEDS_SHOPPING }

data class RecipeListItem(val match: RecipeMatchResult, val category: RecipeCategory?, val totalTimeMinutes: Int)

data class RecipesUiState(
    val isLoading: Boolean = true,
    val items: List<RecipeListItem> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: RecipeCategory? = null,
    val readinessFilter: ReadinessFilter = ReadinessFilter.ALL,
    val favoritesOnly: Boolean = false,
    val maxPrepPlusCookMinutes: Int? = null
)

class RecipesViewModel(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<RecipeCategory?>(null)
    private val readinessFilter = MutableStateFlow(ReadinessFilter.ALL)
    private val favoritesOnly = MutableStateFlow(false)
    private val maxTime = MutableStateFlow<Int?>(null)

    private val filters = combine(searchQuery, categoryFilter, readinessFilter, favoritesOnly, maxTime) { q, c, r, f, t ->
        Filters(q, c, r, f, t)
    }

    val uiState: StateFlow<RecipesUiState> = combine(
        recipeRepository.observeAllWithIngredients(),
        ingredientRepository.observeAll(),
        filters
    ) { recipes, inventory, f ->
        val matches = recipeRepository.matchAll(recipes, inventory)
        val byId = recipes.associateBy { it.recipe.id }

        var items = matches.map {
            val recipe = byId.getValue(it.recipeId).recipe
            RecipeListItem(it, recipe.category, recipe.totalTimeMinutes)
        }

        if (f.query.isNotBlank()) {
            val needle = f.query.trim().lowercase()
            items = items.filter { it.match.recipeName.lowercase().contains(needle) }
        }
        if (f.category != null) items = items.filter { it.category == f.category }
        if (f.favoritesOnly) items = items.filter { it.match.isFavorite }
        if (f.maxMinutes != null) items = items.filter { it.totalTimeMinutes <= f.maxMinutes }
        items = when (f.readiness) {
            ReadinessFilter.ALL -> items
            ReadinessFilter.CAN_COOK_NOW -> items.filter { it.match.readiness == Readiness.CAN_COOK_NOW }
            ReadinessFilter.ALMOST_READY -> items.filter { it.match.readiness == Readiness.ALMOST_READY }
            ReadinessFilter.NEEDS_SHOPPING -> items.filter { it.match.readiness == Readiness.NEEDS_SHOPPING }
        }

        items = items.sortedWith(
            compareByDescending<RecipeListItem> { it.match.canCookNow }
                .thenByDescending { it.match.coveragePercent }
                .thenBy { it.match.recipeName.lowercase() }
        )

        RecipesUiState(
            isLoading = false,
            items = items,
            searchQuery = f.query,
            categoryFilter = f.category,
            readinessFilter = f.readiness,
            favoritesOnly = f.favoritesOnly,
            maxPrepPlusCookMinutes = f.maxMinutes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipesUiState())

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setCategoryFilter(c: RecipeCategory?) { categoryFilter.value = c }
    fun setReadinessFilter(r: ReadinessFilter) { readinessFilter.value = r }
    fun setFavoritesOnly(v: Boolean) { favoritesOnly.value = v }
    fun setMaxTime(minutes: Int?) { maxTime.value = minutes }

    fun toggleFavorite(recipeId: Long, isFavorite: Boolean) {
        viewModelScope.launch { recipeRepository.setFavorite(recipeId, isFavorite) }
    }

    fun duplicate(recipe: RecipeWithIngredients) {
        viewModelScope.launch { recipeRepository.duplicate(recipe) }
    }

    fun delete(recipe: RecipeWithIngredients) {
        viewModelScope.launch { recipeRepository.delete(recipe.recipe) }
    }

    private data class Filters(
        val query: String,
        val category: RecipeCategory?,
        val readiness: ReadinessFilter,
        val favoritesOnly: Boolean,
        val maxMinutes: Int?
    )
}
