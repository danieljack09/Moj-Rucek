package com.mojrucek.app.session

import com.mojrucek.app.auth.AccountKind

/** Plans (and cancels) the one background retry for a session refresh that could not be done right now. */
interface RetryScheduler {
    fun scheduleRetry()
    fun cancelRetry()
}

/** Which server-side account type an app-side account type should have. */
object ExpectedKind {
    /** null = no expectation (an unknown type must never force a refresh). */
    fun of(kind: AccountKind?): String? = when (kind) {
        AccountKind.GUEST -> "GUEST"
        AccountKind.GOOGLE, AccountKind.EMAIL -> "REGISTERED"
        AccountKind.OTHER, null -> null
    }
}

/**
 * Decides when the app asks the server about the signed-in account. Called when the signed-in account (its id or
 * its type) changes and once when the app starts with an account:
 *  - the answer is asked for only when needed (nothing remembered, older than 12 hours, or the remembered type no
 *    longer matches, e.g. the guest has linked Google);
 *  - when that cannot be done now (no internet, server busy), one background retry is planned;
 *  - once a refresh succeeded, a pending retry is no longer needed.
 * Nobody signed in means nothing to do. Failures are never shown to the user.
 */
class SessionTriggers(private val refresher: SessionRefresher, private val retry: RetryScheduler) {

    suspend fun onAccount(uid: String?, kind: AccountKind?) {
        if (uid == null) return
        when (refresher.refreshIfStale(uid, ExpectedKind.of(kind))) {
            is RefreshOutcome.Retry -> retry.scheduleRetry()
            is RefreshOutcome.Updated -> retry.cancelRetry()
            RefreshOutcome.Fresh, is RefreshOutcome.GaveUp, RefreshOutcome.NoToken, RefreshOutcome.AccountChanged -> Unit
        }
    }
}
