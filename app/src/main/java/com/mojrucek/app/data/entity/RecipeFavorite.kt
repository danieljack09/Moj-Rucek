package com.mojrucek.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A favourite recipe of ONE user (Room v4). Until now favourites were a flag on the recipe row, so every
 * account on the phone shared them. Created and filled in by the v3 -> v4 migration; the app starts using
 * it in a later step (see docs/room-v4-ownership-and-sync-design.md), so for now nothing reads it.
 *
 * [recipeRef] says which recipe: "s:<server id>" for a recipe from the server, "u:<uuid>" for the user's own.
 * [ownerUid] is "" until the existing data is claimed by an account.
 */
@Entity(tableName = "recipe_favorites", primaryKeys = ["ownerUid", "recipeRef"])
data class RecipeFavorite(
    val ownerUid: String,
    val recipeRef: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)
