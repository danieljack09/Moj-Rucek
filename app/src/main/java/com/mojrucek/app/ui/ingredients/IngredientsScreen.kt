package com.mojrucek.app.ui.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.ui.components.EmptyState
import com.mojrucek.app.ui.components.formatQuantityWithUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(viewModel: IngredientsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val pendingDuplicate by viewModel.pendingDuplicate.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIngredient by remember { mutableStateOf<Ingredient?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Намирници") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Додади намирница")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                label = { Text("Пребарај") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.lowStockOnly,
                    onClick = { viewModel.setLowStockOnly(!state.lowStockOnly) },
                    label = { Text("При крај") }
                )
                FilterChip(
                    selected = state.expiringOnly,
                    onClick = { viewModel.setExpiringOnly(!state.expiringOnly) },
                    label = { Text("Истекува наскоро") }
                )
            }

            if (state.visibleIngredients.isEmpty()) {
                EmptyState(
                    message = if (state.allIngredients.isEmpty())
                        "Нема намирници. Додади ја првата од копчето +."
                    else "Нема намирници што одговараат на филтерот.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.visibleIngredients, key = { it.id }) { ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            onIncrease = { viewModel.adjustQuantity(ingredient, stepFor(ingredient.unit)) },
                            onDecrease = { viewModel.adjustQuantity(ingredient, -stepFor(ingredient.unit)) },
                            onFinish = { viewModel.markFinished(ingredient) },
                            onDelete = { viewModel.deleteIngredient(ingredient) },
                            onClick = { editingIngredient = ingredient }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        IngredientFormDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSubmit = { input ->
                viewModel.submitNewIngredient(input)
                showAddDialog = false
            }
        )
    }

    editingIngredient?.let { ingredient ->
        IngredientFormDialog(
            initial = ingredient,
            onDismiss = { editingIngredient = null },
            onSubmit = { input ->
                viewModel.updateIngredient(
                    ingredient.copy(
                        name = input.name,
                        normalizedName = Ingredient.normalize(input.name),
                        category = input.category,
                        quantity = input.quantity,
                        unit = input.unit,
                        expirationDateEpochDay = input.expirationDateEpochDay,
                        lowStockThreshold = input.lowStockThreshold,
                        notes = input.notes
                    )
                )
                editingIngredient = null
            }
        )
    }

    pendingDuplicate?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateDialog,
            title = { Text("Намирницата веќе постои") },
            text = {
                Text(
                    "„${pending.existing.name}“ веќе ја имаш дома (${formatQuantityWithUnit(pending.existing.quantity, pending.existing.unit)}). " +
                        "Да ја зголемам количината наместо да креирам нова ставка?"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmMergeDuplicate) { Text("Ажурирај количина") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::confirmAddAsSeparateEntry) { Text("Додади посебно") }
            }
        )
    }
}

private fun stepFor(unit: MeasurementUnit): Double = when (unit.group) {
    com.mojrucek.app.domain.unit.UnitGroup.WEIGHT -> if (unit == MeasurementUnit.KILOGRAM) 0.1 else 50.0
    com.mojrucek.app.domain.unit.UnitGroup.VOLUME -> if (unit == MeasurementUnit.LITER) 0.1 else 50.0
    com.mojrucek.app.domain.unit.UnitGroup.COUNT -> 1.0
}

@Composable
private fun IngredientRow(
    ingredient: Ingredient,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${ingredient.category.displayName} · ${formatQuantityWithUnit(ingredient.quantity, ingredient.unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ingredient.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDecrease) { Icon(Icons.Filled.Remove, contentDescription = "Намали") }
            IconButton(onClick = onIncrease) { Icon(Icons.Filled.Add, contentDescription = "Зголеми") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Избриши") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientFormDialog(
    initial: Ingredient?,
    onDismiss: () -> Unit,
    onSubmit: (NewIngredientInput) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: IngredientCategory.DRUGO) }
    var quantityText by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: MeasurementUnit.PIECE) }
    var lowStockText by remember { mutableStateOf(initial?.lowStockThreshold?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val quantity = quantityText.toDoubleOrNull()
    val isValid = name.isNotBlank() && quantity != null && quantity >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Нова намирница" else "Уреди намирница") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Име") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = category.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категорија") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    androidx.compose.material3.ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        IngredientCategory.entries.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(c.displayName) },
                                onClick = { category = c; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Количина") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuBox(
                        expanded = unitMenuExpanded,
                        onExpandedChange = { unitMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = unit.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Единица") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        androidx.compose.material3.ExposedDropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                            MeasurementUnit.entries.forEach { u ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(u.displayName) },
                                    onClick = { unit = u; unitMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = lowStockText,
                    onValueChange = { lowStockText = it },
                    label = { Text("Праг за „при крај“ (опционално)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Белешки (опционално)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onSubmit(
                        NewIngredientInput(
                            name = name.trim(),
                            category = category,
                            quantity = quantity ?: 0.0,
                            unit = unit,
                            expirationDateEpochDay = initial?.expirationDateEpochDay,
                            lowStockThreshold = lowStockText.toDoubleOrNull(),
                            notes = notes.ifBlank { null }
                        )
                    )
                }
            ) { Text("Зачувај") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Откажи") }
        }
    )
}
