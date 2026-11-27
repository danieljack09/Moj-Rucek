package com.mojrucek.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.ui.recipes.RecipeCover

/** Width behaviour of a [RecipeCard]. */
enum class RecipeCardSize {
    /** Fills the width it is given, e.g. one column of a two-column grid. */
    Grid,

    /** Fixed ~170dp width for a horizontal carousel. */
    Carousel
}

/** Width of a [RecipeCardSize.Carousel] card. */
val RecipeCardCarouselWidth = 170.dp

/**
 * Spec item 7: a square image well ([RecipeCover]: circular photo, or the category
 * icon on a green circle), a heart [CircleIconButton] top right, [meta] pills bottom
 * left and the titleMedium [title] (max 2 lines) below the well.
 *
 * An optional [details] slot sits under the title for extra lines (e.g. category, readiness).
 *
 * The heart shows only when [onToggleFavorite] is given: accent outline heart, filled
 * when [isFavorite]. TalkBack reads it as [favoriteContentDescription] with an on/off state.
 */
@Composable
fun RecipeCard(
    title: String,
    imageUri: String?,
    category: RecipeCategory?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: RecipeCardSize = RecipeCardSize.Grid,
    meta: List<MetaInfo> = emptyList(),
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    favoriteContentDescription: String = "Омилен рецепт",
    details: (@Composable ColumnScope.() -> Unit)? = null
) {
    val widthModifier = when (size) {
        RecipeCardSize.Grid -> Modifier.fillMaxWidth()
        RecipeCardSize.Carousel -> Modifier.width(RecipeCardCarouselWidth)
    }
    Column(
        modifier = modifier
            .then(widthModifier)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        RecipeCover(imageUri = imageUri, category = category, modifier = Modifier.fillMaxWidth()) {
            if (onToggleFavorite != null) {
                CircleIconButton(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = favoriteContentDescription,
                    onClick = onToggleFavorite,
                    containerColor = MaterialTheme.colorScheme.background,
                    iconTint = MaterialTheme.colorScheme.primary,
                    circleSize = 32.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .semantics { toggleableState = ToggleableState(isFavorite) }
                )
            }
            if (meta.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    meta.forEach { MetaPill(it, overImage = true) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (details != null) {
            Column(Modifier.padding(horizontal = 4.dp), content = details)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@LightDarkPreviews
@Composable
private fun RecipeCardPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RecipeCard(
                    title = "Спагети болоњезе",
                    imageUri = null,
                    category = RecipeCategory.PASTA,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    meta = listOf(MetaInfo(Icons.Outlined.Schedule, "40 мин")),
                    isFavorite = true,
                    onToggleFavorite = {}
                )
                RecipeCard(
                    title = "Тавче гравче со многу долго име што се прекинува",
                    imageUri = null,
                    category = RecipeCategory.GRAV_I_LEKJA,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    meta = listOf(MetaInfo(Icons.Outlined.Schedule, "110 мин")),
                    onToggleFavorite = {}
                )
            }
            RecipeCard(
                title = "Пилешко со ориз",
                imageUri = null,
                category = RecipeCategory.PILESKO,
                onClick = {},
                size = RecipeCardSize.Carousel,
                meta = listOf(MetaInfo(Icons.Outlined.Schedule, "55 мин"), MetaInfo(Icons.Outlined.Restaurant, "3"))
            )
        }
    }
}
