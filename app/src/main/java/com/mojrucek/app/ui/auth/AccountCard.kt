package com.mojrucek.app.ui.auth

import com.mojrucek.app.ui.components.AppProgressIndicator
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.mojrucek.app.auth.AccountKind
import com.mojrucek.app.repository.SaveOutcome
import com.mojrucek.app.repository.VerifiedSnapshot
import com.mojrucek.app.ui.components.AppCheckbox
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.OutlinePillButton
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.components.SectionCard
import com.mojrucek.app.ui.components.SectionCardFill
import com.mojrucek.app.ui.components.backupSaverFor
import com.mojrucek.app.ui.components.savedBackupMessage
import com.mojrucek.app.ui.components.suggestedBackupFileName
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mojrucek.app.domain.text.MkPlural

/**
 * Account section of Settings, in the reference's profile style: a greenContainer card with a 48dp
 * avatar circle (the email's initial on green; a person icon for a guest), the "Сметка" label and the
 * account line, then the same actions as before (linking for a guest as the orange main button,
 * verification and sign-out for a signed-in account).
 */
@Composable
fun AccountCard(viewModel: AccountViewModel, guestLine: String = "Гостин") {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var showEmail by remember { mutableStateOf(false) }
    val account = state.account
    // Not configured (debug fallback) or signed out (the gate shows Welcome instead): nothing to show.
    if (!account.available || !account.signedIn) return

    val isGuest = account.kind == AccountKind.GUEST
    val accountLine = if (isGuest) guestLine else {
        val label = when (account.kind) {
            AccountKind.GOOGLE -> "Google"
            AccountKind.EMAIL -> "Е-пошта"
            else -> "Сметка"
        }
        "$label: ${account.email ?: "—"}"
    }

    SectionCard(fill = SectionCardFill.GreenContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(initial = if (isGuest) null else account.email?.firstOrNull()?.uppercaseChar())
            Spacer(Modifier.width(Spacing.rowPadding))
            Column(Modifier.weight(1f)) {
                Text("Сметка", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(accountLine, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state.busy) {
                AppProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
            }
        }

        if (isGuest) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Поврзи Google сметка за да ја задржиш сметката. Твоите податоци остануваат исти.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PrimaryPillButton(
                "Поврзи со Google",
                onClick = { viewModel.continueWithGoogle(context) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinePillButton(
                "Поврзи со е-пошта",
                onClick = { showEmail = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            if (account.kind == AccountKind.EMAIL && !account.emailVerified) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Е-поштата не е потврдена. Отвори го линкот што ти го испративме. Додека не ја потврдиш, сметката важи како гостинска.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePillButton("Потврдив", onClick = viewModel::checkVerification, enabled = !state.busy, modifier = Modifier.weight(1f))
                    OutlinePillButton("Испрати повторно", onClick = viewModel::resendVerification, enabled = !state.busy, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinePillButton(
                "Одјави се",
                onClick = { confirmSignOut = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (confirmSignOut) {
        AppDialog(
            onDismissRequest = { confirmSignOut = false },
            title = "Одјава",
            text = "Ќе се одјавиш од сметката. Податоците на овој телефон остануваат.",
            confirmLabel = "Одјави се",
            onConfirm = { confirmSignOut = false; viewModel.signOut() },
            dismissLabel = "Откажи",
            onDismiss = { confirmSignOut = false }
        )
    }

    if (showEmail) EmailAuthDialog(viewModel, isGuest = isGuest, onDismiss = { showEmail = false })
    SwitchAccountDialogs(state.switch, viewModel)
    AccountMessageDialog(state.message, viewModel::dismissMessage)
}

/** 48dp green circle with the account's initial, or a person icon when there is no initial (guest). */
@Composable
private fun AccountAvatar(initial: Char?) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MojRucekTheme.colors.green),
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) {
            Text(initial.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondary)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MojRucekTheme.colors.iconOnGreen, modifier = Modifier.size(24.dp))
        }
    }
}


/** The guest -> existing account flow. Every step can be cancelled until the final confirmation. */
@Composable
private fun SwitchAccountDialogs(step: SwitchStep?, viewModel: AccountViewModel) {
    val context = LocalContext.current
    val noDismiss = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    when (step) {
        null -> Unit
        is SwitchStep.Explain -> AppDialog(
            onDismissRequest = viewModel::cancelSwitch,
            title = "Сметката веќе постои",
            text = "${step.email ?: "Оваа Google сметка"} веќе е поврзана со друга сметка во Мој Ручек. " +
                "Сметките не се спојуваат.\n\n" +
                "Ако продолжиш:\n" +
                "1. прво се прави и проверува копија од податоците на овој телефон;\n" +
                "2. потоа се најавуваш во постоечката сметка;\n" +
                "3. намирниците, твоите рецепти, плановите и листата за пазарење се бришат од овој телефон.\n\n" +
                "Гостинската сметка потоа не може да се врати. Ако откажеш, ништо не се менува.",
            confirmLabel = "Направи копија",
            onConfirm = viewModel::makeSnapshot,
            dismissLabel = "Откажи",
            onDismiss = viewModel::cancelSwitch
        )
        SwitchStep.MakingSnapshot, SwitchStep.Switching -> AppDialog(
            onDismissRequest = {},
            properties = noDismiss,
            title = if (step == SwitchStep.Switching) "Префрлање…" else "Се прави копија…",
            confirmLabel = null,
            onConfirm = null
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { AppProgressIndicator() }
        }
        is SwitchStep.Confirm -> {
            var understood by remember(step) { mutableStateOf(false) }
            // Extra copy for the user. The verified internal snapshot already exists and is what the
            // switch relies on; cancelling or failing here changes nothing about the switch.
            val scope = rememberCoroutineScope()
            var saveMessage by remember(step) { mutableStateOf<String?>(null) }
            val saver = remember(step) {
                backupSaverFor(context) { withContext(Dispatchers.IO) { step.snapshot.file.readText(Charsets.UTF_8) } }
            }
            val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    saveMessage = when (val outcome = saver.save(uri)) {
                        SaveOutcome.Cancelled -> null
                        is SaveOutcome.Saved -> savedBackupMessage(context, uri)
                        is SaveOutcome.Failed -> "Копијата НЕ е зачувана на уредот.\n\n${outcome.reason}"
                    }
                }
            }
            val s = step.snapshot.summary
            AppDialog(
                onDismissRequest = viewModel::cancelSwitch,
                properties = noDismiss,
                title = "Копијата е готова",
                text = "Копијата е зачувана и проверена: ${MkPlural.recipes(s.recipeCount)}, ${MkPlural.ingredients(s.ingredientCount)}, " +
                    "${MkPlural.items(s.shoppingItemCount)} за пазарење, ${MkPlural.plans(s.weeklyPlanCount)}.\n\n" +
                    "Препорачуваме да ја зачуваш и надвор од телефонот. Подоцна можеш да ја вчиташ " +
                    "преку Поставки → Резервна копија.",
                confirmLabel = "Најави се и избриши",
                onConfirm = viewModel::confirmSwitch,
                confirmEnabled = understood,
                dismissLabel = "Откажи",
                onDismiss = viewModel::cancelSwitch
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinePillButton(
                        "Зачувај на уредот",
                        onClick = { saveLauncher.launch(suggestedBackupFileName()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinePillButton(
                        "Сподели копија",
                        onClick = { shareSnapshot(context, step.snapshot) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    saveMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    UnderstoodRow(
                        text = "Разбирам дека локалните податоци ќе се избришат од овој телефон.",
                        checked = understood,
                        onCheckedChange = { understood = it }
                    )
                }
            }
        }
    }
}

/** A whole-row toggle with an [AppCheckbox], as the old clickable checkbox rows. */
@Composable
internal fun UnderstoodRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppCheckbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Spacing.rowPadding))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AccountMessageDialog(message: String?, onDismiss: () -> Unit) {
    message ?: return
    AppDialog(
        onDismissRequest = onDismiss,
        title = "Сметка",
        text = message,
        confirmLabel = "Во ред",
        onConfirm = onDismiss
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
