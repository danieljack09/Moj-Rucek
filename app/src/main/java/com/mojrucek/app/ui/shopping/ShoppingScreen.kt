package com.mojrucek.app.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.ui.components.EmptyState
import com.mojrucek.app.ui.components.formatQuantityWithUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пазарење") },
                actions = {
                    if (state.purchased.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearPurchased) { Text("Исчисти купени") }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Додади ставка")
            }
        }
    ) { padding ->
        if (state.unpurchasedByCategory.isEmpty() && state.purchased.isEmpty()) {
            EmptyState(
                message = "Листата за пазарење е празна.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.unpurchasedByCategory.isNotEmpty() || state.purchased.isNotEmpty()) {
                item(key = "select_all") {
                    FilledTonalButton(
                        onClick = viewModel::toggleSelectAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(if (state.allSelected) "Деселектирај ги сите" else "Селектирај ги сите")
                    }
                }
            }

            state.unpurchasedByCategory.forEach { (category, items) ->
                item(key = "header_${category.name}") {
                    Text(category.displayName, style = MaterialTheme.typography.titleMedium)
                }
                items(items, key = { it.id }) { item ->
                    ShoppingRow(
                        item = item,
                        onToggle = { viewModel.togglePurchased(item) },
                        onMarkWithInventory = {
                            viewModel.togglePurchased(item)
                            viewModel.addToInventory(item.copy(isPurchased = true))
                        },
                        onMarkWithoutInventory = {
                            viewModel.togglePurchased(item)
                            viewModel.keepWithoutAdding(item.copy(isPurchased = true))
                        },
                        onDelete = { viewModel.delete(item) }
                    )
                }
            }

            if (state.purchased.isNotEmpty()) {
                item(key = "purchased_header") {
                    Text("Купено", style = MaterialTheme.typography.titleMedium)
                }
                items(state.purchased, key = { it.id }) { item ->
                    ShoppingRow(
                        item = item,
                        onToggle = { viewModel.togglePurchased(item) },
                        onMarkWithInventory = { viewModel.addToInventory(item) },
                        onMarkWithoutInventory = { viewModel.keepWithoutAdding(item) },
                        onDelete = { viewModel.delete(item) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddShoppingItemDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { name, qty, unit, category ->
                viewModel.addManualItem(name, qty, unit, category)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingListItem,
    onToggle: () -> Unit,
    onMarkWithInventory: () -> Unit,
    onMarkWithoutInventory: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.isPurchased) {
                    Checkbox(checked = true, onCheckedChange = { onToggle() })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        formatQuantityWithUnit(item.quantity, item.unit),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            PurchaseActionBar(
                onAddToInventory = onMarkWithInventory,
                onForNow = onMarkWithoutInventory,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun PurchaseActionBar(
    onAddToInventory: () -> Unit,
    onForNow: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
    ) {
        ActionSegment(
            icon = Icons.Filled.Home,
            background = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            contentDescription = "Во инвентар",
            onClick = onAddToInventory,
            modifier = Modifier.weight(0.5f).fillMaxHeight()
        )
        ActionSegmentDivider()
        ActionSegment(
            icon = Icons.Filled.Schedule,
            background = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            contentDescription = "За сега",
            onClick = onForNow,
            modifier = Modifier.weight(0.3f).fillMaxHeight()
        )
        ActionSegmentDivider()
        ActionSegment(
            icon = Icons.Filled.Delete,
            background = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            contentDescription = "Избриши",
            onClick = onDelete,
            modifier = Modifier.weight(0.2f).fillMaxHeight()
        )
    }
}

@Composable
private fun ActionSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActionSegmentDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outline)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShoppingItemDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Double, MeasurementUnit, IngredientCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(MeasurementUnit.PIECE) }
    var category by remember { mutableStateOf(IngredientCategory.DRUGO) }
    var unitExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val quantity = quantityText.toDoubleOrNull()
    val isValid = name.isNotBlank() && quantity != null && quantity > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додади во листа за пазарење") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ставка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Количина") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = unit.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Единица") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            MeasurementUnit.entries.forEach { u ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(u.displayName) },
                                    onClick = { unit = u; unitExpanded = false }
                                )
                            }
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = category.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категорија") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        IngredientCategory.entries.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(c.displayName) },
                                onClick = { category = c; categoryExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onSubmit(name.trim(), quantity ?: 1.0, unit, category) }) {
                Text("Додади")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Откажи") } }
    )
}
