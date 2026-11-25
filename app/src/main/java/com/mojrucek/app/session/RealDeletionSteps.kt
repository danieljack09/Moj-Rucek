package com.mojrucek.app.session

import android.content.Context
import com.mojrucek.app.auth.AuthRepository
import com.mojrucek.app.auth.AuthResult
import com.mojrucek.app.repository.LocalDataEraser

/** Keeps [PendingErasureStore] across app restarts, in the app's private preferences. */
class SharedPreferencesPendingErasure(context: Context) : PendingErasureStore {
    private val prefs = context.applicationContext.getSharedPreferences("deletion", Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString("pending_uid", null)
    override fun set(uid: String) { prefs.edit().putString("pending_uid", uid).commit() }   // commit: must survive a kill right after
    override fun clear() { prefs.edit().remove("pending_uid").commit() }
}

/**
 * Everything the app remembers about ONE account outside the database (server answer, list rules, pending requests, sync
 * state and the safety copies kept before sync replaced data). Deleting an account must leave none of it behind.
 */
class AccountMemory(
    private val sessions: SessionStore,
    private val limits: LimitStateStore?,
    private val listRequests: PendingRequestStore?,
    private val syncState: com.mojrucek.app.sync.SyncStateStore?,
    private val syncSafetyCopies: com.mojrucek.app.sync.SafetyCopy?
) {
    fun forget(uid: String) {
        sessions.forget(uid)
        limits?.forget(uid)
        listRequests?.clear(uid)
        syncState?.forget(uid)
        syncSafetyCopies?.deleteAll(uid)
    }
}

/** The real steps: Firebase, our server, the database. [activityContext] is needed for the Google picker (null when only finishing an interrupted deletion). */
class RealDeletionSteps(
    private val auth: AuthRepository,
    private val server: AccountServer,
    private val eraser: LocalDataEraser,
    private val sessions: SessionStore,
    private val activityContext: Context?,
    private val limits: LimitStateStore? = null,
    private val listRequests: PendingRequestStore? = null,
    private val password: String? = null,
    private val syncState: com.mojrucek.app.sync.SyncStateStore? = null,
    private val syncSafetyCopies: com.mojrucek.app.sync.SafetyCopy? = null
) : DeletionSteps {

    override val currentUid: String? get() = auth.currentUid

    override suspend fun reauthenticate(): Step {
        val context = activityContext ?: return Step.Failed("Недостасува екран за потврда.")
        return when (val r = auth.reauthenticate(context, password)) {
            AuthResult.Success -> Step.Done
            AuthResult.Cancelled -> Step.Cancelled
            is AuthResult.Error -> Step.Failed(r.message)
            is AuthResult.CredentialInUse -> Step.Failed("Потврдата не успеа. Обиди се повторно.")
        }
    }

    override suspend fun deleteOnServer(): Step {
        val token = try { auth.idToken(true) } catch (e: Exception) { null }
            ?: return Step.Failed("Нема интернет врска. Провери ја врската и обиди се повторно.")
        return when (val r = server.deleteMe(token)) {
            DeleteApiResult.Deleted -> Step.Done
            DeleteApiResult.Offline -> Step.Failed("Нема интернет врска. Провери ја врската и обиди се повторно.")
            DeleteApiResult.RateLimited, DeleteApiResult.ServerUnavailable -> Step.Failed("Серверот не е достапен. Обиди се подоцна.")
            DeleteApiResult.Unauthorized -> Step.Failed("Сметката не беше потврдена. Обиди се повторно.")
            is DeleteApiResult.Failed -> Step.Failed("Бришењето не успеа. Обиди се повторно.")
        }
    }

    override suspend fun deleteFirebaseAccount(): Step = when (val r = auth.deleteCurrentAccount()) {
        AuthResult.Success -> Step.Done
        is AuthResult.Error -> Step.Failed(r.message + "\n\nСерверскиот запис е веќе избришан; обиди се повторно за да заврши бришењето.")
        else -> Step.Failed("Бришењето не успеа. Обиди се повторно.")
    }

    override suspend fun eraseLocal(uid: String) {
        eraser.eraseAccount(uid)
        AccountMemory(sessions, limits, listRequests, syncState, syncSafetyCopies).forget(uid)
    }
}
