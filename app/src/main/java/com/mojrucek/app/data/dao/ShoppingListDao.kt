package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mojrucek.app.data.entity.ShoppingListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_list_items ORDER BY isPurchased ASC, category ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_items WHERE isPurchased = 0 AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findUnpurchasedByNormalizedName(normalizedName: String): ShoppingListItem?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ShoppingListItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ShoppingListItem>): List<Long>

    @Update
    suspend fun update(item: ShoppingListItem)

    @Delete
    suspend fun delete(item: ShoppingListItem)

    @Query("DELETE FROM shopping_list_items WHERE isPurchased = 1")
    suspend fun clearPurchased()

    @Query("SELECT * FROM shopping_list_items")
    suspend fun getAllOnce(): List<ShoppingListItem>

    @Query("DELETE FROM shopping_list_items")
    suspend fun deleteAll()
}
