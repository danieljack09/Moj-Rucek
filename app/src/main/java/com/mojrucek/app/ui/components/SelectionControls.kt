package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme

private val CheckboxShape = RoundedCornerShape(6.dp)

/**
 * Spec item 10: a 20dp rounded square with a 1.5dp darkOlive border; ticked = accent
 * fill with an onAccent check. With [onCheckedChange] it is its own 48dp toggle
 * target; pass null when the surrounding row handles the click (the row should then
 * carry the toggle semantics).
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MojRucekTheme.colors
    val accent = MaterialTheme.colorScheme.primary
    val toggleModifier = if (onCheckedChange != null) {
        Modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp)
            )
    } else Modifier
    Box(modifier.then(toggleModifier).alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CheckboxShape)
                .background(if (checked) accent else Color.Transparent)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, colors.darkOlive, CheckboxShape)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Colours for a Material [Switch] in the spec's language: checked track darkOlive
 * with a background-colour thumb; unchecked track greenContainer with a
 * textSecondary thumb and the outline colour as its border.
 */
@Composable
fun appSwitchColors(): SwitchColors {
    val colors = MojRucekTheme.colors
    val scheme = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = scheme.background,
        checkedTrackColor = colors.darkOlive,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = colors.darkOlive,
        uncheckedThumbColor = scheme.onSurfaceVariant,
        uncheckedTrackColor = colors.greenContainer,
        uncheckedBorderColor = scheme.outline,
        uncheckedIconColor = colors.greenContainer
    )
}

@LightDarkPreviews
@Composable
private fun SelectionControlsPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var a by remember { mutableStateOf(true) }
            var b by remember { mutableStateOf(false) }
            AppCheckbox(a, { a = it })
            AppCheckbox(b, { b = it })
            AppCheckbox(true, null, enabled = false)
            var s1 by remember { mutableStateOf(true) }
            var s2 by remember { mutableStateOf(false) }
            Switch(s1, { s1 = it }, colors = appSwitchColors())
            Switch(s2, { s2 = it }, colors = appSwitchColors())
        }
    }
}
