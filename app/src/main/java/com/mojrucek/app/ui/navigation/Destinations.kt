package com.mojrucek.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.ShoppingBasket
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
    BottomNavItem(Dest.Home, "Почетна", Icons.Outlined.Home),
    BottomNavItem(Dest.Ingredients, "Намирници", Icons.Outlined.Kitchen),
    BottomNavItem(Dest.Recipes, "Рецепти", Icons.AutoMirrored.Outlined.MenuBook),
    BottomNavItem(Dest.Planner, "Планер", Icons.Outlined.CalendarMonth),
    BottomNavItem(Dest.Shopping, "Пазарење", Icons.Outlined.ShoppingBasket)
)
