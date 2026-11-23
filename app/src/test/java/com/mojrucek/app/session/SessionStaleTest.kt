package com.mojrucek.app.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Stale" now also means: the remembered account type is not the one the account has now. */
class SessionStaleTest {

    private val now = 1_800_000_000_000L

    private class CountingServer(private val answer: SessionApiResult) : SessionServer {
        var calls = 0
        override suspend fun open(idToken: String, deviceNowMs: () -> Long): SessionApiResult { calls++; return answer }
    }

    private class Tokens : IdTokenSource {
        override val currentUid: String = "uid-A"
        override suspend fun idToken(forceRefresh: Boolean): String = "t"
    }

    private fun repo(server: SessionServer, rememberedKind: String?, ageMs: Long = 60_000): Pair<SessionRepository, InMemorySessionStore> {
        val store = InMemorySessionStore()
        if (rememberedKind != null) store.save("uid-A", StoredSession("usr_1", rememberedKind, "GUEST", null, null, 0, refreshedAtMs = now - ageMs))
        return SessionRepository(server, store, Tokens(), nowMs = { now }) to store
    }

    private fun ok(kind: String) = SessionApiResult.Ok(ServerSession("usr_1", kind, "FREE", 1, null, null, now), now)

    @Test fun aRecentAnswer_ofTheRightType_isLeftAlone() = runBlocking {
        val server = CountingServer(ok("GUEST"))
        assertEquals(RefreshOutcome.Fresh, repo(server, "GUEST").first.refreshIfStale("uid-A", "GUEST"))
        assertEquals(0, server.calls)
    }

    @Test fun aRecentAnswer_ofTheWrongType_isRefreshed() = runBlocking {
        for ((remembered, expected) in listOf("GUEST" to "REGISTERED", "REGISTERED" to "GUEST")) {
            val server = CountingServer(ok(expected))
            val (repo, store) = repo(server, remembered)

            assertTrue(repo.refreshIfStale("uid-A", expected) is RefreshOutcome.Updated)

            assertEquals(1, server.calls)
            assertEquals(expected, store.load("uid-A")!!.kind)
        }
    }

    @Test fun noExpectation_neverForcesARefresh_whateverIsRemembered() = runBlocking {
        for (remembered in listOf("GUEST", "REGISTERED", "SOMETHING_NEW")) {
            val server = CountingServer(ok("GUEST"))
            assertEquals(RefreshOutcome.Fresh, repo(server, remembered).first.refreshIfStale("uid-A", null))
            assertEquals(0, server.calls)
        }
    }

    @Test fun aWrongTypeThatCannotBeFixedNow_keepsTheOldAnswer_andSaysTryLater() = runBlocking {
        val (repo, store) = repo(CountingServer(SessionApiResult.Offline), "GUEST")

        assertTrue(repo.refreshIfStale("uid-A", "REGISTERED") is RefreshOutcome.Retry)
        assertEquals("GUEST", store.load("uid-A")!!.kind)
    }

    @Test fun nothingRemembered_isAskedWhateverTheExpectation() = runBlocking {
        for (expected in listOf(null, "GUEST", "REGISTERED")) {
            val server = CountingServer(ok("GUEST"))
            assertTrue(repo(server, null).first.refreshIfStale("uid-A", expected) is RefreshOutcome.Updated)
            assertEquals(1, server.calls)
        }
    }

    @Test fun anOldAnswer_isRefreshed_evenOfTheRightType() = runBlocking {
        val server = CountingServer(ok("GUEST"))
        assertTrue(repo(server, "GUEST", ageMs = 13L * 60 * 60 * 1000).first.refreshIfStale("uid-A", "GUEST") is RefreshOutcome.Updated)
    }
}
