package com.mojrucek.app.session

import android.content.Context
import com.mojrucek.app.repository.UnlockedPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** What the list permission needs from the unlocked-details storage. */
interface UnlockedStore {
    /** True when the details of the server recipe [serverId] are unlocked right now for the signed-in account. */
    suspend fun isUnlocked(serverId: Long): Boolean

    /** Replaces everything unlocked for [uid] by the server's answer. [unlockedUntil] is server time; null for Paid. */
    suspend fun replace(uid: String, payloads: List<UnlockedPayload>, unlockedUntil: Long?)
}

/** What the phone remembers of where the user stands with the list rules, per account. Times are SERVER times. */
@kotlinx.serialization.Serializable
data class StoredLimits(
    val plan: String,
    val guestExpired: Boolean,
    val nextAllowedAt: Long?,
    val unlockedUntil: Long?,
    val clockOffsetMs: Long,
    val savedAtMs: Long
)

interface LimitStateStore {
    fun load(uid: String): StoredLimits?
    fun save(uid: String, limits: StoredLimits)
    fun forget(uid: String)
}

class InMemoryLimitStateStore : LimitStateStore {
    private val map = HashMap<String, StoredLimits>()
    override fun load(uid: String) = map[uid]
    override fun save(uid: String, limits: StoredLimits) { map[uid] = limits }
    override fun forget(uid: String) { map.remove(uid) }
}

class SharedPreferencesLimitStateStore(context: Context) : LimitStateStore {
    private val prefs = context.applicationContext.getSharedPreferences("limits", Context.MODE_PRIVATE)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    override fun load(uid: String): StoredLimits? {
        val text = prefs.getString("l_$uid", null) ?: return null
        return try { json.decodeFromString(StoredLimits.serializer(), text) } catch (e: Exception) { null }
    }
    override fun save(uid: String, limits: StoredLimits) {
        prefs.edit().putString("l_$uid", json.encodeToString(StoredLimits.serializer(), limits)).apply()
    }
    override fun forget(uid: String) { prefs.edit().remove("l_$uid").apply() }
}

/**
 * A request that was sent but whose answer may not have arrived. Remembered so that asking again for the SAME recipes
 * reuses the same request id, and the server then returns the same answer without using up another list.
 */
interface PendingRequestStore {
    fun get(uid: String, key: String): String?
    fun set(uid: String, key: String, requestId: String)
    fun clear(uid: String)
}

class InMemoryPendingRequests : PendingRequestStore {
    private val map = HashMap<String, Pair<String, String>>()
    override fun get(uid: String, key: String) = map[uid]?.takeIf { it.first == key }?.second
    override fun set(uid: String, key: String, requestId: String) { map[uid] = key to requestId }
    override fun clear(uid: String) { map.remove(uid) }
}

class SharedPreferencesPendingRequests(context: Context) : PendingRequestStore {
    private val prefs = context.applicationContext.getSharedPreferences("list_requests", Context.MODE_PRIVATE)
    override fun get(uid: String, key: String): String? = prefs.getString("r_$uid", null)?.split("|", limit = 2)?.takeIf { it.size == 2 && it[0] == key }?.get(1)
    override fun set(uid: String, key: String, requestId: String) { prefs.edit().putString("r_$uid", "$key|$requestId").commit() }
    override fun clear(uid: String) { prefs.edit().remove("r_$uid").commit() }
}

/** What the planner is told before it builds a shopping list. */
sealed interface PermissionResult {
    /** Go ahead; the details of the chosen recipes were unlocked just now. */
    data object Allowed : PermissionResult

    /** Go ahead; no server recipe is involved, or all of them are already unlocked. Nothing was asked. */
    data object NotNeeded : PermissionResult

    data class TooSoon(val nextAllowedAt: Long, val serverNowMs: Long) : PermissionResult
    data object GuestExpired : PermissionResult
    data class TooMany(val max: Int) : PermissionResult
    data object NeedsInternet : PermissionResult

    /** The server is busy or not ready: try again in a moment. */
    data object TryLater : PermissionResult

    /** Anything else (sign-in not accepted, unexpected answer). [message] is plain text for the user. */
    data class Problem(val message: String) : PermissionResult
}

/**
 * Asks the server for permission to make a shopping list that involves server recipes (the Guest/Free/Paid rules live on
 * the server). Nothing is asked when the list involves no server recipe, or when all of them are unlocked already:
 * then no new details are involved, so no new list is used up. Everything else is decided by the server.
 */
