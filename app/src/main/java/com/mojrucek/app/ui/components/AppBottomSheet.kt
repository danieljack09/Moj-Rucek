package com.mojrucek.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.SheetShape
import com.mojrucek.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Spec item 11: a ModalBottomSheet on the background colour with 28dp top corners
 * ([SheetShape]), a 36x4 grabber, a headlineMedium [title] with a close
 * [CircleIconButton], scrollable [content] and, when any footer action is given, an
 * [OutlinePillButton] (secondary: clear/cancel) and a [PrimaryPillButton]
 * (primary: apply/save) side by side.
 *
 * [titleStyle] / [titleColor] restyle the title (still announced as a heading), e.g. a small label.
 *
 * The close button animates the sheet away and then calls [onDismissRequest], the
 * same callback a swipe-down or outside tap uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    closeContentDescription: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionEnabled: Boolean = true,
    primaryActionLoading: Boolean = false,
    titleStyle: TextStyle? = null,
    titleColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        dragHandle = { SheetGrabber() }
    ) {
        SheetBody(
            title = title,
            closeContentDescription = closeContentDescription,
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismissRequest()
                }
            },
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
            primaryActionEnabled = primaryActionEnabled,
            primaryActionLoading = primaryActionLoading,
            titleStyle = titleStyle,
            titleColor = titleColor,
            content = content
        )
    }
}

/** 36x4 grabber in textSecondary at 40%. */
@Composable
private fun SheetGrabber() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

/** Everything inside the sheet below the grabber; split out so it can be previewed. */
@Composable
private fun ColumnScope.SheetBody(
    title: String,
    closeContentDescription: String,
    onClose: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: (() -> Unit)?,
    primaryActionLabel: String?,
    onPrimaryAction: (() -> Unit)?,
    primaryActionEnabled: Boolean,
    primaryActionLoading: Boolean,
    titleStyle: TextStyle? = null,
    titleColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenHorizontal, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = titleStyle ?: MaterialTheme.typography.headlineMedium,
            color = titleColor,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        CircleIconButton(Icons.Outlined.Close, contentDescription = closeContentDescription, onClick = onClose)
    }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
        content = content
    )
    val hasSecondary = secondaryActionLabel != null && onSecondaryAction != null
    val hasPrimary = primaryActionLabel != null && onPrimaryAction != null
    if (hasSecondary || hasPrimary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasSecondary) {
                OutlinePillButton(secondaryActionLabel!!, onClick = onSecondaryAction!!, modifier = Modifier.weight(1f))
            }
            if (hasPrimary) {
                PrimaryPillButton(
                    primaryActionLabel!!,
                    onClick = onPrimaryAction!!,
                    modifier = Modifier.weight(1f),
                    enabled = primaryActionEnabled,
                    loading = primaryActionLoading
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun AppBottomSheetPreview() {
    PreviewSurface {
        Column(
            Modifier
                .clip(SheetShape)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetGrabber()
            SheetBody(
                title = "Филтри",
                closeContentDescription = "Затвори",
                onClose = {},
                secondaryActionLabel = "Откажи",
                onSecondaryAction = {},
                primaryActionLabel = "Зачувај",
                onPrimaryAction = {},
                primaryActionEnabled = true,
                primaryActionLoading = false
            ) {
                ChipRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    AppChip("Пилешко", selected = true, onClick = {})
                    AppChip("Паста", selected = false, onClick = {})
                    AppChip("Риба", selected = false, onClick = {})
                }
            }
        }
    }
}
