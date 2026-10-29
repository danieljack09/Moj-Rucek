package com.mojrucek.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.domain.model.Readiness
import com.mojrucek.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onOpenRecipe: (Long) -> Unit,
    onAddRecipe: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Рецепти") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Filled.Add, contentDescription = "Нов рецепт")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                label = { Text("Пребарај рецепти") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.readinessFilter == ReadinessFilter.ALL,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.ALL) },
                    label = { Text("Сите") }
                )
                FilterChip(
                    selected = state.readinessFilter == ReadinessFilter.CAN_COOK_NOW,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.CAN_COOK_NOW) },
                    label = { Text("Можам да зготвам сега") }
                )
                FilterChip(
                    selected = state.readinessFilter == ReadinessFilter.ALMOST_READY,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.ALMOST_READY) },
                    label = { Text("Речиси подготвено") }
                )
                FilterChip(
                    selected = state.readinessFilter == ReadinessFilter.NEEDS_SHOPPING,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.NEEDS_SHOPPING) },
                    label = { Text("Треба да купам намирници") }
                )
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { viewModel.setFavoritesOnly(!state.favoritesOnly) },
                    label = { Text("Омилени") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.maxPrepPlusCookMinutes == null,
                    onClick = { viewModel.setMaxTime(null) },
                    label = { Text("Секое време") }
                )
                FilterChip(
                    selected = state.maxPrepPlusCookMinutes == 30,
                    onClick = { viewModel.setMaxTime(30) },
                    label = { Text("До 30 мин") }
                )
                FilterChip(
                    selected = state.maxPrepPlusCookMinutes == 60,
                    onClick = { viewModel.setMaxTime(60) },
                    label = { Text("До 60 мин") }
                )
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    message = "Нема рецепти што одговараат на филтрите.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.match.recipeId }) { item ->
                        RecipeCard(
                            item = item,
                            onClick = { onOpenRecipe(item.match.recipeId) },
                            onToggleFavorite = { viewModel.toggleFavorite(item.match.recipeId, !item.match.isFavorite) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(item: RecipeListItem, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val match = item.match
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(match.recipeName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(item.category?.displayName ?: "Без категорија")
                        if (item.totalTimeMinutes > 0) append(" · ${item.totalTimeMinutes} мин")
                        append(" · Покриени: ${match.coveredIngredients}/${match.totalIngredients}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (match.readiness) {
                        Readiness.CAN_COOK_NOW -> MaterialTheme.colorScheme.primary
                        Readiness.ALMOST_READY -> MaterialTheme.colorScheme.secondary
                        Readiness.NEEDS_SHOPPING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (match.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Омилено",
                    tint = if (match.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
