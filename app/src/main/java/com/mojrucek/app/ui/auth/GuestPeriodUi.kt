package com.mojrucek.app.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mojrucek.app.auth.AccountKind
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.domain.session.GuestPeriod
import com.mojrucek.app.domain.session.GuestTexts
import java.time.LocalDate

/** The remembered answer for the account that is signed in now (follows account changes and fresh answers). */
@Composable
fun guestPeriodStatus(container: AppContainer): GuestPeriod.Status {
    val account by container.authRepository.state.collectAsState()
    val changes by container.sessionRepository.changes.collectAsState()
    val session = remember(account.uid, account.kind, changes) { account.uid?.let { container.sessionRepository.cached(it) } }
    // An email account whose address is not confirmed yet still counts as a guest on the server.
    val guestLike = account.kind == AccountKind.GUEST || (account.kind == AccountKind.EMAIL && !account.emailVerified)
    if (!guestLike || session?.kind != "GUEST") return GuestPeriod.Status.NotAGuest
    return session.guestStatus(System.currentTimeMillis())
}

/** The text under "Сметка" in Settings for a guest ("Гостин · уште 11 дена"). */
@Composable
fun guestSettingsLine(container: AppContainer): String = GuestTexts.settingsLine(guestPeriodStatus(container))

/**
 * Tells a guest, once a day at most, that the guest period is over. Never blocks anything: "Подоцна" just closes it,
 * and the other button opens Settings where Google can be linked.
 */
@Composable
fun GuestExpiredDialog(container: AppContainer, onOpenSettings: () -> Unit) {
    val account by container.authRepository.state.collectAsState()
    val status = guestPeriodStatus(container)
    var dismissedForNow by remember { mutableStateOf(false) }
    val uid = account.uid
    val today = LocalDate.now()
    val show = !dismissedForNow && uid != null &&
        GuestPeriod.shouldShowExpiredDialog(status, container.sessionRepository.expiredDialogShownOn(uid), today)
    // Remember at once that it was shown today, so it cannot come back before tomorrow, even if the app is killed.
    LaunchedEffect(show, uid) { if (show && uid != null) container.sessionRepository.markExpiredDialogShown(uid, today) }
    if (!show) return
    AlertDialog(
        onDismissRequest = { dismissedForNow = true },
        title = { Text(GuestTexts.EXPIRED_TITLE) },
        text = { Text(GuestTexts.EXPIRED_TEXT) },
        confirmButton = { TextButton(onClick = { dismissedForNow = true; onOpenSettings() }) { Text("Поврзи со Google") } },
        dismissButton = { TextButton(onClick = { dismissedForNow = true }) { Text("Подоцна") } }
    )
}
