package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.UnlockedDetailsDao
import com.mojrucek.app.data.entity.UnlockedRecipeDetail
import com.mojrucek.app.domain.limits.RecipeAccess
import com.mojrucek.app.domain.limits.RecipeAccessRules
import com.mojrucek.app.domain.limits.RecipeDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** One recipe's details as the server sent them with an approved list. */
data class UnlockedPayload(
    val serverId: Long,
    val description: String?,
    val instructionSteps: List<String>,
    val notes: String?,
    val tags: List<String>
)

/**
 * The unlocked details of the signed-in account. [serverNowMs] must be server-corrected time (see ServerClock), so a
 * wrong phone clock can neither extend nor cut short an unlock.
 */
class UnlockedDetailsRepository(
    private val dao: UnlockedDetailsDao,
    private val owner: CurrentOwner,
    private val serverNowMs: () -> Long,
    private val deviceNowMs: () -> Long = System::currentTimeMillis
) {
    /**
     * Replaces everything unlocked for [ownerKey] with [payloads] (the answer of the server for a new list, or
     * the state restored at sign-in). [unlockedUntil] is the server deadline; null for Paid.
     */
    suspend fun replaceUnlocked(ownerKey: String, payloads: List<UnlockedPayload>, unlockedUntil: Long?) {
        if (!OwnerKeys.isAccount(ownerKey)) return    // unlocks belong to real accounts only
        val saved = deviceNowMs()
        dao.replaceAll(ownerKey, payloads.map {
            UnlockedRecipeDetail(ownerKey, it.serverId, it.description, it.instructionSteps, it.notes, it.tags, unlockedUntil, saved)
        })
    }

    /** What the screen may show for one recipe of the current account. */
    suspend fun accessFor(isOwn: Boolean, serverId: Long?): RecipeAccess {
        if (isOwn || serverId == null) return RecipeAccessRules.access(isOwn, null, serverNowMs())
        return RecipeAccessRules.access(false, dao.find(owner.key, serverId)?.toStored(), serverNowMs())
    }

    /** All unlocked recipes of the current account, as a map by server id (expired ones are left out). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUnlocked(): Flow<Map<Long, RecipeAccess.Unlocked>> =
        owner.flow.flatMapLatest { dao.observeAll(it) }.map { rows ->
            val now = serverNowMs()
            rows.mapNotNull { row ->
                (RecipeAccessRules.access(false, row.toStored(), now) as? RecipeAccess.Unlocked)?.let { row.serverId to it }
            }.toMap()
        }

    /** Removes what has run out. Cheap; call at start and when the screen opens. */
    suspend fun pruneExpired() {
        val key = owner.key
        if (OwnerKeys.isAccount(key)) dao.deleteExpired(key, serverNowMs())
    }

    private fun UnlockedRecipeDetail.toStored() =
        RecipeAccessRules.StoredDetails(RecipeDetails(description, instructionSteps, notes, tags), unlockedUntil)
}
