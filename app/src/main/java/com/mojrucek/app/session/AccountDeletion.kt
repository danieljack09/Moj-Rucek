package com.mojrucek.app.session

import kotlinx.coroutines.CancellationException

/** One step of the deletion, as the orchestrator sees it. */
sealed interface Step {
    data object Done : Step

    /** The user backed out (for example closed the Google picker). Nothing was changed. */
    data object Cancelled : Step

    data class Failed(val message: String) : Step
}

/** What the orchestrator needs from the outside world. Real versions talk to Firebase, the server and the database. */
interface DeletionSteps {
    /** The signed-in account right now. */
    val currentUid: String?

    /** Proves it is really the owner (Google picker); a guest needs nothing. */
    suspend fun reauthenticate(): Step
    suspend fun deleteOnServer(): Step
    suspend fun deleteFirebaseAccount(): Step

    /** Removes everything on this phone that belongs to [uid]. */
    suspend fun eraseLocal(uid: String)
}

/** Remembers an account whose server record is gone but whose phone data is not yet erased (for example the app was closed in between). */
interface PendingErasureStore {
    fun get(): String?
    fun set(uid: String)
    fun clear()
}

class InMemoryPendingErasure : PendingErasureStore {
    private var uid: String? = null
    override fun get() = uid
    override fun set(uid: String) { this.uid = uid }
    override fun clear() { uid = null }
}

sealed interface DeletionOutcome {
    data object Done : DeletionOutcome
    data object Cancelled : DeletionOutcome

    /** [message] is shown to the user. [nothingDeleted] is true when nothing at all was removed anywhere. */
    data class Failed(val message: String, val nothingDeleted: Boolean) : DeletionOutcome
}

/**
 * Deletes an account in the safe order (see docs/phase8-account-deletion-and-email-plan.md):
 *  1. prove it is the owner;  2. delete the server record;  3. delete the Firebase account;  4. erase the phone data.
 * Until step 2 succeeds nothing is removed anywhere, so a failure up to there changes nothing. From then on the
 * account is remembered as "to erase", so the phone data goes even if the app is closed before step 4. Steps 2 and 3
 * are safe to repeat, so after a failure the user can simply try again.
 */
class AccountDeletion(private val steps: DeletionSteps, private val pending: PendingErasureStore) {

    suspend fun run(): DeletionOutcome {
        val uid = steps.currentUid ?: return DeletionOutcome.Failed("Не си најавен.", nothingDeleted = true)
        try {
            when (val r = steps.reauthenticate()) {
                Step.Done -> Unit
                Step.Cancelled -> return DeletionOutcome.Cancelled
                is Step.Failed -> return DeletionOutcome.Failed(r.message, nothingDeleted = true)
            }
            if (steps.currentUid != uid) return DeletionOutcome.Failed("Сметката се промени. Обиди се повторно.", nothingDeleted = true)

            when (val r = steps.deleteOnServer()) {
                Step.Done -> Unit
                Step.Cancelled -> return DeletionOutcome.Cancelled
                is Step.Failed -> return DeletionOutcome.Failed(r.message, nothingDeleted = true)
            }
            // The point of no return: from here the phone data of this account must also go.
            pending.set(uid)

            when (val r = steps.deleteFirebaseAccount()) {
                Step.Done -> Unit
                Step.Cancelled -> return DeletionOutcome.Cancelled
                is Step.Failed -> return DeletionOutcome.Failed(r.message, nothingDeleted = false)
            }
            steps.eraseLocal(uid)
            pending.clear()
            return DeletionOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DeletionOutcome.Failed(
                "Бришењето не успеа (${e.javaClass.simpleName}). Обиди се повторно.",
                nothingDeleted = pending.get() == null
            )
        }
    }

    /**
     * At app start: if a deletion got as far as the point of no return but the phone data was not erased, finish it,
     * unless that very account is still signed in (then the user is meant to retry the deletion).
     */
    suspend fun finishInterrupted() {
        val uid = pending.get() ?: return
        if (steps.currentUid == uid) return
        try {
            steps.eraseLocal(uid)
            pending.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stays pending: tried again at the next start. Nothing else is touched.
        }
    }
}
