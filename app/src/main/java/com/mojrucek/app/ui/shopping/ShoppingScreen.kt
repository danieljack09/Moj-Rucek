package com.mojrucek.app.ui.shopping

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.mojrucek.app.ui.components.AppFab
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.ui.components.AppBottomSheet
import com.mojrucek.app.ui.components.AppCheckbox
import com.mojrucek.app.ui.components.AppSnackbarHost
import com.mojrucek.app.ui.components.EmptyState
import com.mojrucek.app.ui.components.PillDropdownField
import com.mojrucek.app.ui.components.PillTextField
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.components.SecondaryPillButton
import com.mojrucek.app.ui.components.formatQuantityWithUnit
import com.mojrucek.app.ui.components.icon
import com.mojrucek.app.ui.components.tabListBottomPadding
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
import kotlinx.coroutines.flow.collectLatest
import com.mojrucek.app.domain.text.MkPlural

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

    // No top bar: the Scaffold's top padding is the status-bar inset, and the header sits below it.
    Scaffold(
        floatingActionButton = {
            AppFab(onClick = { showAddDialog = true }, contentDescription = "Додади ставка")
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // The reference's "Grocery list" title, with the item count under it.
            Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                ScreenHeader(title = "Пазарење")
                if (state.totalCount > 0) {
                    Text(
                        itemCountLabel(state.totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.unpurchasedByCategory.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ShoppingBasket,
                    title = "Листата за пазарење е празна.",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = tabListBottomPadding(padding))
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    // Bottom padding keeps the last card clear of the FAB and the floating nav bar.
                    contentPadding = PaddingValues(
                        start = Spacing.screenHorizontal,
                        end = Spacing.screenHorizontal,
                        top = 8.dp,
                        bottom = tabListBottomPadding(padding, withFab = true)
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var isFirstCategory = true
                    state.unpurchasedByCategory.forEach { (category, rows) ->
                        // 24dp above each group (the list's 8dp spacing plus 16dp), none above the first.
                        val topPadding = if (isFirstCategory) 0.dp else Spacing.betweenSections - 8.dp
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
    MkPlural.items(count)

/** Group label: the category's line icon (in place of its emoji), then "name (count)" in labelMedium textSecondary. */
@Composable
private fun CategoryHeader(category: IngredientCategory, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "${category.displayName} ($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A card that can be swiped right to put the item in inventory, or left to delete it.
 * Both are reversible from the snackbar. Same swipe state, thresholds and directions as before.
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

/** Rounded like the row: green with a check for "into inventory", accent with the delete icon for "delete". */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, contentColor) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            MojRucekTheme.colors.green to MaterialTheme.colorScheme.onSecondary
        SwipeToDismissBoxValue.EndToStart ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        SwipeToDismissBoxValue.Settled -> Color.Transparent to Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.large)
            .padding(horizontal = 24.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> SwipeLabel(Icons.Outlined.Check, "Во инвентар", contentColor)
            SwipeToDismissBoxValue.EndToStart -> SwipeLabel(Icons.Outlined.DeleteOutline, "Избриши", contentColor)
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
}

@Composable
private fun SwipeLabel(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A greenContainer row in the reference's grocery-list style (20dp corners): name and source line on
 * the left, the quantity pill on the right, and the purchase actions as pills underneath.
 */
@Composable
private fun ShoppingCard(
    row: ShoppingRowUi,
    onBuy: () -> Unit,
    onBuyForNow: () -> Unit,
    onPartial: (toInventory: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val item = row.item
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MojRucekTheme.colors.greenContainer)
            .padding(start = 16.dp, top = Spacing.rowPadding, end = Spacing.rowPadding, bottom = Spacing.rowPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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

/** The listed amount as a pill (background colour on the greenContainer row). */
@Composable
private fun QuantityPill(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The three choices for a bought item as pills with 8dp gaps: "Во инвентар" is the main (accent)
 * action, "За сега" a background-colour pill, "Избриши" an outline pill. A tap uses the whole
 * listed amount; a long press on either purchase pill asks how much was actually bought.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseActionBar(
    onAddToInventory: () -> Unit,
    onAddToInventoryLong: () -> Unit,
    onForNow: () -> Unit,
    onForNowLong: () -> Unit,
    onDelete: () -> Unit
) {
    // One line of three equal pills when there is room (about 340dp at the current text size);
    // otherwise the pills keep their own width and wrap, so no label is cut off.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val oneLine = maxWidth >= 340.dp * LocalDensity.current.fontScale
        val segments: @Composable ((Int) -> Modifier) -> Unit = { segmentModifier ->
            ActionSegment(
                icon = Icons.Outlined.Home,
                label = "Во инвентар",
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onAddToInventory,
                onLongClick = onAddToInventoryLong,
                modifier = segmentModifier(0)
            )
            ActionSegment(
                icon = Icons.Outlined.Schedule,
                label = "За сега",
                background = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = onForNow,
                onLongClick = onForNowLong,
                modifier = segmentModifier(1)
            )
            ActionSegment(
                icon = Icons.Outlined.DeleteOutline,
                label = "Избриши",
                background = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                outlined = true,
                onClick = onDelete,
                onLongClick = null,
                modifier = segmentModifier(2)
            )
        }
        if (oneLine) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Same proportions as before the redesign.
                val weights = listOf(0.4f, 0.29f, 0.31f)
                segments { Modifier.weight(weights[it]) }
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                segments { Modifier }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionSegment(
    icon: ImageVector,
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(PillShape)
            .background(background)
            .then(if (outlined) Modifier.border(1.dp, contentColor, PillShape) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { "Делумно купено" }
            )
            .padding(horizontal = 8.dp),
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

/** "1.0" -> "1", "0.5" -> "0.5", so the pre-filled field shows the quantity the way the list does. */
private fun quantityToText(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()

/**
 * Sheet in the reference's "Edit" style. The close circle is the old "Откажи"; "Потврди" is the
 * full-width save, enabled under the same rule as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "Купено: ${item.name}",
        closeContentDescription = "Откажи",
        primaryActionLabel = "Потврди",
        primaryActionEnabled = isValid,
        onPrimaryAction = {
            // Buying more than listed isn't meaningful here, so cap at the listed amount.
            val quantity = (parsed ?: item.quantity).coerceIn(Double.MIN_VALUE, item.quantity)
            onConfirm(quantity, toInventory)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "На листата: ${formatQuantityWithUnit(item.quantity, item.unit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PillTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = "Количина (${item.unit.displayName})",
                isError = !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryPillButton("Половина", onClick = { quantityText = quantityToText(item.quantity / 2) })
                SecondaryPillButton("Сè", onClick = { quantityText = quantityToText(item.quantity) })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(PillShape)
                    .toggleable(value = toInventory, role = Role.Checkbox, onValueChange = { toInventory = it })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCheckbox(checked = toInventory, onCheckedChange = null)
                Spacer(Modifier.width(Spacing.rowPadding))
                Text("Додади во инвентар", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Sheet in the reference's "Edit" style: item, quantity and unit, category, and a full-width
 * "Додади" enabled under the same rule as before. The close circle is the old "Откажи".
 */
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

    val quantity = quantityText.toDoubleOrNull()
    val isValid = name.isNotBlank() && quantity != null && quantity > 0.0
    // Error border only for a quantity that has text but fails the same check.
    val quantityError = quantityText.isNotBlank() && (quantity == null || quantity <= 0.0)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "Додади во листа за пазарење",
        closeContentDescription = "Откажи",
        primaryActionLabel = "Додади",
        primaryActionEnabled = isValid,
        onPrimaryAction = { onSubmit(name.trim(), quantity ?: 1.0, unit, category) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillTextField(value = name, onValueChange = { name = it }, label = "Ставка")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = "Количина",
                    isError = quantityError,
                    modifier = Modifier.weight(1f)
                )
                PillDropdownField(
                    label = "Единица",
                    selectedText = unit.displayName,
                    options = MeasurementUnit.entries,
                    optionLabel = { it.displayName },
                    isSelected = { it == unit },
                    onSelect = { unit = it },
                    modifier = Modifier.weight(1f)
                )
            }
            PillDropdownField(
                label = "Категорија",
                selectedText = category.displayName,
                options = IngredientCategory.entries,
                optionLabel = { it.displayName },
                isSelected = { it == category },
                onSelect = { category = it }
            )
        }
    }
}
