package com.mojrucek.app.ui.ingredients

import com.mojrucek.app.domain.unit.UnitConverter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.mojrucek.app.ui.components.AppFab
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.IngredientSuggestion
import com.mojrucek.app.domain.usecase.IngredientSuggestions
import com.mojrucek.app.domain.usecase.WeekCalculator
import com.mojrucek.app.ui.components.AppBottomSheet
import com.mojrucek.app.ui.components.AppChip
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.AppDropdownMenu
import com.mojrucek.app.ui.components.AppDropdownMenuItem
import com.mojrucek.app.ui.components.AppMenuShape
import com.mojrucek.app.ui.components.CategoryChipsRow
import com.mojrucek.app.ui.components.CategoryIconCircle
import com.mojrucek.app.ui.components.ChipRow
import com.mojrucek.app.ui.components.ChipSelectionStyle
import com.mojrucek.app.ui.components.CircleIconButton
import com.mojrucek.app.ui.components.EmptyState
import com.mojrucek.app.ui.components.MetaPill
import com.mojrucek.app.ui.components.PillDropdownField
import com.mojrucek.app.ui.components.PillListRow
import com.mojrucek.app.ui.components.PillSearchField
import com.mojrucek.app.ui.components.PillTextButton
import com.mojrucek.app.ui.components.PillTextField
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.components.appMenuBorder
import com.mojrucek.app.ui.components.formatQuantityWithUnit
import com.mojrucek.app.ui.components.icon
import com.mojrucek.app.ui.components.tabListBottomPadding
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.mojrucek.app.domain.text.MkPlural

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(viewModel: IngredientsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val pendingDuplicate by viewModel.pendingDuplicate.collectAsState()
    val knownNames by viewModel.knownNames.collectAsState()
    val suggestionSource by viewModel.suggestionSource.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // No top bar: the Scaffold's top padding is the status-bar inset, and ScreenHeader sits below it.
    Scaffold(
        floatingActionButton = {
            AppFab(onClick = { showAddDialog = true }, contentDescription = "Додади намирница")
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ScreenHeader(
                title = "Намирници",
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
            ) {
                Box {
                    CircleIconButton(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "Подреди",
                        onClick = { showSortMenu = true }
                    )
                    AppDropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        AppDropdownMenuItem(
                            "Име",
                            onClick = { viewModel.setSort(IngredientSort.NAME); showSortMenu = false },
                            leadingIcon = Icons.Outlined.SortByAlpha,
                            selected = state.sort == IngredientSort.NAME
                        )
                        AppDropdownMenuItem(
                            "Категорија",
                            onClick = { viewModel.setSort(IngredientSort.CATEGORY); showSortMenu = false },
                            leadingIcon = Icons.Outlined.Category,
                            selected = state.sort == IngredientSort.CATEGORY
                        )
                        AppDropdownMenuItem(
                            "Количина",
                            onClick = { viewModel.setSort(IngredientSort.QUANTITY); showSortMenu = false },
                            leadingIcon = Icons.Outlined.Numbers,
                            selected = state.sort == IngredientSort.QUANTITY
                        )
                        AppDropdownMenuItem(
                            "Рок на траење",
                            onClick = { viewModel.setSort(IngredientSort.EXPIRATION); showSortMenu = false },
                            leadingIcon = Icons.Outlined.Event,
                            selected = state.sort == IngredientSort.EXPIRATION
                        )
                    }
                }
            }

            PillSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = "Пребарај",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp)
            )

            // Category is a single choice (filled); the two filters below are independent toggles (outlined).
            CategoryChipsRow(
                categories = IngredientCategory.entries,
                selected = state.categoryFilter,
                onSelect = viewModel::setCategoryFilter,
                counts = state.categoryCounts,
                totalCount = state.allIngredients.size,
                contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal)
            )
            ChipRow {
                AppChip(
                    "При крај",
                    selected = state.lowStockOnly,
                    onClick = { viewModel.setLowStockOnly(!state.lowStockOnly) },
                    selectedStyle = ChipSelectionStyle.Outlined
                )
                AppChip(
                    "Истекува наскоро",
                    selected = state.expiringOnly,
                    onClick = { viewModel.setExpiringOnly(!state.expiringOnly) },
                    selectedStyle = ChipSelectionStyle.Outlined
                )
            }

            if (state.visibleIngredients.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Kitchen,
                    title = if (state.allIngredients.isEmpty())
                        "Нема намирници. Додади ја првата од копчето +."
                    else "Нема намирници што одговараат на филтерот.",
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = tabListBottomPadding(padding))
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Spacing.screenHorizontal, top = 8.dp, end = Spacing.screenHorizontal,
                        bottom = tabListBottomPadding(padding, withFab = true)
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.visibleIngredients, key = { it.id }) { ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            onIncrease = { viewModel.adjustQuantity(ingredient, stepFor(ingredient.unit)) },
                            onDecrease = { viewModel.adjustQuantity(ingredient, -stepFor(ingredient.unit)) },
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
            knownNames = knownNames,
            suggestionSource = suggestionSource,
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
            knownNames = knownNames,
            suggestionSource = suggestionSource,
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
        AppDialog(
            onDismissRequest = viewModel::dismissDuplicateDialog,
            title = "Намирницата веќе постои",
            text = "„${pending.existing.name}“ веќе ја имаш дома (${formatQuantityWithUnit(pending.existing.quantity, pending.existing.unit)}). " +
                "Да ја зголемам количината наместо да креирам нова ставка?",
            confirmLabel = "Ажурирај количина",
            onConfirm = viewModel::confirmMergeDuplicate,
            dismissLabel = "Додади посебно",
            onDismiss = viewModel::confirmAddAsSeparateEntry
        )
    }
}

