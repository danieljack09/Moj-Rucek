package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape

/**
 * One row of a settings group (the reference's Dark mode / Notes / Comments list): a bodyLarge
 * [title], an optional bodySmall [caption] and a [trailing] control (switch, stepper, chevron or
 * value). At least 56dp tall. [onClick] makes the whole row clickable.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
            )
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Thin divider between rows of a settings group. */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Pill stepper: minus circle, the [valueText], plus circle, on a background-colour pill (for use
 * on greenContainer). The two descriptions are what TalkBack reads for the buttons.
 */
@Composable
fun PillStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    valueWidth: Dp = 28.dp
) {
    Row(
        modifier.clip(PillShape).background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            Icons.Outlined.Remove,
            contentDescription = decreaseDescription,
            onClick = onDecrease,
            containerColor = Color.Transparent,
            circleSize = 32.dp
        )
        Text(
            valueText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(valueWidth)
        )
        CircleIconButton(
            Icons.Outlined.Add,
            contentDescription = increaseDescription,
            onClick = onIncrease,
            containerColor = Color.Transparent,
            circleSize = 32.dp
        )
    }
}

/** A line icon in darkOlive on a 40dp background-colour circle: the leading badge of a settings PillListRow. */
@Composable
fun RowIconCircle(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MojRucekTheme.colors.darkOlive) {
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
