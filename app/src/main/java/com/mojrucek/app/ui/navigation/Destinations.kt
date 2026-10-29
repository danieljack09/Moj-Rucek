package com.mojrucek.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Ingredients : Dest("ingredients")
    data object Recipes : Dest("recipes")
    data object Planner : Dest("planner")
    data object Shopping : Dest("shopping")
    data object Settings : Dest("settings")

    data object RecipeDetail : Dest("recipe/{recipeId}") {
        fun path(recipeId: Long) = "recipe/$recipeId"
    }

    /** Create a brand-new recipe. */
    data object RecipeNew : Dest("recipe-edit-new")

    /** Edit an existing recipe by id. */
    data object RecipeEdit : Dest("recipe-edit/{recipeId}") {
        fun path(recipeId: Long) = "recipe-edit/$recipeId"
    }
}

data class BottomNavItem(val dest: Dest, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Dest.Home, "Почетна", Icons.Filled.Home),
    BottomNavItem(Dest.Ingredients, "Намирници", Icons.Filled.Kitchen),
    BottomNavItem(Dest.Recipes, "Рецепти", Icons.Filled.MenuBook),
    BottomNavItem(Dest.Planner, "Планер", Icons.Filled.CalendarMonth),
    BottomNavItem(Dest.Shopping, "Пазарење", Icons.Filled.ShoppingCart)
)
