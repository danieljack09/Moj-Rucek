package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape

/** How a selected [AppChip] looks. */
enum class ChipSelectionStyle {
    /** Multi-select filters: 1dp darkOlive outline on the greenContainer pill. */
    Outlined,

    /** Single choice or tabs: green fill. */
    Filled
}

/**
 * Spec item 6: a 32dp greenContainer pill with a labelMedium label, centred in a
 * 48dp-tall touch target. A selected chip shows [selectedStyle].
 */
@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedStyle: ChipSelectionStyle = ChipSelectionStyle.Outlined,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MojRucekTheme.colors
    val filled = selected && selectedStyle == ChipSelectionStyle.Filled
    val outlined = selected && selectedStyle == ChipSelectionStyle.Outlined
    val contentColor = when {
        filled -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }.let { if (enabled) it else it.copy(alpha = 0.38f) }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = if (selectedStyle == ChipSelectionStyle.Filled) Role.RadioButton else Role.Checkbox,
                interactionSource = interactionSource,
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(PillShape)
                .background(if (filled) colors.green else colors.greenContainer)
                .then(if (outlined) Modifier.border(1.dp, colors.darkOlive, PillShape) else Modifier)
                .indication(interactionSource, ripple())
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
        }
    }
}

/**
 * Horizontally scrolling row of chips, 8dp apart. [contentPadding] defaults to the
 * screen edge padding so the row can run edge to edge while the first chip lines up
 * with the screen content.
 */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@LightDarkPreviews
@Composable
private fun AppChipPreview() {
    PreviewSurface {
        Column {
            var single by remember { mutableStateOf("Сите") }
            ChipRow(contentPadding = PaddingValues(0.dp)) {
                listOf("Сите", "Можам да зготвам сега", "Омилени").forEach {
                    AppChip(it, selected = it == single, onClick = { single = it }, selectedStyle = ChipSelectionStyle.Filled)
                }
            }
            var multi by remember { mutableStateOf(setOf("Пилешко")) }
            ChipRow(contentPadding = PaddingValues(0.dp)) {
                listOf("Пилешко", "Паста", "Риба", "Без месо").forEach { c ->
                    AppChip(c, selected = c in multi, onClick = { multi = if (c in multi) multi - c else multi + c })
                }
            }
            ChipRow(contentPadding = PaddingValues(0.dp)) {
                AppChip("До 30 мин", selected = false, onClick = {}, leadingIcon = Icons.Outlined.Schedule)
                AppChip("До 60 мин", selected = false, onClick = {}, enabled = false)
            }
        }
    }
}
