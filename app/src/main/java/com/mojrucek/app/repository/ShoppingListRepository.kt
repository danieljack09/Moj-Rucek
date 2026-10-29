package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.ShoppingListDao
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.model.GeneratedShoppingItem
import com.mojrucek.app.domain.model.IngredientMatch
import com.mojrucek.app.domain.model.MatchStatus
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientMergeUseCase
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(
    private val dao: ShoppingListDao,
    private val ingredientRepository: IngredientRepository
) {

    fun observeAll(): Flow<List<ShoppingListItem>> = dao.observeAll()

    /** Adds an item, combining it into an existing unpurchased same-name entry when units are compatible (spec §6). */
    suspend fun addOrCombine(
        name: String,
        quantity: Double,
        unit: MeasurementUnit,
        category: IngredientCategory,
        sourceRecipeId: Long? = null,
        sourceWeeklyPlanId: Long? = null
    ): Long {
        val normalized = ShoppingListItem.normalize(name)
        val existing = dao.findUnpurchasedByNormalizedName(normalized)
        if (existing != null) {
            val merge = IngredientMergeUseCase.mergeQuantity(existing.quantity, existing.unit, quantity, unit)
            if (merge is IngredientMergeUseCase.Result.Merged) {
                dao.update(existing.copy(quantity = merge.combinedQuantity))
                return existing.id
            }
            // Unit mismatch: keep as a distinct row rather than guessing a combined amount.
        }
        return dao.insert(
            ShoppingListItem(
                name = name.trim(),
                normalizedName = normalized,
                quantity = quantity,
                unit = unit,
                category = category,
                sourceRecipeId = sourceRecipeId,
                sourceWeeklyPlanId = sourceWeeklyPlanId
            )
        )
    }

    /** Adds every missing/insufficient ingredient of a recipe match (spec §8 "add missing ingredients with one action"). */
    suspend fun addMissingFromRecipe(recipeId: Long, matches: List<IngredientMatch>, defaultCategory: IngredientCategory = IngredientCategory.DRUGO) {
        matches.filter { it.status != MatchStatus.AVAILABLE }.forEach { match ->
            val needed = when (match.status) {
                MatchStatus.MISSING, MatchStatus.UNIT_MISMATCH -> match.requiredQuantity
                MatchStatus.INSUFFICIENT -> (match.requiredQuantity - match.availableQuantity).coerceAtLeast(0.0)
                MatchStatus.AVAILABLE -> 0.0
            }
            if (needed <= 0.0) return@forEach
            addOrCombine(
                name = match.name,
                quantity = needed,
                unit = match.requiredUnit,
                category = defaultCategory,
                sourceRecipeId = recipeId
            )
        }
    }

    /**
     * Replaces the previously auto-generated (and still unpurchased) items for [weeklyPlanId]
     * with [items], merging into any other matching unpurchased entry rather than duplicating it.
     * Never touches already-purchased items, so re-generating a list after editing the plan
     * cannot undo shopping someone already did (spec §9 "without creating duplicates").
     */
    suspend fun replaceGeneratedItemsForPlan(weeklyPlanId: Long, items: List<GeneratedShoppingItem>) {
        dao.getAllOnce()
            .filter { it.sourceWeeklyPlanId == weeklyPlanId && !it.isPurchased }
            .forEach { dao.delete(it) }

        items.forEach { generated ->
            addOrCombine(
                name = generated.name,
                quantity = generated.quantity,
                unit = generated.unit,
                category = generated.category,
                sourceWeeklyPlanId = weeklyPlanId
            )
        }
    }

    suspend fun update(item: ShoppingListItem) = dao.update(item)

    suspend fun delete(item: ShoppingListItem) = dao.delete(item)

    suspend fun setPurchased(item: ShoppingListItem, purchased: Boolean) =
        dao.update(item.copy(isPurchased = purchased, isHandled = if (!purchased) false else item.isHandled))

    /** "Додај во домашни намирници": merges the purchased item into inventory and marks it handled. */
    suspend fun addPurchasedItemToInventory(item: ShoppingListItem) {
        val existing = ingredientRepository.findExistingByName(item.name)
        if (existing != null) {
            ingredientRepository.mergeQuantityInto(existing, item.quantity, item.unit)
        } else {
            ingredientRepository.insert(
                name = item.name,
                category = item.category,
                quantity = item.quantity,
                unit = item.unit,
                expirationDateEpochDay = null,
                lowStockThreshold = null,
                notes = null
            )
        }
        dao.update(item.copy(isHandled = true))
    }

    /** The user bought it for immediate use and does not want it added to the home inventory. */
    suspend fun keepWithoutAddingToInventory(item: ShoppingListItem) {
        dao.update(item.copy(isHandled = true))
    }

    suspend fun clearPurchased() = dao.clearPurchased()

    suspend fun getAllOnce(): List<ShoppingListItem> = dao.getAllOnce()

    suspend fun clearAll() = dao.deleteAll()

    suspend fun restoreAll(items: List<ShoppingListItem>) {
        dao.deleteAll()
        items.forEach { dao.insert(it.copy(id = 0)) }
    }
}
