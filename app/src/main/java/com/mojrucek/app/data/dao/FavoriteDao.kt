package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mojrucek.app.data.entity.RecipeFavorite
import kotlinx.coroutines.flow.Flow

/** Favourite recipes, one set per account. Every read and delete names the owner. */
@Dao
interface FavoriteDao {

    @Query("SELECT recipeRef FROM recipe_favorites WHERE ownerUid = :ownerUid")
    fun observeRefs(ownerUid: String): Flow<List<String>>

    @Query("SELECT recipeRef FROM recipe_favorites WHERE ownerUid = :ownerUid")
    suspend fun getRefs(ownerUid: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: RecipeFavorite)

    @Query("DELETE FROM recipe_favorites WHERE ownerUid = :ownerUid AND recipeRef = :recipeRef")
    suspend fun remove(ownerUid: String, recipeRef: String)

    @Query("DELETE FROM recipe_favorites WHERE ownerUid = :ownerUid")
    suspend fun removeAll(ownerUid: String)
}
