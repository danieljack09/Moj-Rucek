package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KebabDining
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.OutdoorGrill
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.ui.theme.MojRucekTheme

/**
 * Outlined Material icon for each ingredient category (spec item 14: line icons).
 * The UI shows these instead of the category's emoji; the emoji stays in the data
 * layer, untouched.
 */
fun IngredientCategory.icon(): ImageVector = when (this) {
    IngredientCategory.ZELENCUK -> Icons.Outlined.Eco
    IngredientCategory.OVOSJE -> Icons.Outlined.Spa
    IngredientCategory.MESO -> Icons.Outlined.OutdoorGrill
    IngredientCategory.MLECNI_PROIZVODI -> Icons.Outlined.LocalDrink
    IngredientCategory.ZITARICI_I_TESTENINI -> Icons.Outlined.Grass
    IngredientCategory.LEB_I_PEKARSKI_PROIZVODI -> Icons.Outlined.BakeryDining
    IngredientCategory.KONZERVIRANA_HRANA -> Icons.Outlined.Inventory2
    IngredientCategory.ZACINI -> Icons.Outlined.Whatshot
    IngredientCategory.MASLA_I_DODATOCI -> Icons.Outlined.WaterDrop
    IngredientCategory.ZAMRZNATA_HRANA -> Icons.Outlined.AcUnit
    IngredientCategory.JAJCA -> Icons.Outlined.Egg
    IngredientCategory.PIJALOCI -> Icons.Outlined.LocalCafe
    IngredientCategory.DRUGO -> Icons.Outlined.ShoppingBasket
}

/** Outlined icon for a recipe category; also the recipe card's no-photo fallback. */
fun RecipeCategory?.icon(): ImageVector = when (this) {
    RecipeCategory.PILESKO -> Icons.Outlined.KebabDining
    RecipeCategory.SVINSKO -> Icons.Outlined.OutdoorGrill
    RecipeCategory.TELESKO -> Icons.Outlined.DinnerDining
    RecipeCategory.MELENO_MESO -> Icons.Outlined.LunchDining
    RecipeCategory.RIBA -> Icons.Outlined.SetMeal
    RecipeCategory.PASTA -> Icons.Outlined.RamenDining
    RecipeCategory.ORIZ -> Icons.Outlined.RiceBowl
    RecipeCategory.KOMPIR -> Icons.Outlined.Agriculture
    RecipeCategory.GRAV_I_LEKJA -> Icons.Outlined.SoupKitchen
    RecipeCategory.BEZ_MESO -> Icons.Outlined.Eco
    RecipeCategory.DRUGI_JADENJA, null -> Icons.Outlined.Restaurant
}

/**
 * A category icon (iconOnGreen) on a green circle: the spec's fallback for a
 * recipe without a photo, also usable as a leading badge in list rows.
 */
@Composable
fun CategoryIconCircle(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MojRucekTheme.colors.green),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MojRucekTheme.colors.iconOnGreen, modifier = Modifier.size(size * 0.5f))
    }
}

@LightDarkPreviews
@Composable
private fun CategoryIconsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeCategory.entries.take(6).forEach { CategoryIconCircle(it.icon()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IngredientCategory.entries.take(6).forEach {
                    Icon(it.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
