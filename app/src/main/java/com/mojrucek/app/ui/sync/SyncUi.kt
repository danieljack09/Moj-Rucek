package com.mojrucek.app.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.domain.sync.SyncTexts
import com.mojrucek.app.sync.SyncUiStatus

/** The "Синхронизација" card in Settings: when the account last synced, how it went, and the button. Hidden when signed out. */
@Composable
fun SyncCard(container: AppContainer) {
    val account by container.authRepository.state.collectAsState()
    if (!account.available || !account.signedIn) return
    val state by container.syncService.state.collectAsState()
    val busy = state.status == SyncUiStatus.Syncing

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(SyncTexts.TITLE, style = MaterialTheme.typography.titleMedium)
            Text(SyncTexts.lastSynced(state.lastSyncedAt, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium)
            when (val s = state.status) {
                SyncUiStatus.Idle -> Unit
                SyncUiStatus.Syncing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(SyncTexts.SYNCING, style = MaterialTheme.typography.bodyMedium)
                }
                SyncUiStatus.InSync -> Text(SyncTexts.IN_SYNC, style = MaterialTheme.typography.bodyMedium)
                is SyncUiStatus.NeedsChoice -> Text(
                    SyncTexts.CONFLICT_PENDING,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { container.syncService.reopenChoice() }
                )
                SyncUiStatus.Offline -> Text(SyncTexts.OFFLINE, style = MaterialTheme.typography.bodyMedium)
                SyncUiStatus.TryLater -> Text(SyncTexts.TRY_LATER, style = MaterialTheme.typography.bodyMedium)
                SyncUiStatus.TooLarge -> Text(SyncTexts.TOO_LARGE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                is SyncUiStatus.Problem -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = { container.syncService.requestSync(manual = true) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(SyncTexts.BUTTON) }
        }
    }
}

/**
 * Asked when the phone and the server both changed. Shown wherever the user is (a conflict can be found in the background),
 * never decided for them, and "later" only hides it until the next run or until they tap the line in Settings.
 */
@Composable
fun SyncConflictDialog(container: AppContainer) {
    val state by container.syncService.state.collectAsState()
    if (state.status !is SyncUiStatus.NeedsChoice || state.choiceDismissed) return

    AlertDialog(
        onDismissRequest = container.syncService::dismissChoice,
        title = { Text(SyncTexts.CONFLICT_TITLE) },
        text = { Text(SyncTexts.CONFLICT_TEXT) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = container.syncService::chooseKeepThisPhone) { Text(SyncTexts.KEEP_PHONE) }
                TextButton(onClick = container.syncService::chooseUseCloud) { Text(SyncTexts.USE_CLOUD) }
            }
        },
        dismissButton = { TextButton(onClick = container.syncService::dismissChoice) { Text(SyncTexts.LATER) } }
    )
}
