package com.mojrucek.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.IngredientLine
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.domain.unit.UnitConverter
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    val recipe: RecipeWithIngredients? = null,
    val desiredServings: Int = 1,
    val match: RecipeMatchResult? = null,
    val lastAddedToShoppingList: Boolean = false,
    val lastCooked: Boolean = false,
    val notFound: Boolean = false
)

class RecipeDetailViewModel(
    private val recipeId: Long,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val shoppingListRepository: ShoppingListRepository
) : ViewModel() {

    private val desiredServings = MutableStateFlow<Int?>(null)
    private val oneShotFlags = MutableStateFlow(false to false)

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        recipeRepository.observeWithIngredients(recipeId),
        ingredientRepository.observeAll(),
        desiredServings,
        oneShotFlags
    ) { recipe, inventory, desired, flags ->
        if (recipe == null) {
            RecipeDetailUiState(isLoading = false, notFound = true)
        } else {
            val servings = desired ?: recipe.recipe.servings
            val required = recipeRepository.scaledIngredients(recipe, servings)
            val inventoryLines = inventory.map { IngredientLine(it.name, it.normalizedName, it.quantity, it.unit) }
            val match = com.mojrucek.app.domain.usecase.RecipeMatchingUseCase.match(
                recipeId = recipe.recipe.id,
                recipeName = recipe.recipe.name,
                required = required,
                available = inventoryLines,
                isFavorite = recipe.recipe.isFavorite
            )
            RecipeDetailUiState(
                isLoading = false,
                recipe = recipe,
                desiredServings = servings,
                match = match,
                lastAddedToShoppingList = flags.first,
                lastCooked = flags.second
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeDetailUiState())

    fun setDesiredServings(servings: Int) {
        desiredServings.value = servings.coerceAtLeast(1)
    }

    fun toggleFavorite() {
        val current = uiState.value.recipe ?: return
        viewModelScope.launch { recipeRepository.setFavorite(current.recipe.id, !current.recipe.isFavorite) }
    }

    fun addMissingToShoppingList() {
        val match = uiState.value.match ?: return
        viewModelScope.launch {
            shoppingListRepository.addMissingFromRecipe(recipeId, match.matches, IngredientCategory.DRUGO)
            oneShotFlags.value = true to oneShotFlags.value.second
        }
    }

    fun clearAddedFlag() { oneShotFlags.value = false to oneShotFlags.value.second }

    /** Deducts the scaled ingredient quantities from inventory for whichever ingredients are tracked there. */
    fun confirmCooked() {
        val state = uiState.value
        val recipe = state.recipe ?: return
        viewModelScope.launch {
            val required = recipeRepository.scaledIngredients(recipe, state.desiredServings)
            val inventory = ingredientRepository.getAllOnce()
            val byName = inventory.associateBy { it.normalizedName }
            required.forEach { line ->
                val ingredient = byName[line.normalizedName] ?: return@forEach
                if (!UnitConverter.areCompatible(ingredient.unit, line.unit)) return@forEach
                val usedInIngredientUnit = UnitConverter.convert(line.quantity, line.unit, ingredient.unit) ?: return@forEach
                ingredientRepository.adjustQuantity(ingredient, -usedInIngredientUnit)
            }
            oneShotFlags.value = oneShotFlags.value.first to true
        }
    }

    fun clearCookedFlag() { oneShotFlags.value = oneShotFlags.value.first to false }

    fun duplicate(onDone: (Long) -> Unit) {
        val recipe = uiState.value.recipe ?: return
        viewModelScope.launch { onDone(recipeRepository.duplicate(recipe)) }
    }

    fun delete(onDone: () -> Unit) {
        val recipe = uiState.value.recipe ?: return
        viewModelScope.launch {
            recipeRepository.delete(recipe.recipe)
            onDone()
        }
    }
}
