package com.mojrucek.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val unpurchasedByCategory: Map<IngredientCategory, List<ShoppingListItem>> = emptyMap()
)

class ShoppingViewModel(private val repository: ShoppingListRepository) : ViewModel() {

    val uiState: StateFlow<ShoppingUiState> = repository.observeAll().map { items ->
        ShoppingUiState(
            isLoading = false,
            unpurchasedByCategory = items.groupBy { it.category }
                .toSortedMap(compareBy { it.displayName })
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingUiState())

    fun addManualItem(name: String, quantity: Double, unit: MeasurementUnit, category: IngredientCategory) {
        viewModelScope.launch { repository.addOrCombine(name, quantity, unit, category) }
    }

    fun addToInventory(item: ShoppingListItem) {
        viewModelScope.launch { repository.addPurchasedItemToInventory(item) }
    }

    fun keepWithoutAdding(item: ShoppingListItem) {
        viewModelScope.launch { repository.keepWithoutAddingToInventory(item) }
    }

    fun delete(item: ShoppingListItem) {
        viewModelScope.launch { repository.delete(item) }
    }
}
