package com.mojrucek.app.sync

import android.content.Context
import com.mojrucek.app.domain.sync.LocalSyncState
import com.mojrucek.app.domain.sync.SnapshotFingerprint
import com.mojrucek.app.domain.sync.SyncAction
import com.mojrucek.app.domain.sync.SyncDecision
import com.mojrucek.app.session.IdTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The phone's data as a snapshot, and the all-or-nothing replacement of it. Implemented on top of the backup code. */
interface SnapshotPort {
    /** The signed-in account's data as snapshot text (no device settings, no server catalog). */
    suspend fun export(): String

    /** Replaces the signed-in account's data with [json] in ONE step; false (and nothing changed) when it could not be done. */
    suspend fun import(json: String): Boolean
}

/** Keeps a copy of the phone's data before it is replaced. false = it could not be kept, so nothing may be replaced. */
interface SafetyCopy {
    fun save(snapshotJson: String): Boolean
}

/** What the phone remembers about its last successful sync with the server, per account. */
@Serializable
data class SyncRecord(val revision: Int, val fingerprint: String, val syncedAtMs: Long)

interface SyncStateStore {
    fun load(uid: String): SyncRecord?
    fun save(uid: String, record: SyncRecord)
    fun forget(uid: String)
}

class InMemorySyncStateStore : SyncStateStore {
    private val map = HashMap<String, SyncRecord>()
    override fun load(uid: String) = map[uid]
    override fun save(uid: String, record: SyncRecord) { map[uid] = record }
    override fun forget(uid: String) { map.remove(uid) }
}

class SharedPreferencesSyncStateStore(context: Context) : SyncStateStore {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    override fun load(uid: String): SyncRecord? {
        val text = prefs.getString("r_$uid", null) ?: return null
        return try { json.decodeFromString(SyncRecord.serializer(), text) } catch (e: Exception) { null }
    }
    override fun save(uid: String, record: SyncRecord) {
        prefs.edit().putString("r_$uid", json.encodeToString(SyncRecord.serializer(), record)).commit()
    }
    override fun forget(uid: String) { prefs.edit().remove("r_$uid").commit() }
}

/** Facts read from a snapshot's text. */
object SnapshotInfo {
    private val json = Json { ignoreUnknownKeys = true }
    private val LISTS = listOf("ingredients", "recipes", "recipeIngredients", "shoppingListItems", "weeklyPlans", "plannedMeals")

    /** True when every list in the snapshot is empty: the phone has nothing of its own to lose. Unreadable text counts as NOT empty. */
    fun isEmpty(snapshotJson: String): Boolean = try {
        val root = json.parseToJsonElement(snapshotJson) as? JsonObject ?: return false
        LISTS.all { ((root[it] as? JsonArray)?.size ?: 0) == 0 }
    } catch (e: Exception) {
        false
    }
}

/** How a sync run ended. None of these is shown as a raw error. */
sealed interface SyncOutcome {
    data object Uploaded : SyncOutcome
    data object Downloaded : SyncOutcome
    data object AlreadyInSync : SyncOutcome

    /** Both the phone and the server changed: the user must choose ([resolveKeepThisPhone] or [resolveUseCloud]). */
    data class NeedsChoice(val serverRevision: Int) : SyncOutcome

    data object NotSignedIn : SyncOutcome
    data object Offline : SyncOutcome
    data object TryLater : SyncOutcome
    data object TooLarge : SyncOutcome
    data class Problem(val message: String) : SyncOutcome
}

/**
 * Runs the snapshot sync described in docs/sync-plan.md. One run at a time. Rules that hold everywhere in here:
 *  - the phone's data is replaced only after a safety copy of it was kept, and only through the all-or-nothing import;
 *  - a run that finds the signed-in account changed halfway stores nothing;
 *  - the fingerprint remembered after an upload is the one of the data that was SENT, so a change made during the upload is
 *    noticed at the next run;
 *  - the user's choice in a conflict is never made for them.
 */
