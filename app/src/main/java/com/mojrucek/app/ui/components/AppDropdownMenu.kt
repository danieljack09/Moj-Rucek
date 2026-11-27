package com.mojrucek.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme

/** Corners shared by every menu in the app. */
val AppMenuShape = RoundedCornerShape(16.dp)

/** Thin divider-coloured outline that separates a flat menu from the cream screen behind it. */
@Composable
fun appMenuBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/**
 * The app's one menu style: 16dp corners, background colour, a thin divider outline and no
 * shadow (the spec allows a shadow only under the nav bar). Fill it with [AppDropdownMenuItem]s.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = AppMenuShape,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = appMenuBorder(),
        content = content
    )
}

/**
 * A 48dp menu row: optional darkOlive line icon, bodyLarge text, and an accent check when
 * [selected]. The [text] is also what TalkBack reads.
 */
@Composable
fun AppDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, tint = MojRucekTheme.colors.darkOlive) }
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null
    )
}
