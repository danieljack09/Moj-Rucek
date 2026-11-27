package com.mojrucek.app.ui.sync

import com.mojrucek.app.ui.components.AppProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.domain.sync.SyncTexts
import com.mojrucek.app.sync.SyncUiStatus
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.OutlinePillButton
import com.mojrucek.app.ui.components.RowIconCircle
import com.mojrucek.app.ui.components.SectionCard
import com.mojrucek.app.ui.components.SectionCardFill
import com.mojrucek.app.ui.theme.Spacing

/**
 * The "Синхронизација" card in Settings: a greenContainer SectionCard with the title, when the account
 * last synced, the status line, and the button. Hidden when signed out.
 */
@Composable
fun SyncCard(container: AppContainer) {
    val account by container.authRepository.state.collectAsState()
    if (!account.available || !account.signedIn) return
    val state by container.syncService.state.collectAsState()
    val busy = state.status == SyncUiStatus.Syncing

    SectionCard(fill = SectionCardFill.GreenContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIconCircle(Icons.Outlined.CloudSync)
            Spacer(Modifier.width(Spacing.rowPadding))
            Column(Modifier.weight(1f)) {
                Text(SyncTexts.TITLE, style = MaterialTheme.typography.titleMedium)
                Text(
                    SyncTexts.lastSynced(state.lastSyncedAt, System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val statusStyle = MaterialTheme.typography.bodyMedium
        when (val s = state.status) {
            SyncUiStatus.Idle -> Unit
            SyncUiStatus.Syncing -> StatusLine {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                    Text(SyncTexts.SYNCING, style = statusStyle)
                }
            }
            SyncUiStatus.InSync -> StatusLine { Text(SyncTexts.IN_SYNC, style = statusStyle) }
            is SyncUiStatus.NeedsChoice -> StatusLine {
                Text(
                    SyncTexts.CONFLICT_PENDING,
                    style = statusStyle,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(role = Role.Button) { container.syncService.reopenChoice() }
                )
            }
            SyncUiStatus.Offline -> StatusLine { Text(SyncTexts.OFFLINE, style = statusStyle) }
            SyncUiStatus.TryLater -> StatusLine { Text(SyncTexts.TRY_LATER, style = statusStyle) }
            SyncUiStatus.TooLarge -> StatusLine { Text(SyncTexts.TOO_LARGE, style = statusStyle, color = MaterialTheme.colorScheme.error) }
            is SyncUiStatus.Problem -> StatusLine { Text(s.message, style = statusStyle, color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinePillButton(
            SyncTexts.BUTTON,
            onClick = { container.syncService.requestSync(manual = true) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusLine(content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    content()
}

/**
 * Asked when the phone and the server both changed. Shown wherever the user is (a conflict can be found in the background),
 * never decided for them, and "later" only hides it until the next run or until they tap the line in Settings.
 * The two choices are equal outline pills stacked in the content; "Подоцна" is the text action.
 */
@Composable
fun SyncConflictDialog(container: AppContainer) {
    val state by container.syncService.state.collectAsState()
    if (state.status !is SyncUiStatus.NeedsChoice || state.choiceDismissed) return

    AppDialog(
        onDismissRequest = container.syncService::dismissChoice,
        title = SyncTexts.CONFLICT_TITLE,
        text = SyncTexts.CONFLICT_TEXT,
        confirmLabel = null,
        onConfirm = null,
        dismissLabel = SyncTexts.LATER,
        onDismiss = container.syncService::dismissChoice
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinePillButton(
                SyncTexts.KEEP_PHONE,
                onClick = container.syncService::chooseKeepThisPhone,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinePillButton(
                SyncTexts.USE_CLOUD,
                onClick = container.syncService::chooseUseCloud,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
