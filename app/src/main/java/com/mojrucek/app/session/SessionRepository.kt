package com.mojrucek.app.session

import com.mojrucek.app.domain.session.ServerClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the Firebase sign-in token comes from (the real one is the app's AuthRepository). */
interface IdTokenSource {
    /** The id of the account that is signed in right now, or null. */
    val currentUid: String?

    /** A token for the signed-in account; [forceRefresh] asks Firebase for a brand new one. Null when nobody is signed in. */
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

/** What a refresh did. The app never shows any of this to the user; it only decides whether to try again later. */
sealed interface RefreshOutcome {
    /** The server answered; the new answer is stored. */
    data class Updated(val session: StoredSession) : RefreshOutcome

    /** Nothing was needed (the stored answer is recent). */
    data object Fresh : RefreshOutcome

    /** Temporary trouble (no internet, busy or unavailable server): try again later. */
    data class Retry(val why: String) : RefreshOutcome

    /** The server will not accept this account's token, or answered something unusable: do not loop; wait for the next trigger. */
    data class GaveUp(val why: String) : RefreshOutcome

    /** Nobody is signed in (no token). */
    data object NoToken : RefreshOutcome

    /** Another account is signed in now; this refresh was for a previous one and did nothing. */
    data object AccountChanged : RefreshOutcome
}

/** What the triggers and the retry job need from the session code (so they can be tested without a server). */
interface SessionRefresher {
    /** Asks the server now, whatever is remembered. */
    suspend fun refresh(uid: String): RefreshOutcome

    /**
     * Asks the server only when needed: nothing is remembered, it is old, or the remembered account type
     * ([StoredSession.kind]) is not [expectedKind] (for example the guest has linked Google since).
     */
    suspend fun refreshIfStale(uid: String, expectedKind: String? = null): RefreshOutcome
}

/**
 * Tells the server who is signed in (opening the guest's 14 days on the first call), remembers the answer per
 * account, and never blocks or breaks the app: every failure is turned into a [RefreshOutcome].
 */
class SessionRepository(
    private val server: SessionServer,
    private val store: SessionStore,
    private val tokens: IdTokenSource,
    private val nowMs: () -> Long = System::currentTimeMillis
) : SessionRefresher {
    /** One refresh at a time: a second caller waits, then sees the fresh answer instead of asking again. */
    private val lock = Mutex()

    private val _changes = MutableStateFlow(0L)

    /** Ticks every time a new answer is remembered, so screens and reminders can follow it. */
    val changes: StateFlow<Long> = _changes

    fun expiredDialogShownOn(uid: String): java.time.LocalDate? = store.expiredDialogShownOn(uid)
    fun markExpiredDialogShown(uid: String, day: java.time.LocalDate) = store.setExpiredDialogShownOn(uid, day)

    /** The last remembered answer for [uid], if any. */
    fun cached(uid: String): StoredSession? = store.load(uid)

    override suspend fun refreshIfStale(uid: String, expectedKind: String?): RefreshOutcome = refreshIfStale(uid, expectedKind, DEFAULT_MAX_AGE_MS)

    /** Asks the server only if nothing is remembered for [uid], it is older than [maxAgeMs], or its type is not [expectedKind]. */
    suspend fun refreshIfStale(uid: String, expectedKind: String?, maxAgeMs: Long): RefreshOutcome = lock.withLock {
        val last = store.load(uid)
        val age = last?.let { nowMs() - it.refreshedAtMs }
        // A remembered time in the future (the phone clock was moved back) counts as stale, never as "fresh forever".
        val recent = last != null && age != null && age in 0 until maxAgeMs
        val typeMatches = expectedKind == null || last?.kind == expectedKind
        if (recent && typeMatches) RefreshOutcome.Fresh else doRefresh(uid)
    }

    /** Asks the server now, whatever is remembered. */
    override suspend fun refresh(uid: String): RefreshOutcome = lock.withLock { doRefresh(uid) }

    private suspend fun doRefresh(uid: String): RefreshOutcome {
        try {
            if (tokens.currentUid != uid) return RefreshOutcome.AccountChanged
            val token = tokens.idToken(false) ?: return RefreshOutcome.NoToken
            var result = server.open(token, nowMs)
            if (result is SessionApiResult.Unauthorized) {
                // The token may simply have expired: ask Firebase for a new one, once, and try once more.
                val fresh = tokens.idToken(true) ?: return RefreshOutcome.NoToken
                if (tokens.currentUid != uid) return RefreshOutcome.AccountChanged
                result = server.open(fresh, nowMs)
            }
            return when (result) {
                is SessionApiResult.Ok -> {
                    if (tokens.currentUid != uid) return RefreshOutcome.AccountChanged
                    val s = result.session
                    val stored = StoredSession(
                        userId = s.userId,
                        kind = s.kind,
                        plan = s.plan,
                        guestStartedAt = s.guestStartedAt,
                        guestExpiresAt = s.guestExpiresAt,
                        clockOffsetMs = ServerClock.measuredAt(s.serverTime, result.deviceNowAtResponseMs).offsetMs,
                        refreshedAtMs = nowMs()
                    )
                    store.save(uid, stored)
                    _changes.value = _changes.value + 1
                    RefreshOutcome.Updated(stored)
                }
                SessionApiResult.Unauthorized -> RefreshOutcome.GaveUp("the server did not accept the sign-in")
                SessionApiResult.RateLimited -> RefreshOutcome.Retry("the server asked to slow down")
                SessionApiResult.ServerUnavailable -> RefreshOutcome.Retry("the server is not ready")
                SessionApiResult.Offline -> RefreshOutcome.Retry("no connection to the server")
                is SessionApiResult.Failed -> RefreshOutcome.GaveUp(result.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Typically Firebase could not fetch a token without internet. Never a crash, never an error for the user.
            return RefreshOutcome.Retry("could not get a sign-in token (${e.javaClass.simpleName})")
        }
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 12L * 60 * 60 * 1000
    }
}
