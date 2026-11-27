package com.mojrucek.app.ui.home

import com.mojrucek.app.ui.recipes.RecipeCover
import com.mojrucek.app.ui.components.longDateMk
import com.mojrucek.app.ui.components.MetaPill
import com.mojrucek.app.ui.components.MacedonianDayNames
import com.mojrucek.app.domain.text.MkPlural
import com.mojrucek.app.domain.model.Readiness
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.mojrucek.app.ui.recipes.RecipeThumb
import com.mojrucek.app.ui.components.AppProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.util.MacedonianSearch
import com.mojrucek.app.ui.components.AppBottomSheet
import com.mojrucek.app.ui.components.CategoryIconCircle
import com.mojrucek.app.ui.components.CircleIconButton
import com.mojrucek.app.ui.components.PillListRow
import com.mojrucek.app.ui.components.PillSearchField
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.components.PromoCard
import com.mojrucek.app.ui.components.SectionCard
import com.mojrucek.app.ui.components.SectionCardFill
import com.mojrucek.app.ui.components.SectionHeader
import com.mojrucek.app.ui.components.icon
import com.mojrucek.app.ui.components.tabListBottomPadding
import com.mojrucek.app.ui.recipes.recipeMetaLine
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenLowStock: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenNextWeekPlanner: () -> Unit,
    adBanner: (@Composable () -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    var showRecipePicker by remember { mutableStateOf(false) }
    var pickerRecipes by remember { mutableStateOf<List<RecipeWithIngredients>>(emptyList()) }

    // No top bar: the header is the first list item (it scrolls away, as in the reference). The
    // Scaffold's top padding is the status-bar inset; the bottom inset goes into the list padding.
    Scaffold { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AppProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            // Content runs behind the floating nav bar; the bottom padding keeps the last card clear of it.
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                top = 8.dp,
                end = Spacing.screenHorizontal,
                bottom = tabListBottomPadding(padding)
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections)
        ) {
            item {
                HomeHeader(onOpenSettings = onOpenSettings)
            }

            item {
                TodayLunchCard(
                    today = state.today,
                    onChangeClick = {
                        viewModel.recipesForPicker { pickerRecipes = it }
                        showRecipePicker = true
                    },
                    onOpenRecipe = onOpenRecipe
                )
            }

            item {
                WeekPreview(
                    days = state.weekDays,
                    onOpenRecipe = onOpenRecipe,
                    onOpenPlanner = onOpenPlanner
                )
            }

            item {
                KitchenOverview(
                    lowStockCount = state.lowStockIngredients.size,
                    shoppingCount = state.unpurchasedShoppingCount,
                    onOpenLowStock = onOpenLowStock,
                    onOpenShopping = onOpenShopping
                )
            }

            item {
                WeeklyReminderCard(
                    nextWeekPlanned = state.nextWeekPlanned,
                    onClick = onOpenNextWeekPlanner
                )
            }

            // Future ad banner: only occupies a list item (and its spacing) when an ad is supplied.
            if (adBanner != null) {
                item { AdBannerSlot(content = adBanner) }
            }
        }
    }

    if (showRecipePicker) {
        RecipePickerDialog(
            recipes = pickerRecipes,
            onDismiss = { showRecipePicker = false },
            onPick = { recipeId ->
                viewModel.setTodayMeal(recipeId)
                showRecipePicker = false
            },
            onClear = {
                viewModel.setTodayMeal(null)
                showRecipePicker = false
            }
        )
    }
}

/**
 * The reference's "Let's cook a masterpiece" block: the app name as a small line, the greeting
 * as the headlineLarge screen title, and Settings as a trailing circle button.
 */
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Мој Ручек",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Што ќе готвиме денес?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(Icons.Outlined.Settings, contentDescription = "Поставки", onClick = onOpenSettings)
    }
}

/**
 * Green promo card. Nothing planned: the card's label as the title with the "nothing chosen" line
 * under it. Meal planned: the recipe name as the title with the card's label under it, plus the
 * "open recipe" link. The dish circle bleeds off the right edge.
 */
@Composable
private fun TodayLunchCard(today: TodayMealUi?, onChangeClick: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    PromoCard(
        title = today?.recipeName ?: "🍽️ Денешен ручек",
        subtitle = if (today == null) "Нема избрано јадење за денес." else "🍽️ Денешен ручек",
        buttonLabel = if (today == null) "Избери јадење" else "Промени",
        onButtonClick = onChangeClick,
        secondaryButtonLabel = if (today != null) "Отвори рецепт" else null,
        onSecondaryButtonClick = today?.let { { onOpenRecipe(it.recipeId) } },
        textWidthFraction = 0.62f,
        imageBleed = 36.dp
    ) {
        // TodayMealUi (HomeViewModel) carries only the recipe id and name, no photo or category,
        // so this is the generic recipe icon on a dish-like circle.
        DishCircle(icon = (null as RecipeCategory?).icon(), size = 148.dp)
    }
}

