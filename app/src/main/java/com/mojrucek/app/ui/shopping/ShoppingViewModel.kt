package com.mojrucek.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.ShoppingListRepository
import com.mojrucek.app.repository.ShoppingUndo
import com.mojrucek.app.ui.components.formatQuantityWithUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card on the shopping screen: the item plus where it came from ("од: Тавче гравче"). */
data class ShoppingRowUi(
    val item: ShoppingListItem,
    val sourceLabel: String?
)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val unpurchasedByCategory: Map<IngredientCategory, List<ShoppingRowUi>> = emptyMap(),
    val totalCount: Int = 0
)

/** A snackbar to show after an action; [undo] backs its "Врати" button. */
data class ShoppingEvent(val message: String, val undo: ShoppingUndo)

class ShoppingViewModel(
    private val repository: ShoppingListRepository,
    recipeRepository: RecipeRepository
) : ViewModel() {

    private val recipeNames: Flow<Map<Long, String>> =
        recipeRepository.observeAllWithIngredients().map { recipes ->
            recipes.associate { it.recipe.id to it.recipe.name }
        }

    val uiState: StateFlow<ShoppingUiState> =
        combine(repository.observeAll(), recipeNames) { items, names ->
            val rows = items.map { item ->
                ShoppingRowUi(item = item, sourceLabel = sourceLabelFor(item, names))
            }
            ShoppingUiState(
                isLoading = false,
                unpurchasedByCategory = rows.groupBy { it.item.category }
                    .toSortedMap(compareBy { STORE_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }),
                totalCount = rows.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingUiState())

    private val _events = Channel<ShoppingEvent>(Channel.BUFFERED)
    val events: Flow<ShoppingEvent> = _events.receiveAsFlow()

    fun addManualItem(name: String, quantity: Double, unit: MeasurementUnit, category: IngredientCategory) {
        viewModelScope.launch { repository.addOrCombine(name, quantity, unit, category) }
    }

    fun addToInventory(item: ShoppingListItem, purchasedQuantity: Double) {
        viewModelScope.launch {
            val outcome = repository.addPurchasedItemToInventory(item, purchasedQuantity)
            val base = if (outcome.addedToInventory) {
                "Во инвентар: ${item.name}"
            } else {
                // The stored inventory entry uses a unit this purchase can't be combined with.
                "Купено: ${item.name} (единицата не одговара со инвентарот, не е додадено)"
            }
            _events.send(ShoppingEvent(withRemainder(base, item, outcome.remainingQuantity), outcome.undo))
        }
    }

    fun keepWithoutAdding(item: ShoppingListItem, purchasedQuantity: Double) {
        viewModelScope.launch {
            val outcome = repository.keepWithoutAddingToInventory(item, purchasedQuantity)
            _events.send(ShoppingEvent(withRemainder("Купено: ${item.name}", item, outcome.remainingQuantity), outcome.undo))
        }
    }

    fun delete(item: ShoppingListItem) {
        viewModelScope.launch {
            val undo = repository.deleteWithUndo(item)
            _events.send(ShoppingEvent("Избришано: ${item.name}", undo))
        }
    }

    fun undo(undo: ShoppingUndo) {
        viewModelScope.launch { repository.undo(undo) }
    }

    private fun withRemainder(message: String, item: ShoppingListItem, remaining: Double): String =
        if (remaining > 0.0) "$message · уште ${formatQuantityWithUnit(remaining, item.unit)} во листата" else message

    private fun sourceLabelFor(item: ShoppingListItem, recipeNames: Map<Long, String>): String? {
        item.sourceRecipeId?.let { id -> recipeNames[id]?.let { return "од: $it" } }
        if (item.sourceWeeklyPlanId != null) return "од: неделниот план"
        return null
    }

    companion object {
        /**
         * Categories in the order you usually walk a shop: fresh produce and bread first,
         * then the fridges, dry goods, drinks, and frozen food last so it stays cold.
         */
        val STORE_ORDER = listOf(
            IngredientCategory.ZELENCUK,
            IngredientCategory.OVOSJE,
            IngredientCategory.LEB_I_PEKARSKI_PROIZVODI,
            IngredientCategory.MESO,
            IngredientCategory.MLECNI_PROIZVODI,
            IngredientCategory.JAJCA,
            IngredientCategory.ZITARICI_I_TESTENINI,
            IngredientCategory.KONZERVIRANA_HRANA,
            IngredientCategory.MASLA_I_DODATOCI,
            IngredientCategory.ZACINI,
            IngredientCategory.PIJALOCI,
            IngredientCategory.ZAMRZNATA_HRANA,
            IngredientCategory.DRUGO
        )
    }
}
