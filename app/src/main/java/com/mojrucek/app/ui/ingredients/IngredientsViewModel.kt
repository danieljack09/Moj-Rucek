package com.mojrucek.app.ui.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientMergeUseCase
import com.mojrucek.app.repository.IngredientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class IngredientSort { NAME, CATEGORY, EXPIRATION }

data class NewIngredientInput(
    val name: String,
    val category: IngredientCategory,
    val quantity: Double,
    val unit: MeasurementUnit,
    val expirationDateEpochDay: Long?,
    val lowStockThreshold: Double?,
    val notes: String?
)

data class PendingDuplicate(val existing: Ingredient, val incoming: NewIngredientInput)

data class IngredientsUiState(
    val isLoading: Boolean = true,
    val allIngredients: List<Ingredient> = emptyList(),
    val visibleIngredients: List<Ingredient> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: IngredientCategory? = null,
    val sort: IngredientSort = IngredientSort.NAME,
    val lowStockOnly: Boolean = false,
    val expiringOnly: Boolean = false
)

class IngredientsViewModel(private val repository: IngredientRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<IngredientCategory?>(null)
    private val sort = MutableStateFlow(IngredientSort.NAME)
    private val lowStockOnly = MutableStateFlow(false)
    private val expiringOnly = MutableStateFlow(false)

    private val filters = combine(searchQuery, categoryFilter, sort, lowStockOnly, expiringOnly) { q, c, s, l, e ->
        Filters(q, c, s, l, e)
    }

    val uiState: StateFlow<IngredientsUiState> = combine(repository.observeAll(), filters) { all, f ->
        val today = com.mojrucek.app.domain.usecase.WeekCalculator.today().toEpochDay()
        var visible = all
        if (f.query.isNotBlank()) {
            val needle = f.query.trim().lowercase()
            visible = visible.filter { it.normalizedName.contains(needle) }
        }
        if (f.category != null) visible = visible.filter { it.category == f.category }
        if (f.lowStockOnly) visible = visible.filter { it.isLowStock }
        if (f.expiringOnly) visible = visible.filter {
            val exp = it.expirationDateEpochDay
            exp != null && exp in today..(today + 5)
        }
        visible = when (f.sort) {
            IngredientSort.NAME -> visible.sortedBy { it.name.lowercase() }
            IngredientSort.CATEGORY -> visible.sortedBy { it.category.displayName }
            IngredientSort.EXPIRATION -> visible.sortedWith(
                compareBy(nullsLast()) { it.expirationDateEpochDay }
            )
        }
        IngredientsUiState(
            isLoading = false,
            allIngredients = all,
            visibleIngredients = visible,
            searchQuery = f.query,
            categoryFilter = f.category,
            sort = f.sort,
            lowStockOnly = f.lowStockOnly,
            expiringOnly = f.expiringOnly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IngredientsUiState())

    private val _pendingDuplicate = MutableStateFlow<PendingDuplicate?>(null)
    val pendingDuplicate: StateFlow<PendingDuplicate?> = _pendingDuplicate

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setCategoryFilter(c: IngredientCategory?) { categoryFilter.value = c }
    fun setSort(s: IngredientSort) { sort.value = s }
    fun setLowStockOnly(v: Boolean) { lowStockOnly.value = v }
    fun setExpiringOnly(v: Boolean) { expiringOnly.value = v }

    /** Submits the add-ingredient form. If a same-name ingredient exists, asks for confirmation before merging. */
    fun submitNewIngredient(input: NewIngredientInput) {
        viewModelScope.launch {
            val existing = repository.findExistingByName(input.name)
            if (existing != null) {
                _pendingDuplicate.value = PendingDuplicate(existing, input)
            } else {
                insertNow(input)
            }
        }
    }

    fun confirmMergeDuplicate() {
        val pending = _pendingDuplicate.value ?: return
        viewModelScope.launch {
            val result = repository.mergeQuantityInto(pending.existing, pending.incoming.quantity, pending.incoming.unit)
            if (result is IngredientMergeUseCase.Result.UnitMismatch) {
                // Can't combine — fall back to inserting as a separate entry.
                insertNow(pending.incoming)
            }
            _pendingDuplicate.value = null
        }
    }

    fun confirmAddAsSeparateEntry() {
        val pending = _pendingDuplicate.value ?: return
        viewModelScope.launch {
            insertNow(pending.incoming)
            _pendingDuplicate.value = null
        }
    }

    fun dismissDuplicateDialog() { _pendingDuplicate.value = null }

    private suspend fun insertNow(input: NewIngredientInput) {
        repository.insert(
            name = input.name,
            category = input.category,
            quantity = input.quantity,
            unit = input.unit,
            expirationDateEpochDay = input.expirationDateEpochDay,
            lowStockThreshold = input.lowStockThreshold,
            notes = input.notes
        )
    }

    fun updateIngredient(ingredient: Ingredient) {
        viewModelScope.launch { repository.update(ingredient) }
    }

    fun deleteIngredient(ingredient: Ingredient) {
        viewModelScope.launch { repository.delete(ingredient) }
    }

    fun adjustQuantity(ingredient: Ingredient, delta: Double) {
        viewModelScope.launch { repository.adjustQuantity(ingredient, delta) }
    }

    fun markFinished(ingredient: Ingredient) {
        viewModelScope.launch { repository.markFinished(ingredient) }
    }

    private data class Filters(
        val query: String,
        val category: IngredientCategory?,
        val sort: IngredientSort,
        val lowStockOnly: Boolean,
        val expiringOnly: Boolean
    )
}
