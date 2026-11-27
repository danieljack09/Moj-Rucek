package com.mojrucek.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Spec item 12: a dialog on the background colour with 28dp corners
 * (shapes.extraLarge), a titleLarge [title], a bodyMedium [text] and/or custom
 * [content] (e.g. text fields), and pill actions: an optional [PillTextButton]
 * ([dismissLabel]) and a [PrimaryPillButton] ([confirmLabel]), right-aligned.
 * With no [confirmLabel] (and no dismiss) the action row is left out, e.g. for a
 * non-dismissable progress dialog or when [content] holds the choices itself.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String?,
    onConfirm: (() -> Unit)?,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        AppDialogSurface(
            title = title,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            modifier = modifier,
            text = text,
            dismissLabel = dismissLabel,
            onDismiss = onDismiss,
            confirmEnabled = confirmEnabled,
            confirmLoading = confirmLoading,
            content = content
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDialogSurface(
    title: String,
    confirmLabel: String?,
    onConfirm: (() -> Unit)?,
    modifier: Modifier,
    text: String?,
    dismissLabel: String?,
    onDismiss: (() -> Unit)?,
    confirmEnabled: Boolean,
    confirmLoading: Boolean,
    content: (@Composable () -> Unit)?
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
            val hasDismiss = dismissLabel != null && onDismiss != null
            val hasConfirm = confirmLabel != null && onConfirm != null
            if (!hasDismiss && !hasConfirm) return@Column
            Spacer(Modifier.height(24.dp))
            // FlowRow: when both labels don't fit on one line, the primary action moves to its own
            // line (still right-aligned) instead of squeezing and cutting off its label.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasDismiss) {
                    PillTextButton(dismissLabel!!, onClick = onDismiss!!)
                }
                if (hasConfirm) {
                    PrimaryPillButton(confirmLabel!!, onClick = onConfirm!!, enabled = confirmEnabled, loading = confirmLoading)
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun AppDialogPreview() {
    PreviewSurface {
        AppDialogSurface(
            title = "Избриши рецепт?",
            confirmLabel = "Избриши",
            onConfirm = {},
            modifier = Modifier,
            text = "Рецептот ќе се избрише од сите уреди.",
            dismissLabel = "Откажи",
            onDismiss = {},
            confirmEnabled = true,
            confirmLoading = false,
            content = null
        )
    }
}
