package com.mojrucek.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape

/** One short fact shown as a [MetaPill], e.g. a clock icon with "20 мин". */
@Immutable
data class MetaInfo(val icon: ImageVector, val text: String)

/**
 * Spec item 13: a 24dp pill with a 14dp accent icon and labelSmall text.
 * [overImage] = true uses the background colour at 90% (for pills laid over an
 * image well); false uses greenContainer (for pills on the cream background).
 * [containerColor], [contentColor] and [iconTint] override those (e.g. a status pill).
 */
@Composable
fun MetaPill(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    overImage: Boolean = false,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    maxLines: Int = 2
) {
    val fill = when {
        containerColor != Color.Unspecified -> containerColor
        overImage -> MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
        else -> MojRucekTheme.colors.greenContainer
    }
    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(PillShape)
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (contentColor != Color.Unspecified) contentColor else MaterialTheme.colorScheme.onSurface,
            // Wraps rather than clipping on narrow screens or large text (never an ellipsis).
            maxLines = maxLines
        )
    }
}

/** [MetaPill] for a [MetaInfo]. */
@Composable
fun MetaPill(info: MetaInfo, modifier: Modifier = Modifier, overImage: Boolean = false) =
    MetaPill(info.icon, info.text, modifier, overImage)

@LightDarkPreviews
@Composable
private fun MetaPillPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaPill(Icons.Outlined.Schedule, "20 мин")
            MetaPill(Icons.Outlined.Restaurant, "3 порции")
            Box(Modifier.background(MojRucekTheme.colors.card).padding(8.dp)) {
                MetaPill(Icons.Outlined.Schedule, "45 мин", overImage = true)
            }
        }
    }
}
