package com.mojrucek.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape

/** Content alpha for disabled secondary, outline and text buttons. */
private const val DISABLED_ALPHA = 0.38f

/** Height of the filled and outlined pill buttons (spec: 52dp). */
private val PillButtonHeight = 52.dp

/**
 * Main action: accent pill, 52dp, onAccent labelLarge. Disabled = accentSoft fill.
 * While [loading] the button keeps its accent fill, ignores clicks and shows a
 * spinner in place of the label (the label still sets the width and is read by TalkBack).
 * Width is up to the caller: pass Modifier.fillMaxWidth() for a full-width button.
 */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    PillButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        container = if (enabled) colors.primary else MojRucekTheme.colors.accentSoft,
        content = colors.onPrimary,
        border = null
    )
}

/** Quieter action: greenContainer pill with an accent label. Same size as [PrimaryPillButton]. */
@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    PillButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        container = MojRucekTheme.colors.greenContainer,
        content = if (enabled) accent else accent.copy(alpha = DISABLED_ALPHA),
        border = null
    )
}

/**
 * Transparent pill with a 1dp accent border and an accent label, e.g. "clear" or "cancel".
 * [maxLines] can be raised for a long label that must not be cut off.
 */
@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    maxLines: Int = 2
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA)
    PillButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        container = Color.Transparent,
        content = accent,
        border = BorderStroke(1.dp, accent),
        maxLines = maxLines
    )
}

/**
 * Text link: accent labelLarge with no fill, in a 48dp-tall touch target.
 * [color] overrides the accent where accent text would lack contrast (e.g. on a green promo card).
 */
@Composable
fun PillTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val accent = color
    PillButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        container = Color.Transparent,
        content = if (enabled) accent else accent.copy(alpha = DISABLED_ALPHA),
        border = null,
        minHeight = 48.dp,
        contentPadding = PaddingValues(horizontal = 12.dp)
    )
}

@Composable
private fun PillButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    icon: ImageVector?,
    container: Color,
    content: Color,
    border: BorderStroke?,
    minHeight: Dp = PillButtonHeight,
    // Vertical padding only matters for multi-line labels; one line already fits the 52dp minimum.
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
    maxLines: Int = 2
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled && !loading,
        shape = PillShape,
        color = container,
        contentColor = content,
        border = border
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.alpha(if (loading) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = content,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun PillButtonsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryPillButton("Го зготвив ова", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryPillButton("Го зготвив ова", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            PrimaryPillButton("Го зготвив ова", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
            SecondaryPillButton("Додади намирница", onClick = {}, icon = Icons.Outlined.ShoppingCart, modifier = Modifier.fillMaxWidth())
            SecondaryPillButton("Додади намирница", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinePillButton("Откажи", onClick = {}, modifier = Modifier.weight(1f))
                PrimaryPillButton("Зачувај", onClick = {}, modifier = Modifier.weight(1f))
            }
            OutlinePillButton("Откажи", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            OutlinePillButton("Откажи", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillTextButton("Откажи", onClick = {})
                PillTextButton("Откажи", onClick = {}, enabled = false)
                PillTextButton("Откажи", onClick = {}, loading = true)
            }
        }
    }
}
