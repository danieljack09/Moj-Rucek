package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme

/**
 * Spec item 3: a 40dp greenContainer circle with a 20dp darkOlive icon, centred
 * in a 48dp touch target. [containerColor] and [iconTint] can be overridden, e.g.
 * a background-coloured circle over a recipe image well, or an accent heart.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MojRucekTheme.colors.greenContainer,
    iconTint: Color = MojRucekTheme.colors.darkOlive,
    circleSize: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(containerColor)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(circleSize / 2)
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun CircleIconButtonPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад", onClick = {})
            CircleIconButton(Icons.Outlined.Add, contentDescription = "Додади", onClick = {})
            CircleIconButton(Icons.Outlined.Settings, contentDescription = "Поставки", onClick = {})
            CircleIconButton(Icons.Outlined.Settings, contentDescription = "Поставки", onClick = {}, enabled = false)
        }
    }
}