private fun stepFor(unit: MeasurementUnit): Double = when (unit.group) {
    com.mojrucek.app.domain.unit.UnitGroup.WEIGHT -> if (unit == MeasurementUnit.KILOGRAM) 0.1 else 50.0
    com.mojrucek.app.domain.unit.UnitGroup.VOLUME -> if (unit == MeasurementUnit.LITER) 0.1 else 50.0
    com.mojrucek.app.domain.unit.UnitGroup.COUNT -> 1.0
}

/**
 * A greenContainer row (20dp corners): category icon circle (with a small accent dot when the item is
 * low on stock), then the name (titleMedium, up to 2 lines) and the category (one line); the expiry pill
 * gets its own full-width line below.
 * On the right the quantity is the most visible thing: number in titleMedium, unit in bodyMedium, accent
 * when low on stock, centred above the minus/plus stepper; then the overflow menu. Only the name column
 * flexes, so on narrow screens or with large text the name wraps and the quantity is never squeezed.
 * Tapping the row opens the edit sheet.
 */
@Composable
private fun IngredientRow(
    ingredient: Ingredient,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MojRucekTheme.colors.greenContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, top = 10.dp, end = 4.dp, bottom = 10.dp)
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            CategoryIconCircle(ingredient.category.icon())
            if (ingredient.isLowStock) {
                // Low stock: a small accent dot, read out with the "При крај" filter's label.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MojRucekTheme.colors.greenContainer)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .semantics { contentDescription = "При крај" }
                )
            }
        }
        Spacer(Modifier.width(Spacing.rowPadding))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                ingredient.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                ingredient.category.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QuantityText(ingredient)
            // Compact stepper: two circles on a background-coloured pill.
            Row(
                Modifier.clip(PillShape).background(MaterialTheme.colorScheme.background),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    Icons.Outlined.Remove,
                    contentDescription = "Намали",
                    onClick = onDecrease,
                    containerColor = Color.Transparent,
                    circleSize = 32.dp
                )
                CircleIconButton(
                    Icons.Outlined.Add,
                    contentDescription = "Зголеми",
                    onClick = onIncrease,
                    containerColor = Color.Transparent,
                    circleSize = 32.dp
                )
            }
        }
        Box {
            CircleIconButton(
                Icons.Outlined.MoreVert,
                contentDescription = "Повеќе опции",
                onClick = { showMenu = true },
                containerColor = Color.Transparent
            )
            AppDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                AppDropdownMenuItem(
                    "Уреди",
                    onClick = { showMenu = false; onClick() },
                    leadingIcon = Icons.Outlined.Edit
                )
                AppDropdownMenuItem(
                    "Избриши",
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = Icons.Outlined.DeleteOutline
                )
            }
        }
    }
    // Expiry on its own line under the name and category, using the full width to the right edge so it
    // never shares space with the stepper. Indented to line up with the name (40dp icon + 12dp gap).
    // The label may wrap inside the pill but is never cut off: it is the important part.
    expirationLabel(ingredient.expirationDateEpochDay)?.let { label ->
        val urgent = ingredient.expirationDateEpochDay!! - WeekCalculator.today().toEpochDay() <= EXPIRING_SOON_DAYS
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().padding(start = 40.dp + Spacing.rowPadding, end = 8.dp)) {
            ExpiryPill(label, urgent)
        }
    }
    }
}

