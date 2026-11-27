package com.mojrucek.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.ui.auth.AccountCard
import com.mojrucek.app.ui.auth.AccountViewModel
import com.mojrucek.app.ui.auth.DeleteAccountSection
import com.mojrucek.app.ui.auth.GuestExpiredDialog
import com.mojrucek.app.ui.sync.SyncCard
import com.mojrucek.app.ui.sync.SyncConflictDialog
import com.mojrucek.app.ui.auth.guestSettingsLine
import com.mojrucek.app.ui.components.FloatingPillNavBar
import com.mojrucek.app.ui.components.GenericViewModelFactory
import com.mojrucek.app.ui.components.PillNavItem
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

private const val NAV_FADE_MILLIS = 200

private val pillNavItems = bottomNavItems.map { PillNavItem(it.dest.route, it.label, it.icon) }

@Composable
fun MojRucekNavHost(container: AppContainer) {
    val navController = rememberNavController()
    var showLowStockOnly by remember { mutableStateOf(false) }
    var showNextWeekInPlanner by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.dest.route == currentRoute }

    GuestExpiredDialog(container, onOpenSettings = { navController.navigate(Dest.Settings.route) })
    SyncConflictDialog(container)

    Scaffold(
        // Edge-to-edge: the outer Scaffold reserves no space. Screen content draws behind the
        // floating nav bar (each tab list ends with Spacing.navBarClearance), and each screen's
        // own Scaffold/TopAppBar handles the status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                FloatingPillNavBar(
                    items = pillNavItems,
                    selectedRoute = currentRoute,
                    onSelect = { item ->
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(NAV_FADE_MILLIS)) },
            exitTransition = { fadeOut(tween(NAV_FADE_MILLIS)) },
            popEnterTransition = { fadeIn(tween(NAV_FADE_MILLIS)) },
            popExitTransition = { fadeOut(tween(NAV_FADE_MILLIS)) }
        ) {
            composable(Dest.Home.route) {
                val vm: HomeViewModel = viewModel(factory = GenericViewModelFactory {
                    HomeViewModel(container.recipeRepository, container.ingredientRepository, container.plannerRepository, container.settingsRepository, container.shoppingListRepository)
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
                    onOpenLowStock = {
                        showLowStockOnly = true
                        navController.navigate(Dest.Ingredients.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenShopping = {
                        navController.navigate(Dest.Shopping.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenNextWeekPlanner = {
                        showNextWeekInPlanner = true
                        navController.navigate(Dest.Planner.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Dest.Ingredients.route) {
                val vm: IngredientsViewModel = viewModel(factory = GenericViewModelFactory {
                    IngredientsViewModel(container.ingredientRepository, container.recipeRepository, container.catalogSyncRepository)
                })
                // One-shot: set when arriving from the Home "Намирници при крај" card.
                LaunchedEffect(showLowStockOnly) {
                    if (showLowStockOnly) {
                        vm.setLowStockOnly(true)
                        showLowStockOnly = false
                    }
                }
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
                    PlannerViewModel(
                        container.plannerRepository, container.recipeRepository, container.ingredientRepository, container.settingsRepository,
                        container.listPermission, { container.authRepository.currentUid }
                    )
                })
                // One-shot: set when arriving from the Home "Неделен потсетник" card.
                // Reuses the Planner's own week navigation: this week, then +1 = upcoming week.
                LaunchedEffect(showNextWeekInPlanner) {
                    if (showNextWeekInPlanner) {
                        vm.goToThisWeek()
                        vm.goToNextWeek()
                        showNextWeekInPlanner = false
                    }
                }
                PlannerScreen(
                    viewModel = vm,
                    onOpenRecipe = { id -> navController.navigate(Dest.RecipeDetail.path(id)) },
                    onOpenSettings = { navController.navigate(Dest.Settings.route) }
                )
            }

            composable(Dest.Shopping.route) {
                val vm: ShoppingViewModel = viewModel(factory = GenericViewModelFactory {
                    ShoppingViewModel(container.shoppingListRepository, container.recipeRepository)
                })
                ShoppingScreen(vm)
            }

            composable(Dest.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = GenericViewModelFactory {
                    SettingsViewModel(
                        navController.context.applicationContext as android.app.Application,
                        container.settingsRepository,
                        container.backupRepository,
                        container.catalogSyncRepository
                    )
                })
                val accountVm: AccountViewModel = viewModel(factory = GenericViewModelFactory {
                    AccountViewModel(container.authRepository, container.accountSwitchRepository)
                })
                SettingsScreen(vm, onBack = { navController.popBackStack() }, accountCard = {
                    AccountCard(accountVm, guestLine = guestSettingsLine(container))
                    SyncCard(container)
                    DeleteAccountSection(container)
                })
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
                    RecipeDetailViewModel(recipeId, container.recipeRepository, container.ingredientRepository, container.shoppingListRepository, container.unlockedDetailsRepository)
                })
                RecipeDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Dest.RecipeEdit.path(id)) },
                    onDuplicated = { id ->
                        navController.navigate(Dest.RecipeDetail.path(id)) {
                            popUpTo(Dest.Recipes.route)
                        }
                    },
                    // Same as tapping the Shopping tab, so the recipe is still there when you return to Recipes.
                    onOpenShopping = {
                        navController.navigate(Dest.Shopping.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
