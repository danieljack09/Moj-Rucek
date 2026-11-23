package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mojrucek.app.data.entity.Ingredient
import kotlinx.coroutines.flow.Flow

/** The pantry. Every read and delete names the owner, so one account can never see or remove another's rows. */
@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients WHERE ownerUid = :ownerUid ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(ownerUid: String): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE id = :id AND ownerUid = :ownerUid")
    suspend fun getById(id: Long, ownerUid: String): Ingredient?

    @Query("SELECT * FROM ingredients WHERE normalizedName = :normalizedName AND ownerUid = :ownerUid LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String, ownerUid: String): Ingredient?

    @Query("SELECT * FROM ingredients WHERE normalizedName IN (:normalizedNames) AND ownerUid = :ownerUid")
    suspend fun findByNormalizedNames(normalizedNames: List<String>, ownerUid: String): List<Ingredient>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: Ingredient): Long

    @Update
    suspend fun update(ingredient: Ingredient)

    @Delete
    suspend fun delete(ingredient: Ingredient)

    @Query("UPDATE ingredients SET quantity = :quantity, updatedAt = :updatedAt WHERE id = :id AND ownerUid = :ownerUid")
    suspend fun updateQuantity(id: Long, quantity: Double, ownerUid: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ingredients WHERE ownerUid = :ownerUid")
    suspend fun deleteAll(ownerUid: String)

    @Query("SELECT * FROM ingredients WHERE ownerUid = :ownerUid")
    suspend fun getAllOnce(ownerUid: String): List<Ingredient>
}
