package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes
}
