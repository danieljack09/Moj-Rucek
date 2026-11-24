package com.mojrucek.app.ui.auth

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.auth.AccountKind
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.repository.SaveOutcome
import com.mojrucek.app.session.DeletionOutcome
import com.mojrucek.app.ui.components.GenericViewModelFactory
import com.mojrucek.app.ui.components.backupSaverFor
import com.mojrucek.app.ui.components.savedBackupMessage
import com.mojrucek.app.ui.components.suggestedBackupFileName
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the "delete account" flow stands. */
sealed interface DeleteStage {
    data object Idle : DeleteStage
    data object Confirm : DeleteStage
    data object Working : DeleteStage
    data class Problem(val message: String) : DeleteStage
}

class DeleteAccountViewModel(private val container: AppContainer) : ViewModel() {
    private val _stage = MutableStateFlow<DeleteStage>(DeleteStage.Idle)
    val stage: StateFlow<DeleteStage> = _stage

    fun open() { if (_stage.value == DeleteStage.Idle) _stage.value = DeleteStage.Confirm }
    fun close() { if (_stage.value != DeleteStage.Working) _stage.value = DeleteStage.Idle }

    /** Runs the deletion. NonCancellable: leaving the screen must not stop it halfway. */
    fun confirm(activityContext: Context) {
        if (_stage.value != DeleteStage.Confirm) return
        _stage.value = DeleteStage.Working          // set before launching: a double tap cannot start two runs
        viewModelScope.launch {
            val outcome = withContext(NonCancellable) { container.accountDeletion(activityContext).run() }
            _stage.value = when (outcome) {
                DeletionOutcome.Done, DeletionOutcome.Cancelled -> DeleteStage.Idle
                is DeletionOutcome.Failed -> DeleteStage.Problem(outcome.message)
            }
        }
    }
}

/** "Избриши сметка" under the account card, for guests and Google accounts. */
@Composable
fun DeleteAccountSection(container: AppContainer) {
    val account by container.authRepository.state.collectAsState()
    if (!account.available || !account.signedIn || (account.kind != AccountKind.GUEST && account.kind != AccountKind.GOOGLE)) return

    val vm: DeleteAccountViewModel = viewModel(
        factory = GenericViewModelFactory { DeleteAccountViewModel(container) }
    )
    val stage by vm.stage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var understood by remember(stage) { mutableStateOf(false) }
    var copyMessage by remember(stage) { mutableStateOf<String?>(null) }
    val saver = remember { backupSaverFor(context) { container.backupRepository.exportToJson(container.currentOwner.key) } }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            copyMessage = when (val outcome = saver.save(uri)) {
                SaveOutcome.Cancelled -> null
                is SaveOutcome.Saved -> savedBackupMessage(context, uri)
                is SaveOutcome.Failed -> "Копијата НЕ е зачувана.\n\n${outcome.reason}"
            }
        }
    }

    TextButton(onClick = vm::open, modifier = Modifier.fillMaxWidth()) {
        Text("Избриши сметка", color = MaterialTheme.colorScheme.error)
    }

    when (val s = stage) {
        DeleteStage.Idle -> Unit
        DeleteStage.Confirm -> AlertDialog(
            onDismissRequest = vm::close,
            title = { Text("Избриши сметка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ќе се избришат сметката и сите нејзини податоци на овој телефон " +
                            "(намирници, твоите рецепти, планови, листа за пазарење, омилени), " +
                            "како и записот на нашиот сервер.\n\nОва не може да се врати."
                    )
                    OutlinedButton(
                        onClick = { saveLauncher.launch(suggestedBackupFileName()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Зачувај копија прво") }
                    copyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth().clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = understood, onCheckedChange = { understood = it })
                        Text("Разбирам дека не може да се врати")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.confirm(context) }, enabled = understood) {
                    Text("Избриши", color = if (understood) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                }
            },
            dismissButton = { TextButton(onClick = vm::close) { Text("Откажи") } }
        )
        DeleteStage.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Се брише…") },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } },
            confirmButton = {}
        )
        is DeleteStage.Problem -> AlertDialog(
            onDismissRequest = vm::close,
            title = { Text("Не успеа") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::close) { Text("Во ред") } }
        )
    }
}
