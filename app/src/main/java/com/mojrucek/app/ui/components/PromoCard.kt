package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.Spacing

/**
 * Spec item 8: a green card (20dp corners) with a titleLarge [title], a bodySmall
 * [subtitle] and an accent [PrimaryPillButton]. The optional [image] sits at the
 * right, pushed [imageBleed] past the edge so the card clips it. Text keeps to
 * [textWidthFraction] of the width when an image is present. An optional
 * [secondaryButtonLabel] adds a text link in the on-green colour under the button.
 */
@Composable
fun PromoCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    buttonLabel: String? = null,
    onButtonClick: (() -> Unit)? = null,
    secondaryButtonLabel: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null,
    textWidthFraction: Float = 0.6f,
    imageBleed: Dp = 28.dp,
    image: (@Composable BoxScope.() -> Unit)? = null
) {
    val onGreen = MaterialTheme.colorScheme.onSecondary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MojRucekTheme.colors.green)
    ) {
        if (image != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = imageBleed),
                contentAlignment = Alignment.Center,
                content = image
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (image != null) textWidthFraction else 1f)
                .padding(Spacing.cardPadding)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = onGreen, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onGreen, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (buttonLabel != null && onButtonClick != null) {
                Spacer(Modifier.height(16.dp))
                PrimaryPillButton(buttonLabel, onClick = onButtonClick)
            }
            if (secondaryButtonLabel != null && onSecondaryButtonClick != null) {
                Spacer(Modifier.height(4.dp))
                // Shifted left by the link's own 12dp padding so its text lines up with the title.
                PillTextButton(
                    secondaryButtonLabel,
                    onClick = onSecondaryButtonClick,
                    color = onGreen,
                    modifier = Modifier.offset(x = (-12).dp)
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun PromoCardPreview() {
    PreviewSurface {
        PromoCard(
            title = "Денешен ручек",
            subtitle = "Нема избрано јадење за денес.",
            buttonLabel = "Избери јадење",
            onButtonClick = {}
        ) {
            Icon(
                Icons.Outlined.RamenDining,
                contentDescription = null,
                tint = MojRucekTheme.colors.darkOlive,
                modifier = Modifier.size(140.dp)
            )
        }
    }
}
