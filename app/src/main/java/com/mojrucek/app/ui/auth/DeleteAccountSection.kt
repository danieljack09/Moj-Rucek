package com.mojrucek.app.ui.auth

import com.mojrucek.app.ui.components.AppProgressIndicator
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.auth.AccountKind
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.repository.SaveOutcome
import com.mojrucek.app.session.DeletionOutcome
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.GenericViewModelFactory
import com.mojrucek.app.ui.components.OutlinePillButton
import com.mojrucek.app.ui.components.PillTextField
import com.mojrucek.app.ui.components.backupSaverFor
import com.mojrucek.app.ui.components.savedBackupMessage
import com.mojrucek.app.ui.components.suggestedBackupFileName
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
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
    fun confirm(activityContext: Context, password: String? = null) {
        if (_stage.value != DeleteStage.Confirm) return
        _stage.value = DeleteStage.Working          // set before launching: a double tap cannot start two runs
        viewModelScope.launch {
            val outcome = withContext(NonCancellable) { container.accountDeletion(activityContext, password).run() }
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
    if (!account.available || !account.signedIn || (account.kind != AccountKind.GUEST && account.kind != AccountKind.GOOGLE && account.kind != AccountKind.EMAIL)) return

    val vm: DeleteAccountViewModel = viewModel(
        factory = GenericViewModelFactory { DeleteAccountViewModel(container) }
    )
    val stage by vm.stage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var understood by remember(stage) { mutableStateOf(false) }
    var password by remember(stage) { mutableStateOf("") }
    val needsPassword = account.kind == AccountKind.EMAIL
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

    // A row in the error colour, in the same pill-row language as the rest of Settings.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(PillShape)
            .background(MojRucekTheme.colors.greenContainer)
            .clickable(role = Role.Button, onClick = vm::open)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.rowPadding))
        Text("Избриши сметка", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }

    when (val s = stage) {
        DeleteStage.Idle -> Unit
        DeleteStage.Confirm -> {
            // Same gate as before: understood, and for an email account a non-empty password.
            val ready = understood && (!needsPassword || password.isNotEmpty())
            AppDialog(
                onDismissRequest = vm::close,
                title = "Избриши сметка",
                text = "Ќе се избришат сметката и сите нејзини податоци на овој телефон " +
                    "(намирници, твоите рецепти, планови, листа за пазарење, омилени), " +
                    "како и записот на нашиот сервер.\n\nОва не може да се врати.",
                confirmLabel = "Избриши",
                onConfirm = { vm.confirm(context, password.takeIf { needsPassword }) },
                confirmEnabled = ready,
                dismissLabel = "Откажи",
                onDismiss = vm::close
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinePillButton(
                        "Зачувај копија прво",
                        onClick = { saveLauncher.launch(suggestedBackupFileName()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    copyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (needsPassword) {
                        PillTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Лозинка (за потврда)",
                            masked = true
                        )
                    }
                    UnderstoodRow(
                        text = "Разбирам дека не може да се врати",
                        checked = understood,
                        onCheckedChange = { understood = it }
                    )
                }
            }
        }
        DeleteStage.Working -> AppDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = "Се брише…",
            confirmLabel = null,
            onConfirm = null
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { AppProgressIndicator() }
        }
        is DeleteStage.Problem -> AppDialog(
            onDismissRequest = vm::close,
            title = "Не успеа",
            text = s.message,
            confirmLabel = "Во ред",
            onConfirm = vm::close
        )
    }
}
