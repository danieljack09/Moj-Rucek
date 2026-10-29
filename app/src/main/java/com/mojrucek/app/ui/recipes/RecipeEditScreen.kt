package com.mojrucek.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.domain.unit.MeasurementUnit

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Нов рецепт" else "Уреди рецепт") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Име на рецептот") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Опис (опционално)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.servingsText,
                        onValueChange = viewModel::setServingsText,
                        label = { Text("Порции") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.prepTimeText,
                        onValueChange = viewModel::setPrepTimeText,
                        label = { Text("Подготовка (мин)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.cookTimeText,
                        onValueChange = viewModel::setCookTimeText,
                        label = { Text("Готвење (мин)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = state.category?.displayName ?: "Без категорија",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категорија") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Без категорија") },
                            onClick = { viewModel.setCategory(null); expanded = false }
                        )
                        RecipeCategory.entries.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(c.displayName) },
                                onClick = { viewModel.setCategory(c); expanded = false }
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.tagsText,
                    onValueChange = viewModel::setTagsText,
                    label = { Text("Ознаки, одделени со запирка (опционално)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item { Text("Намирници", style = MaterialTheme.typography.titleMedium) }
            items(state.ingredients, key = { it.id }) { line ->
                IngredientLineEditor(
                    line = line,
                    onChange = { updated -> viewModel.updateIngredientLine(line.id) { updated } },
                    onRemove = { viewModel.removeIngredientLine(line.id) }
                )
            }
            item {
                OutlinedButton(onClick = viewModel::addIngredientLine, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Додади намирница")
                }
            }

            item { Text("Чекори на подготовка", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(state.steps) { index, step ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { viewModel.setStep(index, it) },
                        label = { Text("Чекор ${index + 1}") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeStep(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Избриши чекор")
                    }
                }
            }
            item {
                OutlinedButton(onClick = viewModel::addStep, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Додади чекор")
                }
            }

            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    label = { Text("Белешки (опционално)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = viewModel::save,
                    enabled = state.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Зачувај рецепт") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientLineEditor(
    line: IngredientLineInput,
    onChange: (IngredientLineInput) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { onChange(line.copy(name = it)) },
                    label = { Text("Намирница") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Избриши намирница")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.quantityText,
                    onValueChange = { onChange(line.copy(quantityText = it)) },
                    label = { Text("Количина") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = line.unit.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Единица") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MeasurementUnit.entries.forEach { u ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(u.displayName) },
                                onClick = { onChange(line.copy(unit = u)); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
