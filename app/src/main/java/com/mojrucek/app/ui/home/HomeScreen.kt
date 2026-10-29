package com.mojrucek.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.model.RecipeMatchResult
import com.mojrucek.app.domain.model.Readiness

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenPlanner: () -> Unit,
    onQuickAddIngredient: () -> Unit,
    onQuickAddShoppingItem: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showRecipePicker by remember { mutableStateOf(false) }
    var pickerRecipes by remember { mutableStateOf<List<RecipeWithIngredients>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мој Ручек") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Поставки")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Што ќе готвиме денес?",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                TodayLunchCard(
                    today = state.today,
                    onChangeClick = {
                        viewModel.recipesForPicker { pickerRecipes = it }
                        showRecipePicker = true
                    },
                    onOpenRecipe = onOpenRecipe
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onQuickAddIngredient, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.height(0.dp))
                        Text("Намирница", maxLines = 1)
                    }
                    OutlinedButton(onClick = onQuickAddShoppingItem, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.height(18.dp))
                        Text("За пазарење", maxLines = 1)
                    }
                }
            }

            item { InventorySummaryCard(state = state) }

            item {
                NextWeekCard(
                    plannedCount = state.nextWeekPlanned,
                    shoppingGenerated = state.nextWeekShoppingGenerated,
                    onOpenPlanner = onOpenPlanner
                )
            }

            if (state.suggestedRecipes.isNotEmpty()) {
                item {
                    Text(
                        "Предложени рецепти",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.suggestedRecipes, key = { it.recipeId }) { match ->
                    SuggestedRecipeCard(match = match, onClick = { onOpenRecipe(match.recipeId) })
                }
            }
        }
    }

    if (showRecipePicker) {
        RecipePickerDialog(
            recipes = pickerRecipes,
            onDismiss = { showRecipePicker = false },
            onPick = { recipeId ->
                viewModel.setTodayMeal(recipeId)
                showRecipePicker = false
            },
            onClear = {
                viewModel.setTodayMeal(null)
                showRecipePicker = false
            }
        )
    }
}

@Composable
private fun TodayLunchCard(today: TodayMealUi?, onChangeClick: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🍽️ Денешен ручек", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (today == null) {
                Text("Нема избрано јадење за денес.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(today.recipeName, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChangeClick) {
                    Text(if (today == null) "Избери јадење" else "Промени")
                }
                if (today != null) {
                    TextButton(onClick = { onOpenRecipe(today.recipeId) }) {
                        Text("Отвори рецепт")
                    }
                }
            }
        }
    }
}

@Composable
private fun InventorySummaryCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Намирници дома", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("${state.ingredientCount} ставки во инвентарот", style = MaterialTheme.typography.bodyMedium)
            if (state.lowStockIngredients.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.height(18.dp)
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "При крај: " + state.lowStockIngredients.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun NextWeekCard(plannedCount: Int, shoppingGenerated: Boolean, onOpenPlanner: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Следна недела", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "$plannedCount од 7 дена испланирани · " +
                    if (shoppingGenerated) "листата за пазарење е подготвена" else "листата за пазарење сè уште не е генерирана",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenPlanner) { Text("Отвори планер") }
        }
    }
}

@Composable
private fun SuggestedRecipeCard(match: RecipeMatchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(match.recipeName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (match.readiness) {
                        Readiness.CAN_COOK_NOW -> "Можам да зготвам сега"
                        Readiness.ALMOST_READY -> "Речиси подготвено"
                        Readiness.NEEDS_SHOPPING -> "Треба да купам"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when (match.readiness) {
                        Readiness.CAN_COOK_NOW -> MaterialTheme.colorScheme.primary
                        Readiness.ALMOST_READY -> MaterialTheme.colorScheme.secondary
                        Readiness.NEEDS_SHOPPING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (match.coveragePercent / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Покриени намирници: ${match.coveredIngredients} од ${match.totalIngredients}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RecipePickerDialog(
    recipes: List<RecipeWithIngredients>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Избери рецепт за денес") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(recipes, key = { it.recipe.id }) { r ->
                    TextButton(onClick = { onPick(r.recipe.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(r.recipe.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Затвори") }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("Испразни ден") }
        }
    )
}
