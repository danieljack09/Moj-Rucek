package com.mojrucek.app.repository

import android.database.sqlite.SQLiteConstraintException
import com.mojrucek.app.data.dao.ShoppingListDao
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.model.GeneratedShoppingItem
import com.mojrucek.app.domain.model.IngredientMatch
import com.mojrucek.app.domain.model.MatchStatus
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientMergeUseCase
import com.mojrucek.app.domain.usecase.LegacyPurchasedItems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/** The shopping list of the account that is signed in: every call goes through [owner]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListRepository(
    private val dao: ShoppingListDao,
    private val ingredientRepository: IngredientRepository,
    private val owner: CurrentOwner
) {

    fun observeAll(): Flow<List<ShoppingListItem>> = owner.flow.flatMapLatest { dao.observeAll(it) }

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
        val existing = dao.findUnpurchasedByNormalizedName(normalized, owner.key)
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
                sourceWeeklyPlanId = sourceWeeklyPlanId,
                ownerUid = owner.key
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
        dao.getAllOnce(owner.key)
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

    /**
     * "Додај во домашни намирници": merges [purchasedQuantity] into inventory, then removes the
     * item from the list if it was fully bought, or keeps the remainder on the list otherwise.
     * Returns what changed, so the purchase can be undone from the snackbar.
     */
    suspend fun addPurchasedItemToInventory(item: ShoppingListItem, purchasedQuantity: Double): PurchaseOutcome {
        val existing = ingredientRepository.findExistingByName(item.name)
        var addedToInventory = true
        var insertedId: Long? = null
        if (existing != null) {
            val merge = ingredientRepository.mergeQuantityInto(existing, purchasedQuantity, item.unit)
            addedToInventory = merge is IngredientMergeUseCase.Result.Merged
        } else {
            insertedId = ingredientRepository.insert(
                name = item.name,
                category = item.category,
                quantity = purchasedQuantity,
                unit = item.unit,
                expirationDateEpochDay = null,
                lowStockThreshold = null,
                notes = null
            )
        }
        val remaining = removeOrReduce(item, purchasedQuantity)
        return PurchaseOutcome(
            remainingQuantity = remaining,
            addedToInventory = addedToInventory,
            undo = ShoppingUndo(
                originalItem = item,
                inventoryBefore = existing.takeIf { addedToInventory },
                insertedIngredientId = insertedId
            )
        )
    }

    /**
     * The user bought [purchasedQuantity] for immediate use and does not want it added to the
     * home inventory. Any amount still missing stays on the list.
     */
    suspend fun keepWithoutAddingToInventory(item: ShoppingListItem, purchasedQuantity: Double): PurchaseOutcome {
        val remaining = removeOrReduce(item, purchasedQuantity)
        return PurchaseOutcome(
            remainingQuantity = remaining,
            addedToInventory = false,
            undo = ShoppingUndo(originalItem = item)
        )
    }

    /** Deletes the item and returns how to put it back. */
    suspend fun deleteWithUndo(item: ShoppingListItem): ShoppingUndo {
        dao.delete(item)
        return ShoppingUndo(originalItem = item)
    }

    /**
     * Reverts a purchase or delete: puts the inventory back the way it was and restores the
     * shopping list item with its original quantity.
     */
    suspend fun undo(undo: ShoppingUndo) {
        // Another account is signed in now: this undo belongs to the previous one and must not touch anything.
        if (undo.originalItem.ownerUid != owner.key) return
        undo.inventoryBefore?.let { ingredientRepository.setQuantity(it.id, it.quantity) }
        undo.insertedIngredientId?.let { id ->
            ingredientRepository.getById(id)?.let { ingredientRepository.delete(it) }
        }
        val current = dao.getAllOnce(owner.key).firstOrNull { it.id == undo.originalItem.id }
        if (current != null) {
            dao.update(undo.originalItem)
        } else {
            try {
                dao.insert(undo.originalItem)
            } catch (e: SQLiteConstraintException) {
                // The id was reused in the meantime: restore it as a new row instead.
                dao.insert(undo.originalItem.copy(id = 0))
            }
        }
    }

    /**
     * Deletes [item] when [purchasedQuantity] covers it, otherwise lowers its quantity to what is
     * still needed. Returns the quantity left on the list (0 when the item was removed).
     */
    private suspend fun removeOrReduce(item: ShoppingListItem, purchasedQuantity: Double): Double {
        val remaining = item.quantity - purchasedQuantity
        // The epsilon absorbs floating-point leftovers so a fully bought line is never kept as ~0.
        return if (purchasedQuantity >= item.quantity || remaining <= REMAINDER_EPSILON) {
            dao.delete(item)
            0.0
        } else {
            dao.update(item.copy(quantity = remaining))
            remaining
        }
    }

    private companion object {
        const val REMAINDER_EPSILON = 1e-9
    }

    /** The list of [ownerKey] (default: the signed-in account). */
    suspend fun getAllOnce(ownerKey: String = owner.key): List<ShoppingListItem> = dao.getAllOnce(ownerKey)

    suspend fun clearAll() = dao.deleteAll(owner.key)

    /** Replaces the signed-in account's list with [items]; every restored row becomes theirs. */
    suspend fun restoreAll(items: List<ShoppingListItem>) {
        val ownerKey = owner.key
        dao.deleteAll(ownerKey)
        items.forEach { dao.insert(it.copy(id = 0, ownerUid = ownerKey)) }
        // Backups made before purchased items were removed immediately can still contain some.
        migrateLegacyPurchasedItems()
    }

    /**
     * One-time cleanup of items flagged as purchased by older versions (see [LegacyPurchasedItems]).
     * Safe to run repeatedly. Items are re-opened before anything is deleted, so an interruption
     * cannot lose an unresolved item.
     */
    suspend fun migrateLegacyPurchasedItems() {
        val plan = LegacyPurchasedItems.plan(dao.getAllOnce(owner.key))
        if (plan.isEmpty) return
        plan.reopen.forEach { dao.update(it) }
        if (plan.deleteIds.isNotEmpty()) {
            val doomed = plan.deleteIds.toSet()
            dao.getAllOnce(owner.key).filter { it.id in doomed }.forEach { dao.delete(it) }
        }
    }
}

/** Everything a purchase or delete changed, so the snackbar's "Врати" can revert it. */
data class ShoppingUndo(
    val originalItem: ShoppingListItem,
    /** The inventory entry as it was before the purchase was merged into it. */
    val inventoryBefore: Ingredient? = null,
    /** The inventory entry the purchase created, if there was none before. */
    val insertedIngredientId: Long? = null
)

data class PurchaseOutcome(
    /** What is still on the shopping list after this purchase; 0 when the item is gone. */
    val remainingQuantity: Double,
    /** False when the item was kept out of inventory, or its unit did not match the stored one. */
    val addedToInventory: Boolean,
    val undo: ShoppingUndo
)
