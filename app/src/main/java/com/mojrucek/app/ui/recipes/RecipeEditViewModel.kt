package com.mojrucek.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IngredientLineInput(
    val id: Int,
    val name: String = "",
    val quantityText: String = "",
    val unit: MeasurementUnit = MeasurementUnit.PIECE,
    val notes: String = ""
)

data class RecipeEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val description: String = "",
    val servingsText: String = "4",
    val prepTimeText: String = "15",
    val cookTimeText: String = "30",
    val category: RecipeCategory? = null,
    val tagsText: String = "",
    val notes: String = "",
    val steps: List<String> = listOf(""),
    val ingredients: List<IngredientLineInput> = listOf(IngredientLineInput(0)),
    val savedRecipeId: Long? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            servingsText.toIntOrNull()?.let { it > 0 } == true &&
            ingredients.any { it.name.isNotBlank() && it.quantityText.toDoubleOrNull() != null }
}

class RecipeEditViewModel(
    private val recipeId: Long?,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeEditUiState(isLoading = recipeId != null, isNew = recipeId == null))
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private var nextLineId = 1

    init {
        if (recipeId != null) {
            viewModelScope.launch {
                val existing = recipeRepository.getWithIngredients(recipeId)
                if (existing != null) {
                    _uiState.value = RecipeEditUiState(
                        isLoading = false,
                        isNew = false,
                        name = existing.recipe.name,
                        description = existing.recipe.description ?: "",
                        servingsText = existing.recipe.servings.toString(),
                        prepTimeText = existing.recipe.prepTimeMinutes.toString(),
                        cookTimeText = existing.recipe.cookTimeMinutes.toString(),
                        category = existing.recipe.category,
                        tagsText = existing.recipe.tags.joinToString(", "),
                        notes = existing.recipe.notes ?: "",
                        steps = existing.recipe.instructionSteps.ifEmpty { listOf("") },
                        ingredients = existing.ingredients.mapIndexed { index, ri ->
                            IngredientLineInput(index, ri.name, UnitConverterFormat.format(ri.quantity), ri.unit, ri.notes ?: "")
                        }.ifEmpty { listOf(IngredientLineInput(0)) }
                    )
                    nextLineId = _uiState.value.ingredients.size
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun setName(v: String) = update { it.copy(name = v) }
    fun setDescription(v: String) = update { it.copy(description = v) }
    fun setServingsText(v: String) = update { it.copy(servingsText = v) }
    fun setPrepTimeText(v: String) = update { it.copy(prepTimeText = v) }
    fun setCookTimeText(v: String) = update { it.copy(cookTimeText = v) }
    fun setCategory(c: RecipeCategory?) = update { it.copy(category = c) }
    fun setTagsText(v: String) = update { it.copy(tagsText = v) }
    fun setNotes(v: String) = update { it.copy(notes = v) }

    fun setStep(index: Int, value: String) = update { s ->
        s.copy(steps = s.steps.toMutableList().also { it[index] = value })
    }
    fun addStep() = update { it.copy(steps = it.steps + "") }
    fun removeStep(index: Int) = update { s ->
        val newSteps = s.steps.toMutableList().apply { removeAt(index) }
        s.copy(steps = newSteps.ifEmpty { listOf("") })
    }

    fun updateIngredientLine(id: Int, transform: (IngredientLineInput) -> IngredientLineInput) = update { s ->
        s.copy(ingredients = s.ingredients.map { if (it.id == id) transform(it) else it })
    }
    fun addIngredientLine() = update { it.copy(ingredients = it.ingredients + IngredientLineInput(nextLineId++)) }
    fun removeIngredientLine(id: Int) = update { s ->
        val newList = s.ingredients.filterNot { it.id == id }
        s.copy(ingredients = newList.ifEmpty { listOf(IngredientLineInput(nextLineId++)) })
    }

    private fun update(transform: (RecipeEditUiState) -> RecipeEditUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun save() {
        val s = _uiState.value
        if (!s.isValid) return
        viewModelScope.launch {
            val recipe = Recipe(
                id = recipeId ?: 0,
                name = s.name.trim(),
                description = s.description.ifBlank { null },
                servings = s.servingsText.toIntOrNull() ?: 1,
                prepTimeMinutes = s.prepTimeText.toIntOrNull() ?: 0,
                cookTimeMinutes = s.cookTimeText.toIntOrNull() ?: 0,
                instructionSteps = s.steps.map { it.trim() }.filter { it.isNotBlank() },
                category = s.category,
                tags = s.tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                notes = s.notes.ifBlank { null }
            )
            val ingredients = s.ingredients
                .filter { it.name.isNotBlank() && it.quantityText.toDoubleOrNull() != null }
                .map {
                    RecipeIngredient(
                        recipeId = 0,
                        name = it.name.trim(),
                        normalizedName = RecipeIngredient.normalize(it.name),
                        quantity = it.quantityText.toDouble(),
                        unit = it.unit,
                        notes = it.notes.ifBlank { null }
                    )
                }
            val savedId = recipeRepository.upsert(recipe, ingredients)
            _uiState.value = _uiState.value.copy(savedRecipeId = savedId)
        }
    }
}

private object UnitConverterFormat {
    fun format(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}
