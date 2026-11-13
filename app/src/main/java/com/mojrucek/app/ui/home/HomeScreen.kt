package com.mojrucek.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mojrucek.app.data.relation.RecipeWithIngredients
import com.mojrucek.app.domain.util.MacedonianSearch

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мој Ручек") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Поставки")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Што ќе готвиме денес?",
                    style = MaterialTheme.typography.titleLarge
                )
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

@Composable
private fun TodayLunchCard(today: TodayMealUi?, onChangeClick: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🍽️ Денешен ручек", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (today == null) {
                Text("Нема избрано јадење за денес.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(today.recipeName, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChangeClick) {
                    Text(if (today == null) "Избери јадење" else "Промени")
                }
                if (today != null) {
                    TextButton(onClick = { onOpenRecipe(today.recipeId) }) {
                        Text("Отвори рецепт")
                    }
                }
            }
        }
    }
}

private val shortDayNames = listOf("ПОН", "ВТО", "СРЕ", "ЧЕТ", "ПЕТ", "САБ", "НЕД")

@Composable
private fun WeekPreview(
    days: List<WeekDayUi>,
    onOpenRecipe: (Long) -> Unit,
    onOpenPlanner: () -> Unit
) {
    val sectionBg = Color(0xFFE8E2EB)
    val plannedBg = Color(0xFFD6E3D2)
    val emptyBg = Color(0xFFF8F5ED)
    val todayBg = Color(0xFFDDE6D6)
    val todayText = Color(0xFF344B3B)
    val mainText = Color(0xFF35342F)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = sectionBg, contentColor = mainText)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Оваа недела", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                days.forEach { day ->
                    val meal = day.meal
                    val bg = when {
                        day.isToday -> todayBg
                        meal != null -> plannedBg
                        else -> emptyBg
                    }
                    val textColor = if (day.isToday) todayText else mainText
                    Card(
                        onClick = { if (meal != null) onOpenRecipe(meal.recipeId) else onOpenPlanner() },
                        modifier = Modifier.weight(1f).height(76.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = bg, contentColor = textColor),
                        border = if (day.isToday) BorderStroke(2.dp, todayText) else null
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                shortDayNames[day.dayOfWeek - 1],
                                fontSize = 10.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            if (meal != null) {
                                Text(
                                    meal.recipeName,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Text("+", fontSize = 16.sp, color = textColor.copy(alpha = 0.55f))
                                }
                            }
                        }
                    }
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
        Text("Кујнски преглед", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard(
                icon = Icons.Filled.Inventory2,
                count = lowStockCount,
                label = "Намирници при крај",
                background = Color(0xFFDDE6D6),
                textColor = Color(0xFF344B3B),
                onClick = onOpenLowStock,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            OverviewCard(
                icon = Icons.Filled.ShoppingCart,
                count = shoppingCount,
                label = "За купување",
                background = Color(0xFFE8E2EB),
                textColor = Color(0xFF35342F),
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E5C9), contentColor = Color(0xFF67542F))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Неделен потсетник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OverviewCard(
    icon: ImageVector,
    count: Int,
    label: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background, contentColor = textColor)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Избери рецепт за денес") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Пребарај рецепт") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (filteredRecipes.isEmpty()) {
                    Box(modifier = Modifier.height(360.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Нема пронајдени рецепти")
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
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
                            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                        }
                        items(others, key = { it.recipe.id }) { r ->
                            RecipePickerRow(r, onPick)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Затвори") }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("Испразни ден") }
        }
    )
}

@Composable
private fun RecipePickerRow(recipe: RecipeWithIngredients, onPick: (Long) -> Unit) {
    TextButton(onClick = { onPick(recipe.recipe.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (recipe.recipe.isFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Омилен рецепт",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(recipe.recipe.name, modifier = Modifier.fillMaxWidth())
        }
    }
}
