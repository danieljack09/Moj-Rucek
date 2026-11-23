package com.mojrucek.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(tableName = "recipes")
@Serializable
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val servings: Int = 3,
    val prepTimeMinutes: Int = 0,
    val cookTimeMinutes: Int = 0,
    /** One instruction step per list entry, shown numbered in the UI. */
    val instructionSteps: List<String> = emptyList(),
    val category: RecipeCategory? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val imageUri: String? = null,
    val isFavorite: Boolean = false,
    val isUserCreated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Id of this recipe on the admin server; null for recipes that exist only on this device. */
    val serverId: Long? = null,
    // Room v4: ownership and sync groundwork, see SyncColumns.kt.
    // Recipes that come from the server are shared by everyone, so their ownerUid stays "".
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") @Transient val ownerUid: String = "",
    @ColumnInfo(defaultValue = "''") val uuid: String = newUuid(),
    @Transient val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") @Transient val dirty: Boolean = true
) {
    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes
}
