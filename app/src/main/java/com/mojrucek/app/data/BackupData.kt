package com.mojrucek.app.data

import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.data.entity.WeeklyPlan
import kotlinx.serialization.Serializable

/** The full on-disk shape of a "Мој Ручек" JSON export (spec §12). */
@Serializable
data class BackupData(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAtEpochMillis: Long,
    val ingredients: List<Ingredient> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val recipeIngredients: List<RecipeIngredient> = emptyList(),
    val shoppingListItems: List<ShoppingListItem> = emptyList(),
    val weeklyPlans: List<WeeklyPlan> = emptyList(),
    val plannedMeals: List<PlannedMeal> = emptyList(),
    val settings: AppSettings = AppSettings()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

data class BackupSummary(
    val ingredientCount: Int,
    val recipeCount: Int,
    val shoppingItemCount: Int,
    val weeklyPlanCount: Int
)

fun BackupData.summary() = BackupSummary(
    ingredientCount = ingredients.size,
    recipeCount = recipes.size,
    shoppingItemCount = shoppingListItems.size,
    weeklyPlanCount = weeklyPlans.size
)
