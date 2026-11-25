package com.mojrucek.app.domain.sync

/** What a sync run should do. See docs/sync-plan.md, section 3. */
sealed interface SyncAction {
    /** Both sides agree (or there is nothing to send). */
    data object Nothing : SyncAction

    /** Send the phone's data; [baseRevision] is the server revision it was based on (0 = nothing on the server yet). */
    data class Upload(val baseRevision: Int) : SyncAction

    /** Replace the phone's data with the server's. Only chosen when the phone has nothing of its own to lose. */
    data object Download : SyncAction

    /** Both sides have changes: the user must choose. */
    data object Conflict : SyncAction
}

/** What the phone knows about its own side. */
data class LocalSyncState(
    /** The revision this phone last synced with; null = never synced on this phone for this account. */
    val lastRevision: Int?,
    /** True when the phone's data differs from what it had at the last sync (or, if never synced, when it has any data). */
    val changedSinceLastSync: Boolean,
    /** True when the phone has no data of its own (nothing to lose by replacing it). */
    val isEmpty: Boolean
)

/** The decision table as a pure function, so every case can be tested without a database or a server. */
object SyncDecision {
    fun decide(local: LocalSyncState, serverRevision: Int): SyncAction {
        val last = local.lastRevision
        if (serverRevision == 0) {
            // Nothing on the server: send what the phone has (if it has anything).
            return if (local.isEmpty) SyncAction.Nothing else SyncAction.Upload(baseRevision = 0)
        }
        if (last == null) {
            // First time on this phone and the server already has data.
            return if (local.isEmpty) SyncAction.Download else SyncAction.Conflict
        }
        return when {
            serverRevision == last -> if (local.changedSinceLastSync) SyncAction.Upload(baseRevision = last) else SyncAction.Nothing
            serverRevision > last -> if (local.changedSinceLastSync) SyncAction.Conflict else SyncAction.Download
            // The server is BEHIND what this phone saw (for example the account was reset): never guess.
            else -> SyncAction.Conflict
        }
    }
}
