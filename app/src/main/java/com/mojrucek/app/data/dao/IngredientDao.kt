package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mojrucek.app.data.entity.Ingredient
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getById(id: Long): Ingredient?

    @Query("SELECT * FROM ingredients WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): Ingredient?

    @Query("SELECT * FROM ingredients WHERE normalizedName IN (:normalizedNames)")
    suspend fun findByNormalizedNames(normalizedNames: List<String>): List<Ingredient>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: Ingredient): Long

    @Update
    suspend fun update(ingredient: Ingredient)

    @Delete
    suspend fun delete(ingredient: Ingredient)

    @Query("UPDATE ingredients SET quantity = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ingredients")
    suspend fun deleteAll()

    @Query("SELECT * FROM ingredients")
    suspend fun getAllOnce(): List<Ingredient>
}
