package com.mojrucek.app.session

import com.mojrucek.app.repository.UnlockedPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ListPermissionTest {

    private val now = 1_800_000_000_000L

    private class FakeServer : GenerationServer {
        class Call(val token: String, val ids: List<Long>, val requestId: String)
        val calls = mutableListOf<Call>()
        var stateCalls = 0
        val script = ArrayDeque<GenerationApiResult<ServerGeneration>>()
        var stateAnswer: GenerationApiResult<ServerLimitState> = GenerationApiResult.Offline
        override suspend fun generate(idToken: String, recipeIds: List<Long>, requestId: String): GenerationApiResult<ServerGeneration> {
            calls += Call(idToken, recipeIds, requestId)
            return if (script.size > 1) script.removeFirst() else script.first()
        }
        override suspend fun state(idToken: String): GenerationApiResult<ServerLimitState> { stateCalls++; return stateAnswer }
    }

    private class FakeUnlocked : UnlockedStore {
        val open = mutableSetOf<Long>()
        var replaced: Triple<String, List<UnlockedPayload>, Long?>? = null
        override suspend fun isUnlocked(serverId: Long) = serverId in open
        override suspend fun replace(uid: String, payloads: List<UnlockedPayload>, unlockedUntil: Long?) {
            replaced = Triple(uid, payloads, unlockedUntil)
            open.clear(); open += payloads.map { it.serverId }
        }
    }

    private class Tokens(override var currentUid: String? = "uid-A") : IdTokenSource {
        var fail = false
        var forced = 0
        override suspend fun idToken(forceRefresh: Boolean): String? {
            if (fail) throw IllegalStateException("no network")
            if (forceRefresh) forced++
            return if (forceRefresh) "fresh" else "tok"
        }
    }

    private class Env(val server: FakeServer = FakeServer(), val unlocked: FakeUnlocked = FakeUnlocked(), val tokens: Tokens = Tokens(),
                      val limits: InMemoryLimitStateStore = InMemoryLimitStateStore(), val pending: InMemoryPendingRequests = InMemoryPendingRequests(), now: Long) {
        var n = 0
        val permission = ListPermission(server, tokens, unlocked, limits, pending, { now }, { "req-${++n}-xxxxxxxx" })
    }

    private fun ok(vararg ids: Long, until: Long? = now + 72 * 3600_000L, next: Long? = until) =
        GenerationApiResult.Ok(ServerGeneration(5, now, until, next, ids.map { ServerRecipeDetails(it, "опис $it", listOf("чекор"), null, emptyList()) }, now), now)

    private fun env(vararg answers: GenerationApiResult<ServerGeneration>) = Env(now = now).also { e -> answers.forEach { e.server.script += it } }

    // ---------- nothing to ask ----------

    @Test fun noServerRecipe_noQuestion() = runBlocking {
        val e = env(ok(1))
        assertEquals(PermissionResult.NotNeeded, e.permission.request(emptySet()))
        assertTrue(e.server.calls.isEmpty())
    }

    @Test fun nobodySignedIn_aBuildWithoutAccounts_noQuestion() = runBlocking {
        val e = env(ok(1)).also { it.tokens.currentUid = null }
        assertEquals(PermissionResult.NotNeeded, e.permission.request(setOf(1)))
        assertTrue(e.server.calls.isEmpty())
    }

    @Test fun everythingAlreadyUnlocked_noQuestion_soARepeatListUsesNothingUp() = runBlocking {
        val e = env(ok(1)).also { it.unlocked.open += setOf(1L, 2L) }
        assertEquals(PermissionResult.NotNeeded, e.permission.request(setOf(1, 2)))
        assertEquals(PermissionResult.NotNeeded, e.permission.request(setOf(2)))
        assertTrue(e.server.calls.isEmpty())
    }

    @Test fun oneNewRecipe_amongUnlockedOnes_isAskedAbout() = runBlocking {
        val e = env(ok(1, 3)).also { it.unlocked.open += 1L }
        e.permission.request(setOf(1, 3))
        assertEquals(1, e.server.calls.size)
    }

    // ---------- the question ----------

    @Test fun approved_savesTheDetails_remembersTheNextTime_andSendsSortedIdsWithTheToken() = runBlocking {
        val e = env(ok(3, 1, 2))
        assertEquals(PermissionResult.Allowed, e.permission.request(setOf(3, 1, 2)))

        val call = e.server.calls.single()
        assertEquals(listOf(1L, 2L, 3L), call.ids)
        assertEquals("tok", call.token)
        val (uid, payloads, until) = e.unlocked.replaced!!
        assertEquals("uid-A", uid)
        assertEquals(setOf(1L, 2L, 3L), payloads.map { it.serverId }.toSet())
        assertEquals(now + 72 * 3600_000L, until)
        assertEquals(now + 72 * 3600_000L, e.permission.remembered("uid-A")!!.nextAllowedAt)
    }

    @Test fun paidAnswer_withoutDeadline_isKeptWithoutDeadline() = runBlocking {
        val e = env(ok(1, until = null, next = null))
        e.permission.request(setOf(1))
        assertNull(e.unlocked.replaced!!.third)
    }

    @Test fun tooSoon_saysWhen_andSavesNothing() = runBlocking {
        val next = now + 5 * 3600_000L
        val e = env(GenerationApiResult.Refused("TOO_SOON", next, null, now))
        val r = e.permission.request(setOf(1))
        assertEquals(PermissionResult.TooSoon(next, now), r)
        assertNull(e.unlocked.replaced)
        assertEquals(next, e.permission.remembered("uid-A")!!.nextAllowedAt)
    }

    @Test fun otherRefusals_areMappedToTheirOwnResult() = runBlocking {
        assertEquals(PermissionResult.GuestExpired, env(GenerationApiResult.Refused("GUEST_EXPIRED", null, null, null)).permission.request(setOf(1)))
        assertEquals(PermissionResult.TooMany(3), env(GenerationApiResult.Refused("TOO_MANY_RECIPES", null, 3, null)).permission.request(setOf(1)))
        assertTrue(env(GenerationApiResult.Refused("SOMETHING_NEW", null, null, null)).permission.request(setOf(1)) is PermissionResult.Problem)
        assertTrue(env(GenerationApiResult.Refused("TOO_SOON", null, null, null)).permission.request(setOf(1)) is PermissionResult.TryLater)
    }

    @Test fun troubleOutcomes_areMapped() = runBlocking {
        assertEquals(PermissionResult.NeedsInternet, env(GenerationApiResult.Offline).permission.request(setOf(1)))
        assertEquals(PermissionResult.TryLater, env(GenerationApiResult.RateLimited).permission.request(setOf(1)))
        assertEquals(PermissionResult.TryLater, env(GenerationApiResult.ServerUnavailable).permission.request(setOf(1)))
        assertTrue(env(GenerationApiResult.Failed("x")).permission.request(setOf(1)) is PermissionResult.Problem)
    }

    @Test fun noTokenBecauseNoInternet_needsInternet_andNothingIsSent() = runBlocking {
        val e = env(ok(1)).also { it.tokens.fail = true }
        assertEquals(PermissionResult.NeedsInternet, e.permission.request(setOf(1)))
        assertTrue(e.server.calls.isEmpty())
    }

    // ---------- safe retry ----------

    @Test fun afterALostAnswer_theSameRecipesAreAskedWithTheSameRequestId() = runBlocking {
        val e = env(GenerationApiResult.Offline, ok(1))
        e.permission.request(setOf(1))
        val second = e.permission.request(setOf(1))

        assertEquals(PermissionResult.Allowed, second)
        assertEquals(2, e.server.calls.size)
        assertEquals(e.server.calls[0].requestId, e.server.calls[1].requestId)
    }

    @Test fun differentRecipes_getANewRequestId() = runBlocking {
        val e = env(GenerationApiResult.Offline)
        e.permission.request(setOf(1))
        e.permission.request(setOf(2))
        assertTrue(e.server.calls[0].requestId != e.server.calls[1].requestId)
    }

    @Test fun afterADefiniteAnswer_theNextRequestIsNew() = runBlocking {
        val e = env(GenerationApiResult.Refused("TOO_SOON", now + 1000, null, now), ok(1))
        e.permission.request(setOf(1))
        e.unlocked.open.clear()
        e.permission.request(setOf(1))
        assertTrue(e.server.calls[0].requestId != e.server.calls[1].requestId)
    }

    @Test fun serverBusy_keepsTheRequestId_forTheRetry() = runBlocking {
        val e = env(GenerationApiResult.ServerUnavailable, ok(1))
        e.permission.request(setOf(1)); e.permission.request(setOf(1))
        assertEquals(e.server.calls[0].requestId, e.server.calls[1].requestId)
    }

    // ---------- tokens and accounts ----------

    @Test fun aRefusedToken_isRenewedOnce_andTheSameRequestIsRepeated() = runBlocking {
        val e = env(GenerationApiResult.Unauthorized, ok(1))
        assertEquals(PermissionResult.Allowed, e.permission.request(setOf(1)))
        assertEquals(listOf("tok", "fresh"), e.server.calls.map { it.token })
        assertEquals(e.server.calls[0].requestId, e.server.calls[1].requestId)
        assertEquals(1, e.tokens.forced)
    }

    @Test fun aTokenRefusedTwice_isAProblem_neverALoop() = runBlocking {
        val e = env(GenerationApiResult.Unauthorized)
        assertTrue(e.permission.request(setOf(1)) is PermissionResult.Problem)
        assertEquals(2, e.server.calls.size)
    }

    @Test fun ifAnotherAccountSignsInDuringTheCall_nothingIsSaved() = runBlocking {
        val e = env(ok(1))
        val tokens = e.tokens
        val server = object : GenerationServer by e.server {
            override suspend fun generate(idToken: String, recipeIds: List<Long>, requestId: String): GenerationApiResult<ServerGeneration> {
                tokens.currentUid = "uid-B"
                return e.server.generate(idToken, recipeIds, requestId)
            }
        }
        val permission = ListPermission(server, tokens, e.unlocked, e.limits, e.pending, { now })
        assertTrue(permission.request(setOf(1)) is PermissionResult.Problem)
        assertNull(e.unlocked.replaced)
        assertNull(e.limits.load("uid-B"))
    }

    @Test fun cancellingTheWork_isNeverSwallowed() {
        val e = env(ok(1))
        val server = object : GenerationServer by e.server {
            override suspend fun generate(idToken: String, recipeIds: List<Long>, requestId: String): GenerationApiResult<ServerGeneration> =
                throw CancellationException("screen closed")
        }
        try {
            runBlocking { ListPermission(server, e.tokens, e.unlocked, e.limits, e.pending, { now }).request(setOf(1)) }
            fail("must propagate")
        } catch (x: CancellationException) { }
    }

    // ---------- restoring the state ----------

    @Test fun refreshState_restoresUnlockedDetails_andTheCountdown() = runBlocking {
        val e = env(ok(1))
        e.server.stateAnswer = GenerationApiResult.Ok(
            ServerLimitState("FREE", false, false, now + 3600_000L, now + 3600_000L, listOf(ServerRecipeDetails(4, "о", listOf("с"), null, emptyList())), now), now
        )
        assertTrue(e.permission.refreshState())
        assertEquals(setOf(4L), e.unlocked.open)
        val l = e.permission.remembered("uid-A")!!
        assertEquals("FREE", l.plan)
        assertEquals(now + 3600_000L, l.nextAllowedAt)
    }

    @Test fun refreshState_isQuietOnFailure_andChangesNothing() = runBlocking {
        val e = env(ok(1))
        for (answer in listOf(GenerationApiResult.Offline, GenerationApiResult.Unauthorized, GenerationApiResult.ServerUnavailable)) {
            e.server.stateAnswer = answer
            assertEquals(false, e.permission.refreshState())
        }
        assertNull(e.unlocked.replaced)
        assertNull(e.permission.remembered("uid-A"))
    }

    @Test fun refreshState_withNobodySignedIn_doesNothing() = runBlocking {
        val e = env(ok(1)).also { it.tokens.currentUid = null }
        assertEquals(false, e.permission.refreshState())
        assertEquals(0, e.server.stateCalls)
    }
}
