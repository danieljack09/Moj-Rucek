package com.mojrucek.app.ui.recipes

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import com.mojrucek.app.ui.components.AppProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.domain.limits.LimitTexts
import com.mojrucek.app.domain.limits.RecipeAccess
import com.mojrucek.app.domain.model.MatchStatus
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.AppSnackbarHost
import com.mojrucek.app.ui.components.CircleIconButton
import com.mojrucek.app.ui.components.OutlinePillButton
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.components.SectionCard
import com.mojrucek.app.ui.components.SectionCardFill
import com.mojrucek.app.ui.components.formatQuantityWithUnit
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
import com.mojrucek.app.domain.text.MkPlural

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicated: (Long) -> Unit,
    onOpenShopping: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCookConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Keeps the "go to shopping" button visible after the snackbar is gone.
    var addedToShoppingList by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastAddedToShoppingList) {
        if (state.lastAddedToShoppingList) {
            addedToShoppingList = true
            viewModel.clearAddedFlag()
            val result = snackbarHostState.showSnackbar(
                message = "Сите намирници што недостасуваат се додадени во листата за пазарење.",
                actionLabel = "Кон пазарење",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onOpenShopping()
        }
    }
    LaunchedEffect(state.lastCooked) {
        if (state.lastCooked) {
            snackbarHostState.showSnackbar("Намирниците се одземени од инвентарот.")
            viewModel.clearCookedFlag()
        }
    }

    // No top bar: the circle buttons over the cover replace it. The Scaffold's top padding is the
    // status-bar inset; the list ends above the system navigation bar.
    Scaffold(snackbarHost = { AppSnackbarHost(snackbarHostState) }) { padding ->
        if (state.isLoading || state.notFound || state.recipe == null || state.match == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Keeps "back" reachable while loading and when the recipe is missing.
                ScreenHeader(
                    title = "Рецепт",
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
                )
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (state.isLoading) AppProgressIndicator() else Text("Рецептот не постои.")
                }
            }
            return@Scaffold
        }

        val recipe = state.recipe!!.recipe
        val match = state.match!!
        val description = when (val a = state.access) {
            RecipeAccess.Own -> recipe.description
            is RecipeAccess.Unlocked -> a.details.description
            RecipeAccess.Locked -> null
        }
        val steps: List<String>
        val notes: String?
        when (val a = state.access) {
            RecipeAccess.Own -> { steps = recipe.instructionSteps; notes = recipe.notes }
            is RecipeAccess.Unlocked -> { steps = a.details.instructionSteps; notes = a.details.notes }
            RecipeAccess.Locked -> { steps = emptyList(); notes = null }
        }
        val locked = state.access == RecipeAccess.Locked

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                bottom = padding.calculateBottomPadding() + Spacing.betweenSections
            )
        ) {
            // Cover with the actions laid over it.
            item {
                Box(Modifier.fillMaxWidth()) {
                    RecipeCover(
                        imageUri = recipe.imageUri,
                        category = recipe.category,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 40.dp)
                            .fillMaxWidth(0.7f)
                            .clip(CircleShape)
                    )
                    CircleIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Назад",
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    Row(Modifier.align(Alignment.TopEnd)) {
                        CircleIconButton(
                            if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Омилено",
                            onClick = viewModel::toggleFavorite,
                            iconTint = MaterialTheme.colorScheme.primary
                        )
                        CircleIconButton(Icons.Outlined.Edit, contentDescription = "Уреди", onClick = { onEdit(recipe.id) })
                        CircleIconButton(Icons.Outlined.ContentCopy, contentDescription = "Копирај", onClick = { viewModel.duplicate(onDuplicated) })
                        CircleIconButton(Icons.Outlined.DeleteOutline, contentDescription = "Избриши", onClick = { showDeleteConfirm = true })
                    }
                }
            }

            // Title and the stat row.
            item {
                Spacer(Modifier.height(Spacing.betweenCards))
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.betweenCards))
                StatRow(
                    prepMinutes = recipe.prepTimeMinutes,
                    cookMinutes = recipe.cookTimeMinutes,
                    servings = state.desiredServings,
                    categoryName = recipe.category?.displayName ?: "Без категорија"
                )
            }

            // Description and tags.
            if (!description.isNullOrBlank() || recipe.tags.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.betweenSections))
                    if (!description.isNullOrBlank()) {
                        SectionTitle("Опис")
                        Spacer(Modifier.height(8.dp))
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (recipe.tags.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        TagRow(recipe.tags)
                    }
                }
            }

            // Ingredients: header with the servings stepper, coverage line, rows with dividers.
            item {
                Spacer(Modifier.height(Spacing.betweenSections))
                // FlowRow: the stepper sits beside the title when both fit, and moves under it on narrow
                // screens or with large text, so the title never breaks mid-word.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionTitle("Потребни намирници", modifier = Modifier.align(Alignment.CenterVertically))
                    ServingsStepper(
                        servings = state.desiredServings,
                        onChange = viewModel::setDesiredServings
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Покриени намирници: ${match.coveredIngredients} од ${match.totalIngredients}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(match.matches) { index, m ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                IngredientLine(
                    name = m.name,
                    quantity = formatQuantityWithUnit(m.requiredQuantity, m.requiredUnit),
                    available = m.status == MatchStatus.AVAILABLE
                )
            }

            // What is missing, then the actions (the same buttons and conditions as before).
            item {
                if (match.missing.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.betweenCards))
                    SectionCard(fill = SectionCardFill.Card) {
                        Text("Недостигаат:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        match.missing.forEach { m ->
                            val text = when (m.status) {
                                MatchStatus.MISSING -> "${m.name} — ${formatQuantityWithUnit(m.requiredQuantity, m.requiredUnit)}"
                                MatchStatus.INSUFFICIENT -> "${m.name} — уште " +
                                    formatQuantityWithUnit(m.requiredQuantity - m.availableQuantity, m.requiredUnit)
                                MatchStatus.UNIT_MISMATCH -> "${m.name} — единицата во инвентарот не одговара"
                                MatchStatus.AVAILABLE -> ""
                            }
                            if (text.isNotBlank()) {
                                Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.betweenCards))
                if (match.missing.isNotEmpty()) {
                    OutlinePillButton(
                        "Додади ги сите намирници што недостасуваат во листата за пазарење",
                        onClick = viewModel::addMissingToShoppingList,
                        icon = Icons.Outlined.ShoppingCart,
                        modifier = Modifier.fillMaxWidth(),
                        // The label is long; it must not be cut off.
                        maxLines = 4
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // The reference pair: outline + primary side by side (the outline only after adding).
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (addedToShoppingList) {
                        OutlinePillButton(
                            "Оди во листата за пазарење",
                            onClick = onOpenShopping,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrimaryPillButton(
                        "Го зготвив ова",
                        onClick = { showCookConfirm = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Steps (or the locked notice) and notes.
            item {
                Spacer(Modifier.height(Spacing.betweenSections))
                if (locked) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberCircle { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(Spacing.rowPadding))
                            Text(LimitTexts.LOCKED_TITLE, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            LimitTexts.LOCKED_TEXT,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SectionTitle("Чекори")
                }
            }
            if (!locked) {
                itemsIndexed(steps) { index, step ->
                    Spacer(Modifier.height(if (index == 0) 8.dp else 12.dp))
                    StepCard(number = index + 1, text = step)
                }
                if (!notes.isNullOrBlank()) {
                    item {
                        Spacer(Modifier.height(Spacing.betweenSections))
                        SectionTitle("Белешки")
                        Spacer(Modifier.height(8.dp))
                        SectionCard(fill = SectionCardFill.GreenContainer) {
                            Text(notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showCookConfirm && state.recipe != null && state.match != null) {
        AppDialog(
            onDismissRequest = { showCookConfirm = false },
            title = "Да ги одземам намирниците?",
            text = "Ќе се одземат следниве количини од инвентарот (за ${MkPlural.servings(state.desiredServings)}):",
            confirmLabel = "Потврди",
            onConfirm = {
                viewModel.confirmCooked()
                showCookConfirm = false
            },
            dismissLabel = "Откажи",
            onDismiss = { showCookConfirm = false }
        ) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                state.match!!.matches.forEach { m ->
                    Text(
                        "• ${m.name}: ${formatQuantityWithUnit(m.requiredQuantity, m.requiredUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AppDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Избриши рецепт?",
            text = "Ова не може да се врати.",
            confirmLabel = "Избриши",
            onConfirm = {
                viewModel.delete(onBack)
                showDeleteConfirm = false
            },
            dismissLabel = "Откажи",
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() }
    )
}

/**
 * The reference's "Rating / Total time / Difficulty / Servings" row, with the data the app has.
 * Each column: a small accent line icon, the value, and a caption.
 */
@Composable
private fun StatRow(prepMinutes: Int, cookMinutes: Int, servings: Int, categoryName: String) {
    // Four columns when each gets about 96dp at the current text size; otherwise a 2 x 2 grid,
    // so values and captions are not cut off or broken mid-word.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val fourColumns = maxWidth / 4 >= 96.dp * fontScale
        val prep: @Composable (Modifier) -> Unit = { StatColumn(Icons.Outlined.Timer, "$prepMinutes мин", "Подготовка", it) }
        val cook: @Composable (Modifier) -> Unit = { StatColumn(Icons.Outlined.LocalFireDepartment, "$cookMinutes мин", "Готвење", it) }
        val serv: @Composable (Modifier) -> Unit = { StatColumn(Icons.Outlined.Group, servings.toString(), "Порции", it) }
        val cat: @Composable (Modifier) -> Unit = { StatColumn(Icons.Outlined.Category, categoryName, "Категорија", it) }
        if (fourColumns) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                prep(Modifier.weight(1f)); cook(Modifier.weight(1f)); serv(Modifier.weight(1f)); cat(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { prep(Modifier.weight(1f)); cook(Modifier.weight(1f)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { serv(Modifier.weight(1f)); cat(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatColumn(icon: ImageVector, value: String, caption: String, modifier: Modifier = Modifier) {
    // Icon above the value so the value gets the full column width (category names can be long).
    Column(modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Tags as static chip-style pills (display only: there is nothing to select or filter here). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            Box(
                Modifier
                    .height(32.dp)
                    .clip(PillShape)
                    .background(MojRucekTheme.colors.greenContainer)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

/**
 * Ingredient row: a dot (green when available, accent when missing or short), the name on the
 * left and the quantity in textSecondary on the right.
 */
@Composable
private fun IngredientLine(name: String, quantity: String, available: Boolean) {
    val dot: Color = if (available) MojRucekTheme.colors.green else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Spacing.rowPadding))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(quantity, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Servings as a pill: "Порции:" caption, then minus, the number and plus. */
@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(MojRucekTheme.colors.greenContainer)
            .padding(start = 12.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Порции:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CircleIconButton(
            Icons.Outlined.Remove,
            contentDescription = "Намали порции",
            onClick = { onChange((servings - 1).coerceAtLeast(1)) },
            containerColor = MaterialTheme.colorScheme.background,
            circleSize = 32.dp
        )
        Text(
            servings.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp).semantics { stateDescription = servings.toString() }
        )
        CircleIconButton(
            Icons.Outlined.Add,
            contentDescription = "Зголеми порции",
            onClick = { onChange(servings + 1) },
            containerColor = MaterialTheme.colorScheme.background,
            circleSize = 32.dp
        )
    }
}

/** A 32dp green circle holding a step number (or an icon). */
@Composable
private fun NumberCircle(content: @Composable () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(MojRucekTheme.colors.green),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSecondary,
            content = content
        )
    }
}

@Composable
private fun StepCard(number: Int, text: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            NumberCircle {
                Text(number.toString(), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(Spacing.rowPadding))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(top = 6.dp)
            )
        }
    }
}
