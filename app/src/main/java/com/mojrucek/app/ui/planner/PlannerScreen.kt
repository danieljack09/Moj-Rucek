package com.mojrucek.app.ui.planner

import com.mojrucek.app.ui.recipes.RecipeThumb
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.usecase.WeekCalculator
import com.mojrucek.app.ui.components.AppBottomSheet
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.CategoryIconCircle
import com.mojrucek.app.ui.components.CircleIconButton
import com.mojrucek.app.ui.components.MetaPill
import com.mojrucek.app.ui.components.PillListRow
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.components.SecondaryPillButton
import com.mojrucek.app.ui.components.SectionCard
import com.mojrucek.app.ui.components.SectionCardFill
import com.mojrucek.app.ui.components.icon
import com.mojrucek.app.ui.components.tabListBottomPadding
import com.mojrucek.app.ui.recipes.recipeMetaLine
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.mojrucek.app.domain.text.MkPlural

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(viewModel: PlannerViewModel, onOpenRecipe: (Long) -> Unit, onOpenSettings: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    var dayBeingEdited by remember { mutableStateOf<Int?>(null) }
    var pickerRecipes by remember { mutableStateOf<List<RecipeWithIngredients>>(emptyList()) }

    // No top bar: the Scaffold's top padding is the status-bar inset, and the header sits below it.
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ScreenHeader(
                title = "Планер",
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
            )
            WeekHeader(
                weekStart = state.weekStart,
                onPrev = viewModel::goToPreviousWeek,
                onNext = viewModel::goToNextWeek,
                onToday = viewModel::goToThisWeek
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.screenHorizontal,
                    top = 8.dp,
                    end = Spacing.screenHorizontal,
                    bottom = Spacing.betweenCards
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HouseholdServingsRow(
                        servings = state.householdServings,
                        onChange = viewModel::setHouseholdServings
                    )
                }
                if (state.incompleteDays.isNotEmpty()) {
                    item {
                        Text(
                            "Сè уште нема јадење за: " + state.incompleteDays.joinToString(", ") { dayName(it) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                items(state.days, key = { it.dayOfWeek }) { day ->
                    DayCard(
                        day = day,
                        onPick = {
                            viewModel.recipesForPicker { pickerRecipes = it }
                            dayBeingEdited = day.dayOfWeek
                        },
                        onOpenRecipe = { day.recipeId?.let(onOpenRecipe) }
                    )
                }
            }

            // Pinned footer, lifted so it sits above the floating nav bar.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screenHorizontal, top = 8.dp, end = Spacing.screenHorizontal, bottom = tabListBottomPadding(padding)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.nextListLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                PrimaryPillButton(
                    if (state.shoppingListGenerated) "Регенерирај листа за пазарење" else "Генерирај листа за пазарење",
                    onClick = viewModel::generateShoppingList,
                    enabled = !state.generating,
                    icon = Icons.Outlined.ShoppingCart,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    dayBeingEdited?.let { day ->
        DayRecipePickerDialog(
            recipes = pickerRecipes,
            currentDays = state.days,
            onDismiss = { dayBeingEdited = null },
            onPick = { recipeId -> viewModel.setMeal(day, recipeId); dayBeingEdited = null },
            onClear = { viewModel.setMeal(day, null); dayBeingEdited = null },
            onCopyFrom = { fromDay -> viewModel.copyMeal(fromDay, day); dayBeingEdited = null }
        )
    }

    state.limitNotice?.let { notice ->
        if (notice.linkGoogle) {
            AppDialog(
                onDismissRequest = viewModel::dismissLimitNotice,
                title = notice.title,
                text = notice.text,
                confirmLabel = "Поврзи со Google",
                onConfirm = { viewModel.dismissLimitNotice(); onOpenSettings() },
                dismissLabel = "Подоцна",
                onDismiss = viewModel::dismissLimitNotice
            )
        } else {
            AppDialog(
                onDismissRequest = viewModel::dismissLimitNotice,
                title = notice.title,
                text = notice.text,
                confirmLabel = "Во ред",
                onConfirm = viewModel::dismissLimitNotice
            )
        }
    }

    state.lastGenerationResult?.let { result ->
        AppDialog(
            onDismissRequest = viewModel::clearGenerationResult,
            title = "Листата за пазарење е подготвена",
            text = "${MkPlural.word(result.items.size, "Додадена е", "Додадени се")} ${MkPlural.items(result.items.size)}.",
            confirmLabel = "Во ред",
            onConfirm = viewModel::clearGenerationResult
        ) {
            if (result.conflicts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Внимание — овие намирници имаат несовпаѓање на единици и мора рачно да се проверат:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    result.conflicts.forEach { c ->
                        Text(
                            "• ${c.name}: " + c.conflictingUnits.joinToString(", ") { it.displayName },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Previous / week range / next. Tapping the range returns to this week, as before.
 */
@Composable
private fun WeekHeader(weekStart: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal - 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Претходна недела", onClick = onPrev)
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(PillShape)
                .clickable(role = Role.Button, onClick = onToday),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${weekStart.format(formatter)} — ${weekStart.plusDays(6).format(formatter)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                // Two lines rather than clipping with large text.
                maxLines = 2
            )
        }
        CircleIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Следна недела", onClick = onNext)
    }
}

/** greenContainer pill: people icon, the label, and a minus / number / plus stepper on the right. */
@Composable
private fun HouseholdServingsRow(servings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(PillShape)
            .background(MojRucekTheme.colors.greenContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Group, contentDescription = null, tint = MojRucekTheme.colors.darkOlive, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.rowPadding))
        Text(
            "Порции за домаќинството:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(
            Modifier.clip(PillShape).background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                Icons.Outlined.Remove,
                contentDescription = "Намали",
                onClick = { onChange((servings - 1).coerceAtLeast(1)) },
                containerColor = Color.Transparent,
                circleSize = 32.dp
            )
            Text(
                servings.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(20.dp)
            )
            CircleIconButton(
                Icons.Outlined.Add,
                contentDescription = "Зголеми",
                onClick = { onChange(servings + 1) },
                containerColor = Color.Transparent,
                circleSize = 32.dp
            )
        }
    }
}

/**
 * One day. Planned: card fill, the day label with a "Промени" circle, and a recipe tile (dish
 * circle, name, servings and readiness pills); tapping the card opens the recipe. Empty:
 * greenContainer fill with a centred "+" ("Избери") and the empty text; tapping opens the picker.
 * Today: green fill with a 1.5dp darkOlive outline.
 */
@Composable
private fun DayCard(day: DayPlanUi, onPick: () -> Unit, onOpenRecipe: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM") }
    val isToday = day.date == WeekCalculator.today()
    val planned = day.recipeId != null
    val fill = when {
        isToday -> SectionCardFill.Green
        planned -> SectionCardFill.Card
        else -> SectionCardFill.GreenContainer
    }
    // Circles and pills sit on the card: background colour on green/greenContainer, greenContainer on card.
    val onCardContainer = if (fill == SectionCardFill.Card) MojRucekTheme.colors.greenContainer else MaterialTheme.colorScheme.background
    SectionCard(
        fill = fill,
        onClick = if (planned) onOpenRecipe else onPick,
        border = if (isToday) BorderStroke(1.5.dp, MojRucekTheme.colors.darkOlive) else null,
        contentPadding = PaddingValues(start = Spacing.cardPadding, top = 12.dp, end = 8.dp, bottom = if (planned) Spacing.cardPadding else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stacked label: day name over the date.
            // At least 112dp so the cards line up, wider when large text needs it.
            Column(Modifier.widthIn(min = 112.dp)) {
                Text(dayName(day.dayOfWeek), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    day.date.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isToday) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (planned) {
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    Icons.Outlined.Edit,
                    contentDescription = "Промени",
                    onClick = onPick,
                    containerColor = onCardContainer
                )
            } else {
                Row(
                    Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircleIconButton(
                        Icons.Outlined.Add,
                        contentDescription = "Избери",
                        onClick = onPick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        iconTint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Нема избрано јадење",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isToday) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (planned) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                // DayPlanUi (PlannerViewModel) carries no photo or category: generic dish icon.
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(onCardContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        (null as RecipeCategory?).icon(),
                        contentDescription = null,
                        tint = MojRucekTheme.colors.darkOlive,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.rowPadding))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        day.recipeName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    ReadinessPills(day, onCardContainer)
                }
            }
        }
    }
}

/** Servings and readiness as MetaPills: green when it can be cooked now, accentSoft when items are missing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessPills(day: DayPlanUi, neutralContainer: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetaPill(
            Icons.Outlined.Group,
            MkPlural.servings(day.servings),
            containerColor = neutralContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconTint = MojRucekTheme.colors.darkOlive
        )
        if (day.canPrepareNow) {
            MetaPill(
                Icons.Outlined.CheckCircle,
                "Може да се подготви веднаш",
                containerColor = if (neutralContainer == MaterialTheme.colorScheme.background) MaterialTheme.colorScheme.background
                else MojRucekTheme.colors.green,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MojRucekTheme.colors.darkOlive
            )
        } else {
            MetaPill(
                Icons.Outlined.ShoppingCart,
                "${MkPlural.word(day.missingCount, "Недостига", "Недостигаат")} ${MkPlural.ingredients(day.missingCount)}",
                containerColor = MojRucekTheme.colors.accentSoft,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Same sheet style as Home's recipe picker, with this picker's own logic: "copy from another
 * day" buttons (only days with meals), the recipe rows (max 320dp), and "Испразни ден" /
 * "Затвори" in the footer. No search, as before.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayRecipePickerDialog(
    recipes: List<RecipeWithIngredients>,
    currentDays: List<DayPlanUi>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClear: () -> Unit,
    onCopyFrom: (Int) -> Unit
) {
    val daysWithMeals = currentDays.filter { it.recipeId != null }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "Избери рецепт",
        closeContentDescription = "Затвори",
        secondaryActionLabel = "Испразни ден",
        onSecondaryAction = onClear,
        primaryActionLabel = "Затвори",
        onPrimaryAction = onDismiss
    ) {
        if (daysWithMeals.isNotEmpty()) {
            Text(
                "Копирај од друг ден:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                daysWithMeals.forEach { d ->
                    SecondaryPillButton(dayName(d.dayOfWeek).take(3), onClick = { onCopyFrom(d.dayOfWeek) })
                }
            }
            Spacer(Modifier.height(Spacing.betweenCards))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recipes, key = { it.recipe.id }) { r ->
                PillListRow(
                    title = r.recipe.name,
                    subtitle = recipeMetaLine(r.recipe.category, r.recipe.servings, r.recipe.totalTimeMinutes),
                    onClick = { onPick(r.recipe.id) },
                    leading = { RecipeThumb(r.recipe.imageUri, r.recipe.category, modifier = Modifier.padding(start = 4.dp)) }
                )
            }
        }
    }
}
