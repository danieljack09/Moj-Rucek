package com.mojrucek.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.Spacing

/** Fill of a [SectionCard]. */
enum class SectionCardFill {
    /** Spec "card" colour: the default section card. */
    Card,

    /** Spec "greenContainer": rows, reminders and quieter blocks. */
    GreenContainer,

    /** Spec "green": a highlighted card (e.g. today in the planner); content uses onGreen. */
    Green
}

/**
 * The standard card: 20dp corners (shapes.large), [fill] colour, no elevation,
 * no outline and 20dp content padding. Pass [onClick] to make the whole card
 * clickable and [border] to add an outline (e.g. to highlight today).
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    fill: SectionCardFill = SectionCardFill.Card,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val color = when (fill) {
        SectionCardFill.Card -> MojRucekTheme.colors.card
        SectionCardFill.GreenContainer -> MojRucekTheme.colors.greenContainer
        SectionCardFill.Green -> MojRucekTheme.colors.green
    }
    val contentColor = if (fill == SectionCardFill.Green) MaterialTheme.colorScheme.onSecondary
    else MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.large
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = body
        )
    }
}

@LightDarkPreviews
@Composable
private fun SectionCardPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
            SectionCard {
                Text("Оваа недела", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Испланирани се 3 од 7 ручеци.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard(fill = SectionCardFill.GreenContainer, onClick = {}) {
                Text("Неделен потсетник", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
