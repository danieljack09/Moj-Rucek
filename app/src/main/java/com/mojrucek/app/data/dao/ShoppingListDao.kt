package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mojrucek.app.data.entity.ShoppingListItem
import kotlinx.coroutines.flow.Flow

/** The shopping list. Reads and bulk deletes name the owner. */
@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_list_items WHERE ownerUid = :ownerUid ORDER BY isPurchased ASC, category ASC, name COLLATE NOCASE ASC")
    fun observeAll(ownerUid: String): Flow<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_items WHERE isPurchased = 0 AND normalizedName = :normalizedName AND ownerUid = :ownerUid LIMIT 1")
    suspend fun findUnpurchasedByNormalizedName(normalizedName: String, ownerUid: String): ShoppingListItem?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ShoppingListItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ShoppingListItem>): List<Long>

    @Update
    suspend fun update(item: ShoppingListItem)

    @Delete
    suspend fun delete(item: ShoppingListItem)

    @Query("SELECT * FROM shopping_list_items WHERE ownerUid = :ownerUid")
    suspend fun getAllOnce(ownerUid: String): List<ShoppingListItem>

    @Query("DELETE FROM shopping_list_items WHERE ownerUid = :ownerUid")
    suspend fun deleteAll(ownerUid: String)
}
