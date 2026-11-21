package com.mojrucek.app.ui.auth

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.mojrucek.app.auth.AccountKind
import com.mojrucek.app.repository.VerifiedSnapshot

/** Account section of Settings: shows the account and offers linking (guest) or sign-out (Google). */
@Composable
fun AccountCard(viewModel: AccountViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    val account = state.account
    if (!account.available) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сметка", style = MaterialTheme.typography.titleMedium)
            when {
                !account.signedIn -> {
                    Text("Не си најавен. Податоците се чуваат само на овој телефон.", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { viewModel.continueWithGoogle(context) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Продолжи со Google") }
                    OutlinedButton(
                        onClick = viewModel::continueAsGuest,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Продолжи како гостин") }
                }
                account.kind == AccountKind.GUEST -> {
                    Text("Гостин", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Поврзи Google сметка за да ја задржиш сметката. Твоите податоци остануваат исти.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.continueWithGoogle(context) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Поврзи со Google") }
                }
                else -> {
                    val label = when (account.kind) {
                        AccountKind.GOOGLE -> "Google"
                        AccountKind.EMAIL -> "Е-пошта"
                        else -> "Сметка"
                    }
                    Text("$label: ${account.email ?: "—"}", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Одјави се")
                    }
                }
            }
            if (state.busy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Одјава") },
            text = { Text("Ќе се одјавиш од сметката. Податоците на овој телефон остануваат.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) { Text("Одјави се") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Откажи") } }
        )
    }

    SwitchAccountDialogs(state.switch, viewModel)
    AccountMessageDialog(state.message, viewModel::dismissMessage)
}

/** The guest -> existing account flow. Every step can be cancelled until the final confirmation. */
@Composable
private fun SwitchAccountDialogs(step: SwitchStep?, viewModel: AccountViewModel) {
    val context = LocalContext.current
    val noDismiss = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    when (step) {
        null -> Unit
        is SwitchStep.Explain -> AlertDialog(
            onDismissRequest = viewModel::cancelSwitch,
            title = { Text("Сметката веќе постои") },
            text = {
                Text(
                    "${step.email ?: "Оваа Google сметка"} веќе е поврзана со друга сметка во Мој Ручек. " +
                        "Сметките не се спојуваат.\n\n" +
                        "Ако продолжиш:\n" +
                        "1. прво се прави и проверува копија од податоците на овој телефон;\n" +
                        "2. потоа се најавуваш во постоечката сметка;\n" +
                        "3. намирниците, твоите рецепти, плановите и листата за пазарење се бришат од овој телефон.\n\n" +
                        "Гостинската сметка потоа не може да се врати. Ако откажеш, ништо не се менува."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::makeSnapshot) { Text("Направи копија") } },
            dismissButton = { TextButton(onClick = viewModel::cancelSwitch) { Text("Откажи") } }
        )
        SwitchStep.MakingSnapshot, SwitchStep.Switching -> AlertDialog(
            onDismissRequest = {},
            properties = noDismiss,
            title = { Text(if (step == SwitchStep.Switching) "Префрлање…" else "Се прави копија…") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            },
            confirmButton = {}
        )
        is SwitchStep.Confirm -> {
            var understood by remember(step) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = viewModel::cancelSwitch,
                properties = noDismiss,
                title = { Text("Копијата е готова") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val s = step.snapshot.summary
                        Text(
                            "Копијата е зачувана и проверена: ${s.recipeCount} рецепти, ${s.ingredientCount} намирници, " +
                                "${s.shoppingItemCount} ставки за пазарење, ${s.weeklyPlanCount} планови.\n\n" +
                                "Препорачуваме да ја зачуваш и надвор од телефонот. Подоцна можеш да ја вчиташ " +
                                "преку Поставки → Резервна копија."
                        )
                        OutlinedButton(onClick = { shareSnapshot(context, step.snapshot) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Сподели / зачувај копија")
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { understood = !understood },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = understood, onCheckedChange = { understood = it })
                            Text("Разбирам дека локалните податоци ќе се избришат од овој телефон.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmSwitch, enabled = understood) { Text("Најави се и избриши") }
                },
                dismissButton = { TextButton(onClick = viewModel::cancelSwitch) { Text("Откажи") } }
            )
        }
    }
}

@Composable
fun AccountMessageDialog(message: String?, onDismiss: () -> Unit) {
    message ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сметка") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Во ред") } }
    )
}

private fun shareSnapshot(context: android.content.Context, snapshot: VerifiedSnapshot) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", snapshot.file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData makes the read grant reach the app picked in the chooser.
        clipData = ClipData.newRawUri(snapshot.file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Сподели копија"))
}
