package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.IngredientDao
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientMergeUseCase
import com.mojrucek.app.domain.usecase.WeekCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IngredientRepository(private val dao: IngredientDao) {

    fun observeAll(): Flow<List<Ingredient>> = dao.observeAll()

    fun observeLowStock(): Flow<List<Ingredient>> = dao.observeAll().map { list -> list.filter { it.isLowStock } }

    fun observeExpiringSoon(withinDays: Long = 5): Flow<List<Ingredient>> = dao.observeAll().map { list ->
        val today = WeekCalculator.today().toEpochDay()
        list.filter { ingredient ->
            val exp = ingredient.expirationDateEpochDay ?: return@filter false
            exp in today..(today + withinDays)
        }
    }

    suspend fun getById(id: Long): Ingredient? = dao.getById(id)

    suspend fun findExistingByName(name: String): Ingredient? =
        dao.findByNormalizedName(Ingredient.normalize(name))

    suspend fun findManyByNames(names: Collection<String>): List<Ingredient> =
        dao.findByNormalizedNames(names.map { Ingredient.normalize(it) }.distinct())

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
            notes = notes?.takeIf { it.isNotBlank() }
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
            dao.updateQuantity(existing.id, result.combinedQuantity)
        }
        return result
    }

    suspend fun setQuantity(id: Long, quantity: Double) =
        dao.updateQuantity(id, quantity.coerceAtLeast(0.0))

    suspend fun adjustQuantity(ingredient: Ingredient, delta: Double) =
        dao.updateQuantity(ingredient.id, (ingredient.quantity + delta).coerceAtLeast(0.0))

    suspend fun markFinished(ingredient: Ingredient) = dao.updateQuantity(ingredient.id, 0.0)

    suspend fun getAllOnce(): List<Ingredient> = dao.getAllOnce()

    suspend fun clearAll() = dao.deleteAll()

    suspend fun restoreAll(ingredients: List<Ingredient>) {
        dao.deleteAll()
        ingredients.forEach { dao.insert(it.copy(id = 0)) }
    }
}
