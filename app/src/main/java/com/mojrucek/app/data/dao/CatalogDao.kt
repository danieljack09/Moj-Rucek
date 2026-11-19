package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mojrucek.app.data.entity.CatalogIngredient
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalog_ingredients ORDER BY name COLLATE NOCASE ASC")
    fun observeIngredients(): Flow<List<CatalogIngredient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIngredient(ingredient: CatalogIngredient)

    @Query("DELETE FROM catalog_ingredients WHERE serverId = :serverId")
    suspend fun deleteIngredient(serverId: Long)
}
