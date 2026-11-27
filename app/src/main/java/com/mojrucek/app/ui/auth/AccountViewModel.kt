package com.mojrucek.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.auth.AccountState
import com.mojrucek.app.domain.auth.EmailInput
import com.mojrucek.app.auth.AuthRepository
import com.mojrucek.app.auth.AuthResult
import com.mojrucek.app.repository.AccountSwitchRepository
import com.mojrucek.app.repository.SnapshotResult
import com.mojrucek.app.repository.VerifiedSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EmailMode { CREATE, SIGN_IN }

/** Steps of the guest -> existing account switch (credential-already-in-use). */
sealed interface SwitchStep {
    /** Explains what will happen; nothing has been done yet. */
    data class Explain(val email: String?) : SwitchStep
    data object MakingSnapshot : SwitchStep
    /** Snapshot verified; waiting for the explicit confirmation. */
    data class Confirm(val email: String?, val snapshot: VerifiedSnapshot) : SwitchStep
    data object Switching : SwitchStep
}

data class AccountUiState(
    val account: AccountState,
    val busy: Boolean = false,
    val message: String? = null,
    val switch: SwitchStep? = null
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val accountSwitchRepository: AccountSwitchRepository
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val switch = MutableStateFlow<SwitchStep?>(null)

    val uiState: StateFlow<AccountUiState> = combine(authRepository.state, busy, message, switch) { a, b, m, s ->
        AccountUiState(a, b, m, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState(authRepository.state.value))

    fun continueAsGuest() = launchAuth { authRepository.continueAsGuest() }

    /** [activityContext] must be the Activity (Credential Manager shows its UI on top of it). */
    fun continueWithGoogle(activityContext: Context) = launchAuth { authRepository.continueWithGoogle(activityContext) }

    /** True while this ViewModel started a switch flow, so only it clears the pending credential. */
    private var ownsConflict = false

    private fun launchAuth(block: suspend () -> AuthResult) {
        if (busy.value) return
        busy.value = true                      // set before launching: a double tap cannot start two flows
        viewModelScope.launch {
            try {
                when (val r = block()) {
                    AuthResult.Success, AuthResult.Cancelled -> Unit
                    is AuthResult.Error -> message.value = r.message
                    is AuthResult.CredentialInUse -> {
                        ownsConflict = true
                        switch.value = SwitchStep.Explain(r.email)
                    }
                }
            } finally {
                busy.value = false
            }
        }
    }

    /**
     * The email form: [onDone] gets null on success (or when the explicit switch flow took over), else the text to show.
     * Everything is checked first; nothing is sent when it is not valid.
     */
    fun submitEmail(mode: EmailMode, rawEmail: String, password: String, onDone: (String?) -> Unit) {
        val email = EmailInput.normalize(rawEmail)
        val problem = if (mode == EmailMode.CREATE) EmailInput.createProblem(email, password) else EmailInput.signInProblem(email, password)
        if (problem != null) { onDone(problem); return }
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                when (val r = if (mode == EmailMode.CREATE) authRepository.createEmailAccount(email, password) else authRepository.signInWithEmail(email, password)) {
                    AuthResult.Success -> {
                        if (mode == EmailMode.CREATE) message.value = "Испратена е порака на $email. Отвори го линкот во неа за да ја потврдиш е-поштата."
                        onDone(null)
                    }
                    AuthResult.Cancelled -> onDone(null)
                    is AuthResult.Error -> onDone(r.message)
                    is AuthResult.CredentialInUse -> {
                        ownsConflict = true
                        switch.value = SwitchStep.Explain(r.email)
                        onDone(null)
                    }
                }
            } finally {
                busy.value = false
            }
        }
    }

    /** "Forgot password": sends the email. [onDone] gets null on success (the same answer whether or not the address has an account). */
    fun requestPasswordReset(rawEmail: String, onDone: (String?) -> Unit) {
        val email = EmailInput.normalize(rawEmail)
        EmailInput.emailProblem(email)?.let { onDone(it); return }
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                onDone((authRepository.sendPasswordReset(email) as? AuthResult.Error)?.message)
            } finally {
                busy.value = false
            }
        }
    }

    /** Sends the verification email again. */
    fun resendVerification() = launchAuthMessage {
        when (val r = authRepository.sendVerificationEmail()) {
            AuthResult.Success -> "Испратена е нова порака. Отвори го линкот во неа."
            is AuthResult.Error -> r.message
            else -> "Не успеа. Обиди се повторно."
        }
    }

    /** "I confirmed it": asks Firebase, and the server hears about it through the usual refresh. */
    fun checkVerification() = launchAuthMessage {
        if (authRepository.refreshEmailVerification()) "Е-поштата е потврдена." else "Сè уште не е потврдена. Отвори го линкот во пораката."
    }

    private fun launchAuthMessage(block: suspend () -> String) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { message.value = block() } finally { busy.value = false }
        }
    }

    fun makeSnapshot() {
        val step = switch.value as? SwitchStep.Explain ?: return
        switch.value = SwitchStep.MakingSnapshot   // synchronous step change: no second run
        viewModelScope.launch {
            when (val r = accountSwitchRepository.createVerifiedSnapshot()) {
                is SnapshotResult.Ok -> switch.value = SwitchStep.Confirm(step.email, r.snapshot)
                is SnapshotResult.Failed -> {
                    cancelSwitch()
                    message.value = "${r.reason}\n\nНишто не е променето. Остануваш гостин."
                }
            }
        }
    }

    /**
     * Only reachable from [SwitchStep.Confirm], i.e. after a verified snapshot and the user's
     * explicit confirmation. Order: re-check data == snapshot -> sign in -> clear (which re-checks
     * inside its transaction). Runs NonCancellable so leaving the screen cannot stop it halfway.
     */
    fun confirmSwitch() {
        val step = switch.value as? SwitchStep.Confirm ?: return
        switch.value = SwitchStep.Switching
        viewModelScope.launch {
            message.value = withContext(NonCancellable) { switchTo(step.snapshot) }
            switch.value = null
        }
    }

    private suspend fun switchTo(snapshot: VerifiedSnapshot): String {
        if (!accountSwitchRepository.matchesSnapshot(snapshot)) {
            releaseConflict()
            return "Податоците се променија по правењето на копијата.\n\nНишто не е избришано. Остануваш гостин. Обиди се повторно."
        }
        return when (val r = authRepository.signInWithPendingCredential()) {
            is AuthResult.Success -> {
                ownsConflict = false
                // The guest's copy now also belongs to the account the person signed into (deleted together with it).
                accountSwitchRepository.recordDestination(snapshot, authRepository.currentUid)
                val cleared = try {
                    accountSwitchRepository.clearLocalUserData(snapshot)
                } catch (e: Exception) {
                    false
                }
                if (cleared) {
                    "Најавен си во постоечката сметка. Податоците од гостинската сметка се зачувани во копијата " +
                        "„${snapshot.file.name}“."
                } else {
                    "Најавен си во постоечката сметка, но локалните податоци НЕ се избришаа " +
                        "(не се совпаѓаа со копијата или бришењето не успеа). Ништо не е изгубено."
                }
            }
            is AuthResult.Error -> {
                releaseConflict()
                "${r.message}\n\nНишто не е избришано. Остануваш гостин."
            }
            else -> {
                releaseConflict()
                "Префрлањето не успеа.\n\nНишто не е избришано. Остануваш гостин."
            }
        }
    }

    fun cancelSwitch() {
        releaseConflict()
        switch.value = null
    }

    private fun releaseConflict() {
        if (ownsConflict) authRepository.clearPendingConflict()
        ownsConflict = false
    }

    override fun onCleared() {
        // Screen gone mid-flow (e.g. Settings closed with the dialog open): drop the credential.
        // A running confirmSwitch is NonCancellable and has already taken ownership.
        if (switch.value !is SwitchStep.Switching) releaseConflict()
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun dismissMessage() { message.value = null }
}
