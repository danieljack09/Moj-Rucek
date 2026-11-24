package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mojrucek.app.data.entity.UnlockedRecipeDetail
import kotlinx.coroutines.flow.Flow

/** Unlocked recipe details, one set per account. Every read and delete names the owner. */
@Dao
interface UnlockedDetailsDao {

    @Query("SELECT * FROM unlocked_recipe_details WHERE ownerUid = :ownerUid")
    fun observeAll(ownerUid: String): Flow<List<UnlockedRecipeDetail>>

    @Query("SELECT * FROM unlocked_recipe_details WHERE ownerUid = :ownerUid AND serverId = :serverId")
    suspend fun find(ownerUid: String, serverId: Long): UnlockedRecipeDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(details: List<UnlockedRecipeDetail>)

    @Query("DELETE FROM unlocked_recipe_details WHERE ownerUid = :ownerUid")
    suspend fun deleteAll(ownerUid: String)

    /** Removes what has run out at server time [serverNowMs]; entries without a deadline stay. */
    @Query("DELETE FROM unlocked_recipe_details WHERE ownerUid = :ownerUid AND unlockedUntil IS NOT NULL AND unlockedUntil <= :serverNowMs")
    suspend fun deleteExpired(ownerUid: String, serverNowMs: Long)

    /** A newer answer from the server replaces everything unlocked before it, in one step. */
    @Transaction
    suspend fun replaceAll(ownerUid: String, details: List<UnlockedRecipeDetail>) {
        deleteAll(ownerUid)
        if (details.isNotEmpty()) insertAll(details)
    }
}