/** A circle standing in for the dish photo on a green promo card: greenContainer with a darkOlive icon. */
@Composable
private fun DishCircle(icon: ImageVector, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MojRucekTheme.colors.greenContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MojRucekTheme.colors.darkOlive, modifier = Modifier.size(size * 0.42f))
    }
}

private val shortDayNames = listOf("ПОН", "ВТО", "СРЕ", "ЧЕТ", "ПЕТ", "САБ", "НЕД")

@Composable
private fun WeekPreview(
    days: List<WeekDayUi>,
    onOpenRecipe: (Long) -> Unit,
    onOpenPlanner: () -> Unit
) {
    // The planned day whose preview sheet is open; null when closed.
    var previewDay by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Оваа недела")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEach { day ->
                DayPill(
                    day = day,
                    // Planned day: the meal preview sheet. Empty day: the planner, as before.
                    onClick = { if (day.meal != null) previewDay = day.dayOfWeek else onOpenPlanner() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val shown = previewDay?.let { dow -> days.firstOrNull { it.dayOfWeek == dow } }
    val meal = shown?.meal
    if (shown != null && meal != null) {
        MealPreviewSheet(
            day = shown,
            meal = meal,
            onDismiss = { previewDay = null },
            onChange = { previewDay = null; onOpenPlanner() },
            onOpenRecipe = { previewDay = null; onOpenRecipe(meal.recipeId) }
        )
    } else if (previewDay != null) {
        // The meal was cleared meanwhile (e.g. from another screen): nothing to preview.
        previewDay = null
    }
}

/**
 * Half-height preview of a planned day: the day and date (the announced sheet title), the recipe
 * photo, name, time / servings / category pills, the description (up to 4 lines) and ingredient
 * coverage coloured by readiness. "Промени" opens the planner, "Отвори рецепт" the full recipe;
 * both close the sheet, as do swipe-down, the close button and back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MealPreviewSheet(
    day: WeekDayUi,
    meal: TodayMealUi,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    onOpenRecipe: () -> Unit
) {
    val preview = day.preview
    val title = day.date?.longDateMk() ?: MacedonianDayNames[day.dayOfWeek - 1]
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        closeContentDescription = "Затвори",
        // Opens to its content height (about two thirds of the screen) so the two buttons are always visible;
        // a half-height partial state would hide them below the screen edge.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        titleStyle = MaterialTheme.typography.labelLarge,
        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        secondaryActionLabel = "Промени",
        onSecondaryAction = onChange,
        primaryActionLabel = "Отвори рецепт",
        onPrimaryAction = onOpenRecipe
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RecipeCover(
                imageUri = preview?.imageUri,
                category = preview?.category,
                modifier = Modifier.size(160.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(Spacing.betweenCards))
            Text(
                meal.recipeName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            if (preview != null) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (preview.totalTimeMinutes > 0) MetaPill(Icons.Outlined.Schedule, "${preview.totalTimeMinutes} мин")
                    MetaPill(Icons.Outlined.Group, MkPlural.servings(preview.servings))
                    MetaPill(preview.category.icon(), preview.category?.displayName ?: "Без категорија")
                }
                preview.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(Spacing.betweenCards))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Same readiness colours as the Recipes grid.
                val coverageColor = when (preview.readiness) {
                    Readiness.CAN_COOK_NOW -> MaterialTheme.colorScheme.primary
                    Readiness.ALMOST_READY -> MojRucekTheme.colors.darkOlive
                    Readiness.NEEDS_SHOPPING -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    "Покриени намирници: ${preview.coveredIngredients} од ${preview.totalIngredients}",
                    style = MaterialTheme.typography.labelLarge,
                    color = coverageColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** One tall day pill: today = green with a darkOlive outline, planned = card fill, empty = greenContainer. */
@Composable
private fun DayPill(day: WeekDayUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MojRucekTheme.colors
    val meal = day.meal
    val fill = when {
        day.isToday -> colors.green
        meal != null -> colors.card
        else -> colors.greenContainer
    }
    val textColor = if (day.isToday) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
    val description = MacedonianDayNames[day.dayOfWeek - 1] + (meal?.let { ": ${it.recipeName}" } ?: "")
    Surface(
        onClick = onClick,
        modifier = modifier.height(96.dp).semantics { contentDescription = description },
        shape = PillShape,
        color = fill,
        contentColor = textColor,
        border = if (day.isToday) BorderStroke(2.dp, colors.darkOlive) else null
    ) {
        Column(
            // The pill's own description ("Четврток: Тавче гравче") replaces the short label and name for TalkBack.
            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 12.dp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                shortDayNames[day.dayOfWeek - 1],
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (meal != null) {
                    Text(
                        meal.recipeName,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        "+",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (day.isToday) textColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KitchenOverview(
    lowStockCount: Int,
    shoppingCount: Int,
    onOpenLowStock: () -> Unit,
    onOpenShopping: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Кујнски преглед")
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
        ) {
            OverviewCard(
                icon = Icons.Outlined.Inventory2,
                count = lowStockCount,
                label = "Намирници при крај",
                onClick = onOpenLowStock,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            OverviewCard(
                icon = Icons.Outlined.ShoppingBasket,
                count = shoppingCount,
                label = "За купување",
                onClick = onOpenShopping,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun WeeklyReminderCard(nextWeekPlanned: Int, onClick: () -> Unit) {
    val message = when {
        nextWeekPlanned <= 0 -> "Време е да го испланираш ручекот за следната недела."
        nextWeekPlanned < 7 -> "Следната недела има испланирани $nextWeekPlanned од 7 ручеци."
        else -> "Ручекот за следната недела е целосно испланиран."
    }
    val action = if (nextWeekPlanned >= 7) "Прегледај ја следната недела →" else "Планирај ја следната недела →"
    SectionCard(fill = SectionCardFill.GreenContainer, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            CategoryIconCircle(Icons.Outlined.CalendarMonth)
            Spacer(Modifier.width(Spacing.rowPadding))
            Column(Modifier.weight(1f)) {
                Text("Неделен потсетник", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(action, onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}

/** Card-colour SectionCard: line icon in a green circle, the count large, the label as a caption. */
@Composable
private fun OverviewCard(
    icon: ImageVector,
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, onClick = onClick) {
        CategoryIconCircle(icon)
        Spacer(Modifier.height(12.dp))
        Text(count.toString(), style = MaterialTheme.typography.headlineLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerDialog(
    recipes: List<RecipeWithIngredients>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClear: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredRecipes = remember(recipes, query) {
        val matches = if (query.isBlank()) {
            recipes
        } else {
            val needle = MacedonianSearch.normalizeForSearch(query.trim())
            recipes.filter { MacedonianSearch.normalizeForSearch(it.recipe.name).contains(needle) }
        }
        matches.sortedByDescending { it.recipe.isFavorite }
    }
    val favorites = filteredRecipes.filter { it.recipe.isFavorite }
    val others = filteredRecipes.filterNot { it.recipe.isFavorite }

    // Footer: "clear day" as the outline (clear) action, "close" as the primary one. The sheet's
    // close button, swipe-down, outside tap and back all call onDismiss, like the old dialog.
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "Избери рецепт за денес",
        closeContentDescription = "Затвори",
        secondaryActionLabel = "Испразни ден",
        onSecondaryAction = onClear,
        primaryActionLabel = "Затвори",
        onPrimaryAction = onDismiss
    ) {
        PillSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Пребарај рецепт",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.rowPadding))
        if (filteredRecipes.isEmpty()) {
            Box(modifier = Modifier.height(360.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Нема пронајдени рецепти", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (favorites.isNotEmpty() && others.isNotEmpty()) {
                    item {
                        Text(
                            "Омилени",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                    }
                }
                items(favorites, key = { it.recipe.id }) { r ->
                    RecipePickerRow(r, onPick)
                }
                if (favorites.isNotEmpty() && others.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant) }
                }
                items(others, key = { it.recipe.id }) { r ->
                    RecipePickerRow(r, onPick)
                }
            }
        }
    }
}

@Composable
private fun RecipePickerRow(recipe: RecipeWithIngredients, onPick: (Long) -> Unit) {
    val r = recipe.recipe
    PillListRow(
        title = r.name,
        subtitle = recipeMetaLine(r.category, r.servings, r.totalTimeMinutes),
        onClick = { onPick(r.id) },
        // Small circular photo, or the category icon when the recipe has none.
        leading = { RecipeThumb(r.imageUri, r.category, modifier = Modifier.padding(start = 4.dp)) },
        trailing = if (r.isFavorite) {
            {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Омилен рецепт",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp).size(20.dp)
                )
            }
        } else null
    )
}
