package com.mojrucek.app.ui.shopping

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.ui.components.EmptyState
import com.mojrucek.app.ui.components.formatQuantityWithUnit
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var partialPurchase by remember { mutableStateOf<Pair<ShoppingListItem, Boolean>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // collectLatest: a new action replaces the snackbar that is still showing, so checking off
    // several items quickly never leaves a queue of stale messages behind.
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = "Врати",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.undo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Пазарење")
                        if (state.totalCount > 0) {
                            Text(
                                itemCountLabel(state.totalCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Додади ставка")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.unpurchasedByCategory.isEmpty()) {
            EmptyState(
                message = "Листата за пазарење е празна.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Bottom padding keeps the last card clear of the FAB.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var isFirstCategory = true
            state.unpurchasedByCategory.forEach { (category, rows) ->
                val topPadding = if (isFirstCategory) 0.dp else 16.dp
                isFirstCategory = false
                item(key = "header_${category.name}") {
                    CategoryHeader(
                        category = category,
                        count = rows.size,
                        modifier = Modifier.padding(top = topPadding).animateItem()
                    )
                }
                items(rows, key = { it.item.id }) { row ->
                    SwipeableShoppingCard(
                        row = row,
                        onBuy = { viewModel.addToInventory(row.item, row.item.quantity) },
                        onBuyForNow = { viewModel.keepWithoutAdding(row.item, row.item.quantity) },
                        onPartial = { toInventory -> partialPurchase = row.item to toInventory },
                        onDelete = { viewModel.delete(row.item) },
                        modifier = Modifier.animateItem()
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

    partialPurchase?.let { (item, initialToInventory) ->
        PartialPurchaseDialog(
            item = item,
            initialToInventory = initialToInventory,
            onDismiss = { partialPurchase = null },
            onConfirm = { quantity, toInventory ->
                if (toInventory) viewModel.addToInventory(item, quantity)
                else viewModel.keepWithoutAdding(item, quantity)
                partialPurchase = null
            }
        )
    }
}

/** Macedonian agreement: "1 ставка", "21 ставка", but "11 ставки", "5 ставки". */
private fun itemCountLabel(count: Int): String =
    if (count % 10 == 1 && count % 100 != 11) "$count ставка" else "$count ставки"

@Composable
private fun CategoryHeader(category: IngredientCategory, count: Int, modifier: Modifier = Modifier) {
    Text(
        "${category.emoji} ${category.displayName} ($count)",
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}

/**
 * A card that can be swiped right to put the item in inventory, or left to delete it.
 * Both are reversible from the snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableShoppingCard(
    row: ShoppingRowUi,
    onBuy: () -> Unit,
    onBuyForNow: () -> Unit,
    onPartial: (toInventory: Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // Act on the settled value rather than in confirmValueChange, which can fire more than once per swipe.
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onBuy()
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) }
    ) {
        ShoppingCard(
            row = row,
            onBuy = onBuy,
            onBuyForNow = onBuyForNow,
            onPartial = onPartial,
            onDelete = onDelete
        )
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, contentColor) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SwipeToDismissBoxValue.EndToStart ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        SwipeToDismissBoxValue.Settled -> Color.Transparent to Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = 20.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> SwipeLabel(Icons.Filled.Home, "Во инвентар", contentColor)
            SwipeToDismissBoxValue.EndToStart -> SwipeLabel(Icons.Filled.Delete, "Избриши", contentColor)
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
}

@Composable
private fun SwipeLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ShoppingCard(
    row: ShoppingRowUi,
    onBuy: () -> Unit,
    onBuyForNow: () -> Unit,
    onPartial: (toInventory: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val item = row.item

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    row.sourceLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                QuantityPill(formatQuantityWithUnit(item.quantity, item.unit))
            }
            PurchaseActionBar(
                onAddToInventory = onBuy,
                onAddToInventoryLong = { onPartial(true) },
                onForNow = onBuyForNow,
                onForNowLong = { onPartial(false) },
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun QuantityPill(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * The three choices for a bought item, each with icon and label. A tap uses the whole listed
 * amount; a long press on either purchase segment asks how much was actually bought.
 */
@Composable
private fun PurchaseActionBar(
    onAddToInventory: () -> Unit,
    onAddToInventoryLong: () -> Unit,
    onForNow: () -> Unit,
    onForNowLong: () -> Unit,
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
            label = "Во инвентар",
            background = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onAddToInventory,
            onLongClick = onAddToInventoryLong,
            modifier = Modifier.weight(0.4f).fillMaxHeight()
        )
        ActionSegmentDivider()
        ActionSegment(
            icon = Icons.Filled.Schedule,
            label = "За сега",
            background = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onForNow,
            onLongClick = onForNowLong,
            modifier = Modifier.weight(0.33f).fillMaxHeight()
        )
        ActionSegmentDivider()
        ActionSegment(
            icon = Icons.Filled.Delete,
            label = "Избриши",
            background = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onClick = onDelete,
            onLongClick = null,
            modifier = Modifier.weight(0.27f).fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { "Делумно купено" }
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/** "1.0" -> "1", "0.5" -> "0.5", so the pre-filled field shows the quantity the way the list does. */
private fun quantityToText(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()

@Composable
private fun PartialPurchaseDialog(
    item: ShoppingListItem,
    initialToInventory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, toInventory: Boolean) -> Unit
) {
    var quantityText by remember(item.id) { mutableStateOf(quantityToText(item.quantity)) }
    var toInventory by remember(item.id) { mutableStateOf(initialToInventory) }

    // Accept a decimal comma too, since that is how quantities are commonly typed here.
    val parsed = quantityText.trim().replace(',', '.').toDoubleOrNull()
    val isValid = parsed != null && parsed.isFinite() && parsed > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Купено: ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "На листата: ${formatQuantityWithUnit(item.quantity, item.unit)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Количина (${item.unit.displayName})") },
                    isError = !isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { quantityText = quantityToText(item.quantity / 2) },
                        label = { Text("Половина") }
                    )
                    AssistChip(
                        onClick = { quantityText = quantityToText(item.quantity) },
                        label = { Text("Сè") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = toInventory, onCheckedChange = { toInventory = it })
                    Text("Додади во инвентар", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    // Buying more than listed isn't meaningful here, so cap at the listed amount.
                    val quantity = (parsed ?: item.quantity).coerceIn(Double.MIN_VALUE, item.quantity)
                    onConfirm(quantity, toInventory)
                }
            ) { Text("Потврди") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Откажи") } }
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
