package com.mojrucek.app.repository

import com.mojrucek.app.auth.AccountState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The values the `ownerUid` column can hold besides a real Firebase user id. */
object OwnerKeys {
    /** Nobody is signed in: matches no row, so nothing is visible and nothing is exposed. */
    const val SIGNED_OUT = "<signed-out>"

    /** No owner: data from before accounts, and the data of a build that runs without accounts (debug fallback). */
    const val UNOWNED = ""

    fun of(accountsAvailable: Boolean, uid: String?): String = when {
        !accountsAvailable -> UNOWNED
        uid.isNullOrEmpty() -> SIGNED_OUT
        else -> uid
    }

    /** True for a signed-in Firebase user (not the unowned bucket and not "nobody"). */
    fun isAccount(key: String): Boolean = key != UNOWNED && key != SIGNED_OUT
}

/**
 * Whose data the app shows and changes right now. Every repository that touches user data asks this for
 * the owner on EVERY call (never caches it), and the lists the screens watch re-run when it changes, so
 * switching accounts switches the data with it.
 */
class CurrentOwner private constructor(
    private val read: () -> String,
    /** Emits the current owner and every change. */
    val flow: Flow<String>
) {
    /** The owner right now. */
    val key: String get() = read()

    companion object {
        fun from(account: StateFlow<AccountState>): CurrentOwner = CurrentOwner(
            read = { account.value.let { OwnerKeys.of(it.available, it.uid) } },
            flow = account.map { OwnerKeys.of(it.available, it.uid) }.distinctUntilChanged()
        )

        /** For tests and for builds with a fixed owner: follows [keys]. */
        fun following(keys: StateFlow<String>): CurrentOwner = CurrentOwner(read = { keys.value }, flow = keys)
    }
}
