package com.mojrucek.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing

/**
 * Bottom content padding for a tab screen's scrolling list. Tab content draws behind the
 * floating nav bar, so the list ends with the screen Scaffold's bottom inset (the system
 * navigation bar) plus [Spacing.navBarClearance], or [Spacing.navBarClearanceWithFab]
 * when the screen also has a FAB, so the last item can be scrolled clear of both.
 */
fun tabListBottomPadding(scaffoldPadding: PaddingValues, withFab: Boolean = false): Dp =
    scaffoldPadding.calculateBottomPadding() +
        if (withFab) Spacing.navBarClearanceWithFab else Spacing.navBarClearance

/**
 * Snackbars as a flat greenContainer pill (no shadow: the spec allows one only under the nav bar)
 * with the message in the text colour and the action as an accent text button.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = PillShape,
            color = MojRucekTheme.colors.greenContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                Modifier.heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
                data.visuals.actionLabel?.let { label ->
                    Spacer(Modifier.width(8.dp))
                    PillTextButton(label, onClick = { data.performAction() })
                }
            }
        }
    }
}

/** Spinner in the spec's style: accent on a greenContainer track. */
@Composable
fun AppProgressIndicator(modifier: Modifier = Modifier, size: Dp = 40.dp, strokeWidth: Dp = 4.dp) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MojRucekTheme.colors.greenContainer,
        strokeWidth = strokeWidth
    )
}

/**
 * The tab screens' FAB: a flat 56dp accent circle with a plus, lifted [Spacing.fabAboveNavBar]
 * so it sits above the floating nav bar.
 */
@Composable
fun AppFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(bottom = Spacing.fabAboveNavBar).size(56.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Icon(Icons.Outlined.Add, contentDescription = contentDescription)
    }
}
