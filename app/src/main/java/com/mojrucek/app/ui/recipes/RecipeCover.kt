package com.mojrucek.app.ui.recipes

import com.mojrucek.app.ui.components.CategoryIconCircle
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.ui.components.LightDarkPreviews
import com.mojrucek.app.ui.components.icon
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.domain.text.MkPlural

/** Share of the well the circular dish photo fills (spec item 7: about 85%). */
private const val PHOTO_FRACTION = 0.85f

/** Share of the well the no-photo fallback circle fills. */
private const val FALLBACK_FRACTION = 0.62f

/**
 * Recipe image well (spec item 7): a square card-colour well with 20dp corners.
 * A dish photo goes in a circle filling about 85% of the well; without a photo the
 * well shows the [category] icon on a green circle.
 *
 * The app has no image-loading library. The only [imageUri] scheme loaded is
 * `file:///android_asset/...` (bundled assets, e.g. the debug sample recipes), decoded
 * off the main thread with the platform's BitmapFactory. Any other URI, a missing asset
 * or a decoding error shows the fallback. [content] receives whether a photo is shown and
 * draws on top of the well (heart button, meta pills).
 */
@Composable
fun RecipeCover(
    imageUri: String?,
    category: RecipeCategory?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(showsImage: Boolean) -> Unit = {}
) {
    val photo = rememberAssetPhoto(imageUri)
    val showsImage = photo != null
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(MojRucekTheme.colors.card),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(PHOTO_FRACTION).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize(FALLBACK_FRACTION)
                    .clip(CircleShape)
                    .background(MojRucekTheme.colors.green),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    category.icon(),
                    contentDescription = null,
                    tint = MojRucekTheme.colors.iconOnGreen,
                    modifier = Modifier.fillMaxSize(0.45f)
                )
            }
        }
        content(showsImage)
    }
}

/**
 * Small round recipe image for list rows (the recipe pickers): the photo when [imageUri] is a
 * loadable asset (same rule as [RecipeCover]), otherwise the [category] icon on a green circle.
 */
@Composable
fun RecipeThumb(imageUri: String?, category: RecipeCategory?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val photo = rememberAssetPhoto(imageUri)
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        CategoryIconCircle(category.icon(), modifier = modifier, size = size)
    }
}

private const val ASSET_PREFIX = "file:///android_asset/"

/** Longest side a photo is decoded to; the largest well (recipe detail hero) is about 70% of the screen width. */
private const val MAX_DECODE_PX = 1080

/**
 * Loads a `file:///android_asset/` photo in the background; null for any other URI (or none),
 * while loading, or when the asset cannot be read. Other schemes are deliberately not handled.
 */
@Composable
private fun rememberAssetPhoto(imageUri: String?): ImageBitmap? {
    val assetPath = imageUri?.takeIf { it.startsWith(ASSET_PREFIX) }?.removePrefix(ASSET_PREFIX) ?: return null
    val assets = LocalContext.current.applicationContext.assets
    val photo by produceState<ImageBitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_PX) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return photo
}

internal fun servingsLabel(servings: Int): String =
    MkPlural.servings(servings)

/** "Category · N порции[ · M мин]" line used under the recipe name. */
internal fun recipeMetaLine(category: RecipeCategory?, servings: Int, totalTimeMinutes: Int): String = buildString {
    append(category?.displayName ?: "Без категорија")
    if (servings > 0) append(" · ${servingsLabel(servings)}")
    if (totalTimeMinutes > 0) append(" · $totalTimeMinutes мин")
}

@LightDarkPreviews
@Composable
private fun RecipeCoverPreview() {
    MojRucekTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            RecipeCover(imageUri = null, category = RecipeCategory.PASTA, modifier = Modifier.width(170.dp))
        }
    }
}
