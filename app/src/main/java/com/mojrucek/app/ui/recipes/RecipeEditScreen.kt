package com.mojrucek.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.ui.components.CircleIconButton
import com.mojrucek.app.ui.components.OutlinePillButton
import com.mojrucek.app.ui.components.PillDropdownField
import com.mojrucek.app.ui.components.PillTextField
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.Spacing

/** "Без категорија" first, then every category: the same options as before. */
private val categoryOptions: List<RecipeCategory?> = listOf<RecipeCategory?>(null) + RecipeCategory.entries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    viewModel: RecipeEditViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.savedRecipeId) {
        state.savedRecipeId?.let { onSaved(it) }
    }

    // Error borders mirror RecipeEditUiState.isValid: servings must be a whole number above 0.
    // (The name is not flagged: a new recipe starts with it empty.)
    val servingsError = state.servingsText.toIntOrNull()?.let { it > 0 } != true

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                // Keeps the focused field above the keyboard (the window is edge-to-edge).
                .imePadding()
        ) {
            ScreenHeader(
                title = if (state.isNew) "Нов рецепт" else "Уреди рецепт",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.screenHorizontal,
                    top = 8.dp,
                    end = Spacing.screenHorizontal,
                    bottom = Spacing.betweenSections
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PillTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = "Име на рецептот"
                    )
                }
                item {
                    PillTextField(
                        value = state.description,
                        onValueChange = viewModel::setDescription,
                        label = "Опис (опционално)",
                        singleLine = false,
                        minLines = 2
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillTextField(
                            value = state.servingsText,
                            onValueChange = viewModel::setServingsText,
                            label = "Порции",
                            isError = servingsError,
                            modifier = Modifier.weight(1f)
                        )
                        PillDropdownField(
                            label = "Категорија",
                            selectedText = state.category?.displayName ?: "Без категорија",
                            options = categoryOptions,
                            optionLabel = { it?.displayName ?: "Без категорија" },
                            isSelected = { it == state.category },
                            onSelect = viewModel::setCategory,
                            modifier = Modifier.weight(1.6f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillTextField(
                            value = state.prepTimeText,
                            onValueChange = viewModel::setPrepTimeText,
                            label = "Подготовка (мин)",
                            modifier = Modifier.weight(1f)
                        )
                        PillTextField(
                            value = state.cookTimeText,
                            onValueChange = viewModel::setCookTimeText,
                            label = "Готвење (мин)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    PillTextField(
                        value = state.tagsText,
                        onValueChange = viewModel::setTagsText,
                        label = "Ознаки, одделени со запирка (опционално)"
                    )
                }

                item { SectionTitle("Намирници") }
                items(state.ingredients, key = { it.id }) { line ->
                    IngredientLineEditor(
                        line = line,
                        onChange = { updated -> viewModel.updateIngredientLine(line.id) { updated } },
                        onRemove = { viewModel.removeIngredientLine(line.id) }
                    )
                }
                item {
                    // Label kept exactly as before ("+ ..."), so no plus icon on top of it.
                    OutlinePillButton(
                        "+ Додади намирница",
                        onClick = viewModel::addIngredientLine,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item { SectionTitle("Чекори на подготовка") }
                itemsIndexed(state.steps) { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillTextField(
                            value = step,
                            onValueChange = { viewModel.setStep(index, it) },
                            label = "Чекор ${index + 1}",
                            singleLine = false,
                            modifier = Modifier.weight(1f)
                        )
                        CircleIconButton(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Избриши чекор",
                            onClick = { viewModel.removeStep(index) }
                        )
                    }
                }
                item {
                    OutlinePillButton(
                        "+ Додади чекор",
                        onClick = viewModel::addStep,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(Modifier.height(Spacing.betweenSections - 12.dp))
                    PillTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        label = "Белешки (опционално)",
                        singleLine = false,
                        minLines = 2
                    )
                }

                item {
                    Spacer(Modifier.height(Spacing.betweenSections - 12.dp))
                    // Disabled (accentSoft) exactly when it was disabled before: while the form is invalid.
                    PrimaryPillButton(
                        "Зачувај рецепт",
                        onClick = viewModel::save,
                        enabled = state.isValid,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Spacing.betweenSections - 12.dp).semantics { heading() }
    )
}

/**
 * One ingredient as a greenContainer rounded block: name and a trailing remove button on the
 * first line, quantity and unit side by side on the second. The fields use the background
 * colour so they stand out from the block. A quantity that is not a number gets the error style
 * (the same toDoubleOrNull check the form's validation uses).
 */
@Composable
private fun IngredientLineEditor(
    line: IngredientLineInput,
    onChange: (IngredientLineInput) -> Unit,
    onRemove: () -> Unit
) {
    val fieldColor = MaterialTheme.colorScheme.background
    val quantityError = line.quantityText.isNotBlank() && line.quantityText.toDoubleOrNull() == null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MojRucekTheme.colors.greenContainer)
            .padding(start = Spacing.rowPadding, top = Spacing.rowPadding, end = 4.dp, bottom = Spacing.rowPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillTextField(
                value = line.name,
                onValueChange = { onChange(line.copy(name = it)) },
                label = "Намирница",
                containerColor = fieldColor,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Избриши намирница",
                onClick = onRemove,
                containerColor = fieldColor
            )
        }
        // Full width (not stopping short of the remove button) so "Количина" fits on narrow screens.
        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillTextField(
                value = line.quantityText,
                onValueChange = { onChange(line.copy(quantityText = it)) },
                label = "Количина",
                isError = quantityError,
                containerColor = fieldColor,
                modifier = Modifier.weight(1f)
            )
            PillDropdownField(
                label = "Единица",
                selectedText = line.unit.displayName,
                options = MeasurementUnit.entries,
                optionLabel = { it.displayName },
                isSelected = { it == line.unit },
                onSelect = { onChange(line.copy(unit = it)) },
                containerColor = fieldColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
