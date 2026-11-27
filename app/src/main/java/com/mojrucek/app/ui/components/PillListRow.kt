package com.mojrucek.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape

/**
 * Spec item 10: a greenContainer row, 52dp minimum, with an optional [leading]
 * slot (checkbox, icon), a bodyLarge [title], an optional bodySmall [subtitle] and
 * an optional [trailing] slot (chevron, value, action). Fully rounded when it has
 * one line, 20dp corners when it has a subtitle. [onClick] makes the whole row clickable.
 * [supportingContent] adds extra content under the subtitle (e.g. a MetaPill).
 */
@Composable
fun PillListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleMaxLines: Int = 2,
    subtitleMaxLines: Int = 1,
    supportingContent: (@Composable () -> Unit)? = null
) {
    val shape = if (subtitle == null && supportingContent == null) PillShape else MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(MojRucekTheme.colors.greenContainer)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(
                start = if (leading != null) 4.dp else 20.dp,
                end = if (trailing != null) 4.dp else 20.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (supportingContent != null) {
                Spacer(Modifier.height(4.dp))
                supportingContent()
            }
        }
        if (trailing != null) trailing()
    }
}

/** Chevron for the trailing slot of a [PillListRow] that opens something. */
@Composable
fun RowChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MojRucekTheme.colors.darkOlive,
        modifier = Modifier.padding(horizontal = 12.dp).size(20.dp)
    )
}

@LightDarkPreviews
@Composable
private fun PillListRowPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var bought by remember { mutableStateOf(false) }
            PillListRow(
                title = "Пилешко филе",
                leading = { AppCheckbox(bought, { bought = it }) },
                trailing = {
                    Text("500 g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                }
            )
            PillListRow(
                title = "Домати",
                subtitle = "Зеленчук",
                leading = { CategoryIconCircle(IngredientCategory.ZELENCUK.icon(), modifier = Modifier.padding(start = 8.dp)) },
                trailing = { RowChevron() },
                onClick = {}
            )
            PillListRow(title = "Поставки", onClick = {}, trailing = { RowChevron() })
        }
    }
}