/**
 * "2 пакувања": the number in titleMedium and the unit in bodyMedium, accent when low on stock. Built from
 * the same two parts as formatQuantityWithUnit (UnitConverter.formatQuantity + MkPlural.unitLabel), so the
 * text and plurals are identical. One line, never wrapped: the row gives the space to the name instead.
 */
@Composable
private fun QuantityText(ingredient: Ingredient) {
    val color = if (ingredient.isLowStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val number = UnitConverter.formatQuantity(ingredient.quantity)
    val unit = MkPlural.unitLabel(ingredient.quantity, ingredient.unit)
    val numberStyle = MaterialTheme.typography.titleMedium.toSpanStyle()
    val unitStyle = MaterialTheme.typography.bodyMedium.toSpanStyle()
    Text(
        buildAnnotatedString {
            withStyle(numberStyle) { append(number) }
            append(" ")
            withStyle(unitStyle) { append(unit) }
        },
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** Expiry as a MetaPill: accentSoft for expired or within 3 days, green when it is still fine. */
@Composable
private fun ExpiryPill(label: String, urgent: Boolean) {
    if (urgent) {
        MetaPill(
            Icons.Outlined.Event,
            label,
            containerColor = MojRucekTheme.colors.accentSoft,
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconTint = MaterialTheme.colorScheme.onSurface,
            maxLines = Int.MAX_VALUE
        )
    } else {
        MetaPill(
            Icons.Outlined.Event,
            label,
            containerColor = MojRucekTheme.colors.green,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            iconTint = MaterialTheme.colorScheme.onSecondary,
            maxLines = Int.MAX_VALUE
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientFormDialog(
    initial: Ingredient?,
    knownNames: List<String>,
    suggestionSource: List<IngredientSuggestion>,
    onDismiss: () -> Unit,
    onSubmit: (NewIngredientInput) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: IngredientCategory.DRUGO) }
    var quantityText by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: MeasurementUnit.PIECE) }
    var lowStockText by remember { mutableStateOf(initial?.lowStockThreshold?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var expirationEpochDay by remember { mutableStateOf(initial?.expirationDateEpochDay) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Hidden right after a pick so the list doesn't reappear for the chosen name.
    var suggestionsEnabled by remember { mutableStateOf(initial == null) }
    val suggestions = remember(name, knownNames, suggestionSource, suggestionsEnabled) {
        if (suggestionsEnabled) IngredientSuggestions.suggest(name, knownNames, limit = 5, source = suggestionSource) else emptyList()
    }

    val quantity = quantityText.toDoubleOrNull()
    val isValid = name.isNotBlank() && quantity != null && quantity >= 0.0
    // Error border only for a quantity that has text but fails the same check.
    val quantityError = quantityText.isNotBlank() && (quantity == null || quantity < 0.0)

    // The reference's "Edit" sheet: title with a close circle ("Откажи": the old dismiss button;
    // swipe-down and outside tap also dismiss), the fields, and a full-width save button that is
    // disabled exactly when the old "Зачувај" was.
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = if (initial == null) "Нова намирница" else "Уреди намирница",
        closeContentDescription = "Откажи",
        primaryActionLabel = "Зачувај",
        primaryActionEnabled = isValid,
        onPrimaryAction = {
            onSubmit(
                NewIngredientInput(
                    name = name.trim(),
                    category = category,
                    quantity = quantity ?: 0.0,
                    unit = unit,
                    expirationDateEpochDay = expirationEpochDay,
                    lowStockThreshold = lowStockText.toDoubleOrNull(),
                    notes = notes.ifBlank { null }
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PillTextField(
                    value = name,
                    onValueChange = { name = it; suggestionsEnabled = true },
                    label = "Име"
                )
                if (suggestions.isNotEmpty()) {
                    SuggestionList(
                        suggestions = suggestions,
                        source = suggestionSource,
                        onPick = { suggestion ->
                            name = suggestion.name
                            val known = suggestion.category?.let { suggestion } ?: IngredientSuggestions.lookup(suggestion.name, suggestionSource)
                            known?.category?.let { category = it }
                            known?.unit?.let { unit = it }
                            suggestionsEnabled = false
                        }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Категорија", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIconCircle(category.icon(), size = 32.dp)
                    Text(
                        category.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                CategoryChipsRow(
                    categories = IngredientCategory.entries,
                    selected = category,
                    onSelect = { category = it ?: IngredientCategory.DRUGO },
                    showAllOption = false
                )
            }

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
            ExpirationDateField(
                epochDay = expirationEpochDay,
                onPick = { showDatePicker = true },
                onClear = { expirationEpochDay = null }
            )
            PillTextField(
                value = lowStockText,
                onValueChange = { lowStockText = it },
                label = "Праг за „при крај“ (опционално)"
            )
            PillTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Белешки (опционално)",
                singleLine = false,
                minLines = 2
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (expirationEpochDay ?: WeekCalculator.today().toEpochDay()) * MILLIS_PER_DAY
        )
        val pickerColors = appDatePickerColors()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                PillTextButton("Во ред", onClick = {
                    pickerState.selectedDateMillis?.let { expirationEpochDay = it / MILLIS_PER_DAY }
                    showDatePicker = false
                })
            },
            dismissButton = {
                PillTextButton("Откажи", onClick = { showDatePicker = false })
            },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 0.dp,
            colors = pickerColors
        ) {
            DatePicker(state = pickerState, colors = pickerColors)
        }
    }
}

/** DatePicker colours from the app palette: background surface, accent selection, darkOlive text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun appDatePickerColors() = DatePickerDefaults.colors(
    containerColor = MaterialTheme.colorScheme.background,
    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    headlineContentColor = MaterialTheme.colorScheme.onSurface,
    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    navigationContentColor = MojRucekTheme.colors.darkOlive,
    yearContentColor = MaterialTheme.colorScheme.onSurface,
    currentYearContentColor = MaterialTheme.colorScheme.primary,
    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
    dayContentColor = MaterialTheme.colorScheme.onSurface,
    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
    todayContentColor = MaterialTheme.colorScheme.primary,
    todayDateBorderColor = MaterialTheme.colorScheme.primary,
    dividerColor = MaterialTheme.colorScheme.outlineVariant
)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Up to five name suggestions in a rounded background-colour panel (menu style) under the name
 * field; each is a PillListRow with the category's line icon. Picking one fills the form.
 */
@Composable
private fun SuggestionList(
    suggestions: List<IngredientSuggestion>,
    source: List<IngredientSuggestion>,
    onPick: (IngredientSuggestion) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppMenuShape)
            .background(MaterialTheme.colorScheme.background)
            .border(appMenuBorder(), AppMenuShape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        suggestions.forEach { suggestion ->
            val category = suggestion.category ?: IngredientSuggestions.lookup(suggestion.name, source)?.category
            PillListRow(
                title = suggestion.name,
                onClick = { onPick(suggestion) },
                titleMaxLines = 1,
                leading = {
                    CategoryIconCircle(
                        (category ?: IngredientCategory.DRUGO).icon(),
                        size = 32.dp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            )
        }
    }
}

private val expirationFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Read-only pill styled like a PillTextField: the label as a small top line, the date (or the
 * "Избери датум" placeholder) below. Tapping opens the date picker; when a date is set a
 * trailing circle clears it, otherwise a calendar icon shows.
 */
@Composable
private fun ExpirationDateField(epochDay: Long?, onPick: () -> Unit, onClear: () -> Unit) {
    val label = "Рок на траење (опционално)"
    val value = epochDay?.let { LocalDate.ofEpochDay(it).format(expirationFormatter) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(PillShape)
            .background(MojRucekTheme.colors.greenContainer)
            .clickable(role = Role.Button, onClick = onPick)
            .semantics { contentDescription = "$label: ${value ?: "Избери датум"}" }
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                value ?: "Избери датум",
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (epochDay != null) {
            CircleIconButton(
                Icons.Outlined.Close,
                contentDescription = "Отстрани датум",
                onClick = onClear,
                containerColor = Color.Transparent
            )
        } else {
            Icon(
                Icons.Outlined.Event,
                contentDescription = null,
                tint = MojRucekTheme.colors.darkOlive,
                modifier = Modifier.padding(end = 12.dp).size(24.dp)
            )
        }
    }
}

/** "Истекува за N дена" style label, or null when the item has no date or is far from expiring. */
private fun expirationLabel(epochDay: Long?): String? {
    epochDay ?: return null
    val days = epochDay - WeekCalculator.today().toEpochDay()
    return when {
        days < 0 -> "Истечено"
        days == 0L -> "Истекува денес"
        days == 1L -> "Истекува утре"
        days <= EXPIRING_SOON_DAYS -> "Истекува за ${MkPlural.days(days.toInt())}"
        else -> "Рок: ${LocalDate.ofEpochDay(epochDay).format(expirationFormatter)}"
    }
}

private const val EXPIRING_SOON_DAYS = 3L
