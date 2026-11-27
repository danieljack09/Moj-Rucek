package com.mojrucek.app.ui.recipes

import com.mojrucek.app.ui.components.AppFab
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.domain.model.Readiness
import com.mojrucek.app.ui.components.AppChip
import com.mojrucek.app.ui.components.ChipRow
import com.mojrucek.app.ui.components.ChipSelectionStyle
import com.mojrucek.app.ui.components.EmptyState
import com.mojrucek.app.ui.components.MetaInfo
import com.mojrucek.app.ui.components.PillSearchField
import com.mojrucek.app.ui.components.RecipeCard
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.components.tabListBottomPadding
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onOpenRecipe: (Long) -> Unit,
    onAddRecipe: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // No top bar: the Scaffold's top padding is the status-bar inset, and ScreenHeader sits below it.
    Scaffold(
        floatingActionButton = {
            AppFab(onClick = onAddRecipe, contentDescription = "Нов рецепт")
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ScreenHeader(
                title = "Рецепти",
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
            )

            // The screen has no filter sheet or sort, so the search pill has no filter button beside it.
            PillSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = "Пребарај рецепти",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp)
            )

            // Readiness is a single choice (filled when selected); "Омилени" is an independent toggle
            // on the same row (outlined when selected), exactly as before.
            ChipRow {
                AppChip(
                    "Сите",
                    selected = state.readinessFilter == ReadinessFilter.ALL,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.ALL) },
                    selectedStyle = ChipSelectionStyle.Filled
                )
                AppChip(
                    "Можам да зготвам сега",
                    selected = state.readinessFilter == ReadinessFilter.CAN_COOK_NOW,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.CAN_COOK_NOW) },
                    selectedStyle = ChipSelectionStyle.Filled
                )
                AppChip(
                    "Речиси подготвено",
                    selected = state.readinessFilter == ReadinessFilter.ALMOST_READY,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.ALMOST_READY) },
                    selectedStyle = ChipSelectionStyle.Filled
                )
                AppChip(
                    "Треба да купам намирници",
                    selected = state.readinessFilter == ReadinessFilter.NEEDS_SHOPPING,
                    onClick = { viewModel.setReadinessFilter(ReadinessFilter.NEEDS_SHOPPING) },
                    selectedStyle = ChipSelectionStyle.Filled
                )
                AppChip(
                    "Омилени",
                    selected = state.favoritesOnly,
                    onClick = { viewModel.setFavoritesOnly(!state.favoritesOnly) },
                    selectedStyle = ChipSelectionStyle.Outlined
                )
            }

            // Time is a single choice.
            ChipRow {
                AppChip(
                    "Секое време",
                    selected = state.maxPrepPlusCookMinutes == null,
                    onClick = { viewModel.setMaxTime(null) },
                    selectedStyle = ChipSelectionStyle.Filled
                )
                AppChip(
                    "До 30 мин",
                    selected = state.maxPrepPlusCookMinutes == 30,
                    onClick = { viewModel.setMaxTime(30) },
                    selectedStyle = ChipSelectionStyle.Filled,
                    leadingIcon = Icons.Outlined.Schedule
                )
                AppChip(
                    "До 60 мин",
                    selected = state.maxPrepPlusCookMinutes == 60,
                    onClick = { viewModel.setMaxTime(60) },
                    selectedStyle = ChipSelectionStyle.Filled,
                    leadingIcon = Icons.Outlined.Schedule
                )
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "Нема рецепти што одговараат на филтрите.",
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = tabListBottomPadding(padding))
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Spacing.screenHorizontal,
                        top = 8.dp,
                        end = Spacing.screenHorizontal,
                        bottom = tabListBottomPadding(padding, withFab = true)
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
                    verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
                ) {
                    items(state.items, key = { it.match.recipeId }) { item ->
                        RecipeGridCard(
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

/**
 * Grid-size [RecipeCard]. The time pill sits on the image well (only when the recipe has a time).
 * Under the title: the category, then ingredient coverage in medium weight, coloured by readiness
 * as before (can cook now = accent, almost ready = darkOlive, needs shopping = textSecondary).
 */
@Composable
private fun RecipeGridCard(item: RecipeListItem, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val match = item.match
    val readinessColor: Color = when (match.readiness) {
        Readiness.CAN_COOK_NOW -> MaterialTheme.colorScheme.primary
        Readiness.ALMOST_READY -> MojRucekTheme.colors.darkOlive
        Readiness.NEEDS_SHOPPING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    RecipeCard(
        title = match.recipeName,
        // Shown when it is a bundled asset (see RecipeCover); otherwise the category icon.
        imageUri = item.imageUri,
        category = item.category,
        onClick = onClick,
        meta = if (item.totalTimeMinutes > 0) listOf(MetaInfo(Icons.Outlined.Schedule, "${item.totalTimeMinutes} мин")) else emptyList(),
        isFavorite = match.isFavorite,
        onToggleFavorite = onToggleFavorite,
        favoriteContentDescription = "Омилено"
    ) {
        Text(
            item.category?.displayName ?: "Без категорија",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "Покриени: ${match.coveredIngredients}/${match.totalIngredients}",
            style = MaterialTheme.typography.labelMedium,
            color = readinessColor,
            maxLines = 1
        )
    }
}
