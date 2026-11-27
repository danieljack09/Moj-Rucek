package com.mojrucek.app.ui.auth

import com.mojrucek.app.ui.components.OutlinePillButton
import com.mojrucek.app.ui.components.AppDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.repository.OwnerKeys
import com.mojrucek.app.repository.SaveOutcome
import com.mojrucek.app.ui.components.backupSaverFor
import com.mojrucek.app.ui.components.savedBackupMessage
import com.mojrucek.app.ui.components.suggestedBackupFileName
import kotlinx.coroutines.launch

/**
 * The one-time question for data that was created before accounts existed, shown when an account signs in
 * and the phone was not signed in at the time of the upgrade. Keep it in this account, save a copy of it first,
 * or decide later. Nothing is ever deleted or uploaded from here.
 */
@Composable
fun LegacyDataPrompt(container: AppContainer) {
    val needed by container.ownershipCoordinator.promptNeeded.collectAsState()
    if (!needed) return

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    // "Save a copy first" saves exactly the data that has no owner yet, the data this question is about.
    val saver = remember { backupSaverFor(context) { container.backupRepository.exportToJson(OwnerKeys.UNOWNED) } }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = when (val outcome = saver.save(uri)) {
                SaveOutcome.Cancelled -> null
                is SaveOutcome.Saved -> savedBackupMessage(context, uri)
                is SaveOutcome.Failed -> "Копијата НЕ е зачувана.\n\n${outcome.reason}"
            }
        }
    }

    LegacyDataPromptDialog(
        message = message,
        onKeep = { scope.launch { container.ownershipCoordinator.keep(container.currentOwner.key) } },
        onLater = { container.ownershipCoordinator.askLater() },
        onSaveCopy = { saveLauncher.launch(suggestedBackupFileName()) }
    )
}

/** The dialog alone, without any app logic, so it can be tested on its own. */
@Composable
fun LegacyDataPromptDialog(message: String?, onKeep: () -> Unit, onLater: () -> Unit, onSaveCopy: () -> Unit) {
    AppDialog(
        onDismissRequest = onLater,
        title = "Податоци од пред сметките",
        text = "На овој телефон има податоци создадени пред да постојат сметките " +
            "(намирници, рецепти, листа за пазарење). Дали да ги задржиш во оваа сметка?\n\n" +
            "Ако избереш „Подоцна“, податоците остануваат на телефонот, но не се прикажуваат " +
            "додека не одлучиш. Ништо не се брише.",
        confirmLabel = "Задржи во оваа сметка",
        onConfirm = onKeep,
        dismissLabel = "Подоцна",
        onDismiss = onLater
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinePillButton("Зачувај копија прво", onClick = onSaveCopy, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