class SyncCoordinator(
    private val server: SyncServer,
    private val tokens: IdTokenSource,
    private val port: SnapshotPort,
    private val safety: SafetyCopy,
    private val store: SyncStateStore,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val lock = Mutex()

    /** What the screen may show: when this account last synced successfully (phone clock), or null. */
    fun lastSyncedAt(uid: String): Long? = store.load(uid)?.syncedAtMs

    suspend fun sync(): SyncOutcome = lock.withLock { guarded { runSync() } }

    /** The user chose to keep this phone's data: upload it over whatever the server has. */
    suspend fun resolveKeepThisPhone(): SyncOutcome = lock.withLock {
        guarded {
            val ctx = start() ?: return@guarded SyncOutcome.NotSignedIn
            val meta = when (val m = server.meta(ctx.token)) {
                is SyncApiResult.Ok -> m.value
                else -> return@guarded failure(m)
            }
            upload(ctx, ctx.localExport, meta.revision)
        }
    }

    /** The user chose the cloud copy: keep a safety copy of this phone's data, then replace it. */
    suspend fun resolveUseCloud(): SyncOutcome = lock.withLock {
        guarded {
            val ctx = start() ?: return@guarded SyncOutcome.NotSignedIn
            download(ctx)
        }
    }

    // ------------------------------------------------------------------------------------------

    private class Ctx(val uid: String, val token: String, val localExport: String)

    private suspend fun guarded(block: suspend () -> SyncOutcome): SyncOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Typically no internet while a token was fetched. Never a crash, never an error for the user.
        SyncOutcome.Offline
    }

    private suspend fun start(): Ctx? {
        val uid = tokens.currentUid ?: return null
        val token = tokens.idToken(false) ?: return null
        return Ctx(uid, token, port.export())
    }

    private suspend fun runSync(): SyncOutcome {
        val ctx = start() ?: return SyncOutcome.NotSignedIn
        val meta = when (val m = server.meta(ctx.token)) {
            is SyncApiResult.Ok -> m.value
            else -> return failure(m)
        }
        if (tokens.currentUid != ctx.uid) return accountChanged()

        val record = store.load(ctx.uid)
        val empty = SnapshotInfo.isEmpty(ctx.localExport)
        val fingerprint = SnapshotFingerprint.of(ctx.localExport)
        val changed = if (record == null) !empty else fingerprint != record.fingerprint
        return when (val action = SyncDecision.decide(LocalSyncState(record?.revision, changed, empty), meta.revision)) {
            SyncAction.Nothing -> SyncOutcome.AlreadyInSync
            is SyncAction.Upload -> upload(ctx, ctx.localExport, action.baseRevision)
            SyncAction.Download -> download(ctx)
            SyncAction.Conflict -> SyncOutcome.NeedsChoice(meta.revision)
        }
    }

    private suspend fun upload(ctx: Ctx, exported: String, baseRevision: Int): SyncOutcome {
        val fingerprint = SnapshotFingerprint.of(exported)
        return when (val r = server.put(ctx.token, baseRevision, exported)) {
            is SyncApiResult.Ok -> {
                // Recorded for the account the data belonged to, even if another account signed in meanwhile.
                store.save(ctx.uid, SyncRecord(r.value, fingerprint, nowMs()))
                SyncOutcome.Uploaded
            }
            is SyncApiResult.Conflict -> SyncOutcome.NeedsChoice(r.serverRevision)
            else -> failure(r)
        }
    }

    private suspend fun download(ctx: Ctx): SyncOutcome {
        val downloaded = when (val d = server.data(ctx.token)) {
            is SyncApiResult.Ok -> d.value
            SyncApiResult.NothingStored -> return SyncOutcome.TryLater
            else -> return failure(d)
        }
        if (tokens.currentUid != ctx.uid) return accountChanged()
        // Nothing is replaced unless the phone's current data was kept first.
        if (!SnapshotInfo.isEmpty(ctx.localExport) && !safety.save(ctx.localExport)) {
            return SyncOutcome.Problem("Не успеав да направам копија од податоците на телефонот, па ништо не е заменето.")
        }
        if (!port.import(downloaded.data)) {
            return SyncOutcome.Problem("Податоците од сервисот не можеа да се вчитаат и ништо не е променето.")
        }
        // What the phone looks like NOW is what counts as "in sync with revision N".
        store.save(ctx.uid, SyncRecord(downloaded.revision, SnapshotFingerprint.of(port.export()), nowMs()))
        return SyncOutcome.Downloaded
    }

    private fun accountChanged() = SyncOutcome.Problem("Сметката се промени додека траеше синхронизацијата. Ништо не е променето.")

    private fun failure(r: SyncApiResult<*>): SyncOutcome = when (r) {
        SyncApiResult.Offline -> SyncOutcome.Offline
        SyncApiResult.RateLimited, SyncApiResult.ServerUnavailable -> SyncOutcome.TryLater
        SyncApiResult.TooLarge -> SyncOutcome.TooLarge
        SyncApiResult.Unauthorized -> SyncOutcome.Problem("Сметката не беше потврдена. Обиди се повторно.")
        else -> SyncOutcome.Problem("Синхронизацијата не успеа. Обиди се повторно.")
    }
}
