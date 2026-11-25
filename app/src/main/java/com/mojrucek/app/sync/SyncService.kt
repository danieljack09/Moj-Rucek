package com.mojrucek.app.sync

import com.mojrucek.app.session.RetryScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/** What the service needs from the coordinator (so it can be tested without one). */
interface Syncer {
    suspend fun sync(): SyncOutcome
    suspend fun resolveKeepThisPhone(): SyncOutcome
    suspend fun resolveUseCloud(): SyncOutcome
    fun lastSyncedAt(uid: String): Long?
}

/** What the Settings card and the conflict dialog show. */
sealed interface SyncUiStatus {
    data object Idle : SyncUiStatus
    data object Syncing : SyncUiStatus
    data object InSync : SyncUiStatus
    data class NeedsChoice(val serverRevision: Int) : SyncUiStatus
    data object Offline : SyncUiStatus
    data object TryLater : SyncUiStatus
    data object TooLarge : SyncUiStatus
    data class Problem(val message: String) : SyncUiStatus
}

data class SyncUiState(
    val status: SyncUiStatus = SyncUiStatus.Idle,
    /** When this account last synced successfully (phone clock), or null. */
    val lastSyncedAt: Long? = null,
    /** The user pressed "later" on the conflict dialog; it stays hidden until a new conflict appears or they reopen it. */
    val choiceDismissed: Boolean = false
)

/**
 * Runs sync when something asks for it (app start, leaving the app, the 12-hour job, the button) and keeps what the screens
 * show. Automatic runs are spaced at least [minGapMs] apart, so a burst of events costs one run; the button always runs.
 * A run that could not reach the server plans one background retry; a finished run (or a conflict, which waits for the
 * user) cancels it. Nothing here ever shows a raw error.
 */
class SyncService(
    private val syncer: Syncer,
    private val retry: RetryScheduler,
    private val currentUid: () -> String?,
    /** Runs the user's actions (button, conflict choice) so they are not cut off when a screen or dialog goes away. */
    private val scope: CoroutineScope,
    /** False while something must settle first (for example the data from before accounts is not handed over yet). */
    private val canSync: () -> Boolean = { true },
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val minGapMs: Long = 60_000
) {
    private val _state = MutableStateFlow(SyncUiState(lastSyncedAt = currentUid()?.let(syncer::lastSyncedAt)))
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    @Volatile private var lastAutoRunMs: Long = Long.MIN_VALUE

    /**
     * @param manual true for the button (never skipped by the spacing rule).
     * @return how the run ended, or null when it was skipped (too soon after another, or one is already running).
     */
    suspend fun syncNow(manual: Boolean): SyncOutcome? {
        if (!canSync()) return null
        if (!manual) {
            val now = nowMs()
            if (lastAutoRunMs != Long.MIN_VALUE && now - lastAutoRunMs in 0 until minGapMs) return null
        }
        val outcome = run { syncer.sync() }
        if (!manual && outcome != null) lastAutoRunMs = nowMs()
        return outcome
    }

    suspend fun resolveKeepThisPhone(): SyncOutcome? = run { syncer.resolveKeepThisPhone() }
    suspend fun resolveUseCloud(): SyncOutcome? = run { syncer.resolveUseCloud() }

    /** For screens: start a run that survives the screen going away. */
    fun requestSync(manual: Boolean) { scope.launch { syncNow(manual) } }
    fun chooseKeepThisPhone() { scope.launch { resolveKeepThisPhone() } }
    fun chooseUseCloud() { scope.launch { resolveUseCloud() } }

    fun dismissChoice() { _state.update { it.copy(choiceDismissed = true) } }
    fun reopenChoice() { _state.update { it.copy(choiceDismissed = false) } }

    /** The signed-in account changed: what was shown belonged to the previous one. */
    fun onAccountChanged() {
        lastAutoRunMs = Long.MIN_VALUE
        _state.value = SyncUiState(lastSyncedAt = currentUid()?.let(syncer::lastSyncedAt))
    }

    private suspend fun run(block: suspend () -> SyncOutcome): SyncOutcome? {
        if (!running.compareAndSet(false, true)) return null
        try {
            _state.update { it.copy(status = SyncUiStatus.Syncing) }
            val outcome = block()
            apply(outcome)
            return outcome
        } catch (e: CancellationException) {
            _state.update { it.copy(status = SyncUiStatus.Idle) }
            throw e
        } finally {
            running.set(false)
        }
    }

    private fun apply(outcome: SyncOutcome) {
        val last = currentUid()?.let(syncer::lastSyncedAt)
        when (outcome) {
            SyncOutcome.Uploaded, SyncOutcome.Downloaded, SyncOutcome.AlreadyInSync -> {
                retry.cancelRetry()
                _state.value = SyncUiState(SyncUiStatus.InSync, last)
            }
            is SyncOutcome.NeedsChoice -> {
                retry.cancelRetry()    // the user decides; nothing to retry
                _state.value = SyncUiState(SyncUiStatus.NeedsChoice(outcome.serverRevision), last, choiceDismissed = false)
            }
            SyncOutcome.Offline -> { retry.scheduleRetry(); _state.update { SyncUiState(SyncUiStatus.Offline, last, it.choiceDismissed) } }
            SyncOutcome.TryLater -> { retry.scheduleRetry(); _state.update { SyncUiState(SyncUiStatus.TryLater, last, it.choiceDismissed) } }
            SyncOutcome.TooLarge -> _state.update { SyncUiState(SyncUiStatus.TooLarge, last, it.choiceDismissed) }
            is SyncOutcome.Problem -> _state.update { SyncUiState(SyncUiStatus.Problem(outcome.message), last, it.choiceDismissed) }
            SyncOutcome.NotSignedIn -> { retry.cancelRetry(); _state.value = SyncUiState() }
        }
    }
}
