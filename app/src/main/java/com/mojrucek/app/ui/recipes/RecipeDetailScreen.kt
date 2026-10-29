package com.mojrucek.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.domain.model.MatchStatus
import com.mojrucek.app.ui.components.formatQuantityWithUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicated: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCookConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastAddedToShoppingList) {
        if (state.lastAddedToShoppingList) {
            snackbarHostState.showSnackbar("Недостасувачките намирници се додадени во листата за пазарење.")
            viewModel.clearAddedFlag()
        }
    }
    LaunchedEffect(state.lastCooked) {
        if (state.lastCooked) {
            snackbarHostState.showSnackbar("Намирниците се одземени од инвентарот.")
            viewModel.clearCookedFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.recipe?.recipe?.name ?: "Рецепт") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    if (state.recipe != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (state.recipe!!.recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Омилено"
                            )
                        }
                        IconButton(onClick = { onEdit(state.recipe!!.recipe.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Уреди")
                        }
                        IconButton(onClick = { viewModel.duplicate(onDuplicated) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Копирај")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Избриши")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.notFound || state.recipe == null || state.match == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Рецептот не постои.")
            }
            return@Scaffold
        }

        val recipe = state.recipe!!.recipe
        val match = state.match!!

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                if (!recipe.description.isNullOrBlank()) {
                    Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer()
                }
                Text(
                    "Подготовка: ${recipe.prepTimeMinutes} мин · Готвење: ${recipe.cookTimeMinutes} мин",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer()

                ServingsStepper(
                    servings = state.desiredServings,
                    onChange = viewModel::setDesiredServings
                )
                Spacer()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Покриени намирници: ${match.coveredIngredients} од ${match.totalIngredients}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (match.missing.isNotEmpty()) {
                            Spacer()
                            Text("Недостигаат:", style = MaterialTheme.typography.labelLarge)
                            match.missing.forEach { m ->
                                val text = when (m.status) {
                                    MatchStatus.MISSING -> "${m.name} — ${formatQuantityWithUnit(m.requiredQuantity, m.requiredUnit)}"
                                    MatchStatus.INSUFFICIENT -> "${m.name} — уште " +
                                        formatQuantityWithUnit(m.requiredQuantity - m.availableQuantity, m.requiredUnit)
                                    MatchStatus.UNIT_MISMATCH -> "${m.name} — единицата во инвентарот не одговара"
                                    MatchStatus.AVAILABLE -> ""
                                }
                                if (text.isNotBlank()) Text("• $text", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer()
                            OutlinedButton(onClick = viewModel::addMissingToShoppingList, modifier = Modifier.fillMaxWidth()) {
                                Text("Додади ги недостасувачките во листа за пазарење")
                            }
                        }
                    }
                }
                Spacer()

                Button(onClick = { showCookConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Готвев го ова")
                }
                Spacer()

                Text("Потребни намирници", style = MaterialTheme.typography.titleMedium)
            }

            items(match.matches) { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        formatQuantityWithUnit(m.requiredQuantity, m.requiredUnit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (m.status == MatchStatus.AVAILABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                Spacer()
                Text("Чекори", style = MaterialTheme.typography.titleMedium)
                recipe.instructionSteps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                }
                if (!recipe.notes.isNullOrBlank()) {
                    Spacer()
                    Text("Белешки", style = MaterialTheme.typography.titleMedium)
                    Text(recipe.notes, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer()
            }
        }
    }

    if (showCookConfirm && state.recipe != null && state.match != null) {
        AlertDialog(
            onDismissRequest = { showCookConfirm = false },
            title = { Text("Да ги одземам намирниците?") },
            text = {
                Column {
                    Text("Ќе се одземат следниве количини од инвентарот (за ${state.desiredServings} порции):")
                    Spacer()
                    state.match!!.matches.forEach { m ->
                        Text("• ${m.name}: ${formatQuantityWithUnit(m.requiredQuantity, m.requiredUnit)}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmCooked()
                    showCookConfirm = false
                }) { Text("Потврди") }
            },
            dismissButton = {
                TextButton(onClick = { showCookConfirm = false }) { Text("Откажи") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Избриши рецепт?") },
            text = { Text("Ова не може да се врати.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(onBack)
                    showDeleteConfirm = false
                }) { Text("Избриши") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Откажи") }
            }
        )
    }
}

@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Порции:", style = MaterialTheme.typography.titleMedium)
        Spacer()
        IconButton(onClick = { onChange((servings - 1).coerceAtLeast(1)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Намали порции")
        }
        Text(servings.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(servings + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Зголеми порции")
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
}
