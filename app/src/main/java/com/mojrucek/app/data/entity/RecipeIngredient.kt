package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mojrucek.app.domain.unit.MeasurementUnit
import kotlinx.serialization.Serializable

/**
 * One required ingredient line of a [Recipe], at the recipe's base
 * [Recipe.servings] serving count.
 *
 * Matching against the home inventory is done by [normalizedName], not by a
 * foreign key to [Ingredient] — a recipe can require an ingredient that is
 * not currently (or ever) tracked in the inventory, and renaming an
 * inventory item should never silently break a recipe.
 */
@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId"), Index("normalizedName")]
)
@Serializable
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipeId: Long,
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val unit: MeasurementUnit,
    val notes: String? = null,
    val sortOrder: Int = 0
) {
    companion object {
        fun normalize(name: String): String = name.trim().lowercase()
    }
}
