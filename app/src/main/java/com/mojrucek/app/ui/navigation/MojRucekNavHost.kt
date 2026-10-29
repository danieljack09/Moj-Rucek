package com.mojrucek.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.ui.components.GenericViewModelFactory
import com.mojrucek.app.ui.home.HomeScreen
import com.mojrucek.app.ui.home.HomeViewModel
import com.mojrucek.app.ui.ingredients.IngredientsScreen
import com.mojrucek.app.ui.ingredients.IngredientsViewModel
import com.mojrucek.app.ui.planner.PlannerScreen
import com.mojrucek.app.ui.planner.PlannerViewModel
import com.mojrucek.app.ui.recipes.RecipeDetailScreen
import com.mojrucek.app.ui.recipes.RecipeDetailViewModel
import com.mojrucek.app.ui.recipes.RecipeEditScreen
import com.mojrucek.app.ui.recipes.RecipeEditViewModel
import com.mojrucek.app.ui.recipes.RecipesScreen
import com.mojrucek.app.ui.recipes.RecipesViewModel
import com.mojrucek.app.ui.settings.SettingsScreen
import com.mojrucek.app.ui.settings.SettingsViewModel
import com.mojrucek.app.ui.shopping.ShoppingScreen
import com.mojrucek.app.ui.shopping.ShoppingViewModel

@Composable
fun MojRucekNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.dest.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    NavigationBar(containerColor = Color.Transparent) {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.dest.route,
                                onClick = {
                                    navController.navigate(item.dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Dest.Home.route) {
                val vm: HomeViewModel = viewModel(factory = GenericViewModelFactory {
                    HomeViewModel(container.recipeRepository, container.ingredientRepository, container.plannerRepository, container.settingsRepository)
                })
                HomeScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(Dest.Settings.route) },
                    onOpenRecipe = { id -> navController.navigate(Dest.RecipeDetail.path(id)) },
                    onOpenPlanner = {
                        navController.navigate(Dest.Planner.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onQuickAddIngredient = {
                        navController.navigate(Dest.Ingredients.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onQuickAddShoppingItem = {
                        navController.navigate(Dest.Shopping.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Dest.Ingredients.route) {
                val vm: IngredientsViewModel = viewModel(factory = GenericViewModelFactory {
                    IngredientsViewModel(container.ingredientRepository)
                })
                IngredientsScreen(vm)
            }

            composable(Dest.Recipes.route) {
                val vm: RecipesViewModel = viewModel(factory = GenericViewModelFactory {
                    RecipesViewModel(container.recipeRepository, container.ingredientRepository)
                })
                RecipesScreen(
                    viewModel = vm,
                    onOpenRecipe = { id -> navController.navigate(Dest.RecipeDetail.path(id)) },
                    onAddRecipe = { navController.navigate(Dest.RecipeNew.route) }
                )
            }

            composable(Dest.Planner.route) {
                val vm: PlannerViewModel = viewModel(factory = GenericViewModelFactory {
                    PlannerViewModel(container.plannerRepository, container.recipeRepository, container.ingredientRepository, container.settingsRepository)
                })
                PlannerScreen(
                    viewModel = vm,
                    onOpenRecipe = { id -> navController.navigate(Dest.RecipeDetail.path(id)) }
                )
            }

            composable(Dest.Shopping.route) {
                val vm: ShoppingViewModel = viewModel(factory = GenericViewModelFactory {
                    ShoppingViewModel(container.shoppingListRepository)
                })
                ShoppingScreen(vm)
            }

            composable(Dest.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = GenericViewModelFactory {
                    SettingsViewModel(
                        navController.context.applicationContext as android.app.Application,
                        container.settingsRepository,
                        container.backupRepository
                    )
                })
                SettingsScreen(vm, onBack = { navController.popBackStack() })
            }

            composable(Dest.RecipeNew.route) {
                val vm: RecipeEditViewModel = viewModel(factory = GenericViewModelFactory {
                    RecipeEditViewModel(null, container.recipeRepository)
                })
                RecipeEditScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Dest.RecipeDetail.path(id)) {
                            popUpTo(Dest.Recipes.route)
                        }
                    }
                )
            }

            composable(
                route = Dest.RecipeEdit.route,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: 0L
                val vm: RecipeEditViewModel = viewModel(factory = GenericViewModelFactory {
                    RecipeEditViewModel(recipeId, container.recipeRepository)
                })
                RecipeEditScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Dest.RecipeDetail.route,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: 0L
                val vm: RecipeDetailViewModel = viewModel(factory = GenericViewModelFactory {
                    RecipeDetailViewModel(recipeId, container.recipeRepository, container.ingredientRepository, container.shoppingListRepository)
                })
                RecipeDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Dest.RecipeEdit.path(id)) },
                    onDuplicated = { id ->
                        navController.navigate(Dest.RecipeDetail.path(id)) {
                            popUpTo(Dest.Recipes.route)
                        }
                    }
                )
            }
        }
    }
}