class ListPermission(
    private val server: GenerationServer,
    private val tokens: IdTokenSource,
    private val unlocked: UnlockedStore,
    private val limits: LimitStateStore,
    private val pending: PendingRequestStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() }
) {
    private val lock = Mutex()

    suspend fun request(serverRecipeIds: Set<Long>): PermissionResult = lock.withLock { requestLocked(serverRecipeIds) }

    private suspend fun requestLocked(serverRecipeIds: Set<Long>): PermissionResult {
        // Without accounts (a build without sign-in) or with no server recipe there is nothing to ask.
        val uid = tokens.currentUid ?: return PermissionResult.NotNeeded
        if (serverRecipeIds.isEmpty()) return PermissionResult.NotNeeded
        if (serverRecipeIds.all { unlocked.isUnlocked(it) }) return PermissionResult.NotNeeded

        val ids = serverRecipeIds.sorted()
        val key = ids.joinToString(",")
        val requestId = pending.get(uid, key) ?: newRequestId().also { pending.set(uid, key, it) }
        try {
            val token = tokens.idToken(false) ?: return PermissionResult.NeedsInternet
            var result = server.generate(token, ids, requestId)
            if (result is GenerationApiResult.Unauthorized) {
                val fresh = tokens.idToken(true) ?: return PermissionResult.NeedsInternet
                if (tokens.currentUid != uid) return PermissionResult.Problem("Сметката се промени. Обиди се повторно.")
                result = server.generate(fresh, ids, requestId)
            }
            if (tokens.currentUid != uid) return PermissionResult.Problem("Сметката се промени. Обиди се повторно.")
            return when (result) {
                is GenerationApiResult.Ok -> {
                    val g = result.value
                    unlocked.replace(uid, g.recipes.map { UnlockedPayload(it.id, it.description, it.instructionSteps, it.notes, it.tags) }, g.unlockedUntil)
                    remember(uid, g.serverTime, result.deviceNowAtResponseMs, plan = null, guestExpired = false, nextAllowedAt = g.nextAllowedAt, unlockedUntil = g.unlockedUntil)
                    pending.clear(uid)
                    PermissionResult.Allowed
                }
                is GenerationApiResult.Refused -> {
                    pending.clear(uid)    // a definite "no": the next try is a new request
                    refused(uid, result)
                }
                GenerationApiResult.Offline -> PermissionResult.NeedsInternet
                GenerationApiResult.RateLimited, GenerationApiResult.ServerUnavailable -> PermissionResult.TryLater
                GenerationApiResult.Unauthorized -> PermissionResult.Problem("Сметката не беше потврдена. Обиди се повторно.")
                is GenerationApiResult.Failed -> PermissionResult.Problem("Не успеав да ја проверам листата. Обиди се повторно.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Typically no internet while fetching a token. The request id is kept for the retry.
            return PermissionResult.NeedsInternet
        }
    }

    private fun refused(uid: String, r: GenerationApiResult.Refused): PermissionResult = when (r.code) {
        "TOO_SOON" -> {
            val next = r.nextAllowedAt
            val serverNow = r.serverTime ?: nowMs()
            if (next == null) PermissionResult.TryLater else {
                limits.load(uid)?.let { limits.save(uid, it.copy(nextAllowedAt = next, savedAtMs = nowMs(), clockOffsetMs = serverNow - nowMs())) }
                    ?: limits.save(uid, StoredLimits("", false, next, null, serverNow - nowMs(), nowMs()))
                PermissionResult.TooSoon(next, serverNow)
            }
        }
        "GUEST_EXPIRED" -> PermissionResult.GuestExpired
        "TOO_MANY_RECIPES" -> PermissionResult.TooMany(r.max ?: 3)
        else -> PermissionResult.Problem("Листата не е одобрена. Обиди се повторно.")
    }

    /** Fetches where the user stands and what is unlocked (for a new phone, a new sign-in, the countdown). Quiet on failure. */
    suspend fun refreshState(): Boolean = lock.withLock { refreshStateLocked() }

    private suspend fun refreshStateLocked(): Boolean {
        val uid = tokens.currentUid ?: return false
        try {
            val token = tokens.idToken(false) ?: return false
            val result = server.state(token)
            if (result !is GenerationApiResult.Ok || tokens.currentUid != uid) return false
            val s = result.value
            unlocked.replace(uid, s.recipes.map { UnlockedPayload(it.id, it.description, it.instructionSteps, it.notes, it.tags) }, s.unlockedUntil)
            remember(uid, s.serverTime, result.deviceNowAtResponseMs, s.plan, s.guestExpired, s.nextAllowedAt, s.unlockedUntil)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
    }

    /** What the screen may say about the next list, from what was last remembered (never asks the server). */
    fun remembered(uid: String): StoredLimits? = limits.load(uid)

    private fun remember(uid: String, serverTime: Long, deviceNowAtResponse: Long, plan: String?, guestExpired: Boolean, nextAllowedAt: Long?, unlockedUntil: Long?) {
        val previousPlan = limits.load(uid)?.plan ?: ""
        limits.save(uid, StoredLimits(plan ?: previousPlan, guestExpired, nextAllowedAt, unlockedUntil, serverTime - deviceNowAtResponse, nowMs()))
    }
}
