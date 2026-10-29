package com.mojrucek.app.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(viewModel: PlannerViewModel, onOpenRecipe: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var dayBeingEdited by remember { mutableStateOf<Int?>(null) }
    var pickerRecipes by remember { mutableStateOf<List<RecipeWithIngredients>>(emptyList()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Планер") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WeekHeader(
                weekStart = state.weekStart,
                onPrev = viewModel::goToPreviousWeek,
                onNext = viewModel::goToNextWeek,
                onToday = viewModel::goToThisWeek
            )

            HouseholdServingsRow(
                servings = state.householdServings,
                onChange = viewModel::setHouseholdServings
            )

            if (state.incompleteDays.isNotEmpty()) {
                Text(
                    "Сè уште нема јадење за: " + state.incompleteDays.joinToString(", ") { dayName(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.days, key = { it.dayOfWeek }) { day ->
                    DayCard(
                        day = day,
                        onPick = {
                            viewModel.recipesForPicker { pickerRecipes = it }
                            dayBeingEdited = day.dayOfWeek
                        },
                        onOpenRecipe = { day.recipeId?.let(onOpenRecipe) }
                    )
                }
            }

            Button(
                onClick = viewModel::generateShoppingList,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(if (state.shoppingListGenerated) "Регенерирај листа за пазарење" else "Генерирај листа за пазарење")
            }
        }
    }

    dayBeingEdited?.let { day ->
        DayRecipePickerDialog(
            recipes = pickerRecipes,
            currentDays = state.days,
            onDismiss = { dayBeingEdited = null },
            onPick = { recipeId -> viewModel.setMeal(day, recipeId); dayBeingEdited = null },
            onClear = { viewModel.setMeal(day, null); dayBeingEdited = null },
            onCopyFrom = { fromDay -> viewModel.copyMeal(fromDay, day); dayBeingEdited = null }
        )
    }

    state.lastGenerationResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearGenerationResult,
            title = { Text("Листата за пазарење е подготвена") },
            text = {
                Column {
                    Text("Додадени се ${result.items.size} ставки.")
                    if (result.conflicts.isNotEmpty()) {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                        Text(
                            "Внимание — овие намирници имаат несовпаѓање на единици и мора рачно да се проверат:",
                            color = MaterialTheme.colorScheme.error
                        )
                        result.conflicts.forEach { c ->
                            Text("• ${c.name}: " + c.conflictingUnits.joinToString(", ") { it.displayName })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearGenerationResult) { Text("Во ред") }
            }
        )
    }
}

@Composable
private fun WeekHeader(weekStart: java.time.LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Претходна недела") }
        TextButton(onClick = onToday) {
            Text("${weekStart.format(formatter)} — ${weekStart.plusDays(6).format(formatter)}")
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Следна недела") }
    }
}

@Composable
private fun HouseholdServingsRow(servings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Порции за домаќинството:", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        IconButton(onClick = { onChange((servings - 1).coerceAtLeast(1)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Намали")
        }
        Text(servings.toString())
        IconButton(onClick = { onChange(servings + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Зголеми")
        }
    }
}

@Composable
private fun DayCard(day: DayPlanUi, onPick: () -> Unit, onOpenRecipe: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM") }
    Card(modifier = Modifier.fillMaxWidth(), onClick = if (day.recipeId != null) onOpenRecipe else onPick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${dayName(day.dayOfWeek)} · ${day.date.format(formatter)}", style = MaterialTheme.typography.titleMedium)
                if (day.recipeName != null) {
                    Text(
                        "${day.recipeName} (${day.servings} порции)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (day.canPrepareNow) "Може да се подготви веднаш" else "Недостигаат ${day.missingCount} намирници",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (day.canPrepareNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Нема избрано јадење", style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onPick) { Text(if (day.recipeId == null) "Избери" else "Промени") }
        }
    }
}

@Composable
private fun DayRecipePickerDialog(
    recipes: List<RecipeWithIngredients>,
    currentDays: List<DayPlanUi>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClear: () -> Unit,
    onCopyFrom: (Int) -> Unit
) {
    val daysWithMeals = currentDays.filter { it.recipeId != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Избери рецепт") },
        text = {
            Column {
                if (daysWithMeals.isNotEmpty()) {
                    Text("Копирај од друг ден:", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        daysWithMeals.forEach { d ->
                            TextButton(onClick = { onCopyFrom(d.dayOfWeek) }) {
                                Text(dayName(d.dayOfWeek).take(3))
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .heightIn(max = 320.dp)
                ) {
                    items(recipes, key = { it.recipe.id }) { r ->
                        TextButton(onClick = { onPick(r.recipe.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(r.recipe.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Затвори") } },
        dismissButton = { TextButton(onClick = onClear) { Text("Испразни ден") } }
    )
}
