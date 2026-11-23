package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.IngredientDao
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientMergeUseCase
import com.mojrucek.app.domain.usecase.WeekCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The pantry of the account that is signed in: every call goes through [owner], so accounts never mix. */
@OptIn(ExperimentalCoroutinesApi::class)
class IngredientRepository(private val dao: IngredientDao, private val owner: CurrentOwner) {

    fun observeAll(): Flow<List<Ingredient>> = owner.flow.flatMapLatest { dao.observeAll(it) }

    fun observeLowStock(): Flow<List<Ingredient>> = observeAll().map { list -> list.filter { it.isLowStock } }

    fun observeExpiringSoon(withinDays: Long = 5): Flow<List<Ingredient>> = observeAll().map { list ->
        val today = WeekCalculator.today().toEpochDay()
        list.filter { ingredient ->
            val exp = ingredient.expirationDateEpochDay ?: return@filter false
            exp in today..(today + withinDays)
        }
    }

    suspend fun getById(id: Long): Ingredient? = dao.getById(id, owner.key)

    suspend fun findExistingByName(name: String): Ingredient? =
        dao.findByNormalizedName(Ingredient.normalize(name), owner.key)

    suspend fun findManyByNames(names: Collection<String>): List<Ingredient> =
        dao.findByNormalizedNames(names.map { Ingredient.normalize(it) }.distinct(), owner.key)

    suspend fun insert(
        name: String,
        category: IngredientCategory,
        quantity: Double,
        unit: MeasurementUnit,
        expirationDateEpochDay: Long?,
        lowStockThreshold: Double?,
        notes: String?
    ): Long = dao.insert(
        Ingredient(
            name = name.trim(),
            normalizedName = Ingredient.normalize(name),
            category = category,
            quantity = quantity,
            unit = unit,
            expirationDateEpochDay = expirationDateEpochDay,
            lowStockThreshold = lowStockThreshold,
            notes = notes?.takeIf { it.isNotBlank() },
            ownerUid = owner.key
        )
    )

    suspend fun update(ingredient: Ingredient) = dao.update(ingredient.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(ingredient: Ingredient) = dao.delete(ingredient)

    /** Adds [addQuantity] of [addUnit] onto an existing entry when units are compatible. */
    suspend fun mergeQuantityInto(
        existing: Ingredient,
        addQuantity: Double,
        addUnit: MeasurementUnit
    ): IngredientMergeUseCase.Result {
        val result = IngredientMergeUseCase.mergeQuantity(
            existingQuantity = existing.quantity,
            existingUnit = existing.unit,
            incomingQuantity = addQuantity,
            incomingUnit = addUnit
        )
        if (result is IngredientMergeUseCase.Result.Merged) {
            dao.updateQuantity(existing.id, result.combinedQuantity, existing.ownerUid)
        }
        return result
    }

    suspend fun setQuantity(id: Long, quantity: Double) =
        dao.updateQuantity(id, quantity.coerceAtLeast(0.0), owner.key)

    /** Decreasing to zero (or below) removes the ingredient instead of leaving an empty entry behind. */
    suspend fun adjustQuantity(ingredient: Ingredient, delta: Double) {
        val newQuantity = (ingredient.quantity + delta).coerceAtLeast(0.0)
        if (newQuantity <= 0.0) {
            dao.delete(ingredient)
        } else {
            dao.updateQuantity(ingredient.id, newQuantity, ingredient.ownerUid)
        }
    }

    suspend fun markFinished(ingredient: Ingredient) = dao.delete(ingredient)

    /** The pantry of [ownerKey] (default: the signed-in account). */
    suspend fun getAllOnce(ownerKey: String = owner.key): List<Ingredient> = dao.getAllOnce(ownerKey)

    suspend fun clearAll() = dao.deleteAll(owner.key)

    /** Replaces the signed-in account's pantry with [ingredients]; every restored row becomes theirs. */
    suspend fun restoreAll(ingredients: List<Ingredient>) {
        val ownerKey = owner.key
        dao.deleteAll(ownerKey)
        ingredients.forEach { dao.insert(it.copy(id = 0, ownerUid = ownerKey)) }
    }
}
