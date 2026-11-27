package com.mojrucek.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Top of a screen, replacing TopAppBar: an optional back [CircleIconButton] on the
 * left, a headlineLarge [title] (up to two lines) and up to three trailing
 * CircleIconButtons in [actions]. [actions] is a slot rather than a list so a screen
 * can anchor a dropdown menu to one of its buttons.
 * The caller adds the screen's horizontal padding and the status-bar inset.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Назад",
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            CircleIconButton(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = backContentDescription,
                onClick = onBack
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@LightDarkPreviews
@Composable
private fun ScreenHeaderPreview() {
    PreviewSurface {
        Column {
            ScreenHeader(title = "Рецепти") {
                CircleIconButton(Icons.Outlined.Add, contentDescription = "Додади рецепт", onClick = {})
                CircleIconButton(Icons.Outlined.Settings, contentDescription = "Поставки", onClick = {})
            }
            ScreenHeader(title = "Пилешко со ориз", onBack = {}) {
                CircleIconButton(Icons.Outlined.Edit, contentDescription = "Уреди", onClick = {})
            }
        }
    }
}
