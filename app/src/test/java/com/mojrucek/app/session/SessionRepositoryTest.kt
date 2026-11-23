package com.mojrucek.app.session

import com.mojrucek.app.domain.session.GuestPeriod
import com.mojrucek.app.session.FakeServer.Reply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

private const val DAY = 24L * 60 * 60 * 1000

/** A server that answers from a script, one answer per call, and remembers what it was asked. */
private class ScriptedServer(vararg answers: SessionApiResult, private val delayMs: Long = 0, private val onCall: () -> Unit = {}) : SessionServer {
    private val script = answers.toMutableList()
    val tokensSeen = mutableListOf<String>()
    val calls get() = tokensSeen.size
    override suspend fun open(idToken: String, deviceNowMs: () -> Long): SessionApiResult {
        tokensSeen += idToken
        onCall()
        if (delayMs > 0) delay(delayMs)
        return if (script.size > 1) script.removeAt(0) else script.first()
    }
}

private class FakeTokens(var uid: String? = "uid-A") : IdTokenSource {
    override val currentUid: String? get() = uid
    var token: String? = "t1"
    var refreshedToken: String? = "t2"
    var forceRefreshes = 0
    var failWith: Throwable? = null
    override suspend fun idToken(forceRefresh: Boolean): String? {
        failWith?.let { throw it }
        if (forceRefresh) { forceRefreshes++; return refreshedToken }
        return token
    }
}

class SessionRepositoryTest {

    private val serverNow = 1_800_000_000_000L
    private fun guest(start: Long = serverNow) = ServerSession("usr_1", "GUEST", "GUEST", start, start, start + 14 * DAY, serverNow)
    private fun registered() = ServerSession("usr_1", "REGISTERED", "FREE", serverNow, null, null, serverNow)
    private fun ok(s: ServerSession, deviceNow: Long = serverNow) = SessionApiResult.Ok(s, deviceNow)

    private class Setup(
        val server: SessionServer,
        val tokens: FakeTokens = FakeTokens(),
        val store: InMemorySessionStore = InMemorySessionStore(),
        var now: Long = 1_800_000_000_000L
    ) {
        val repo = SessionRepository(server, store, tokens, nowMs = { now })
    }

    private fun stored(refreshedAt: Long, kind: String = "GUEST") = StoredSession("usr_old", kind, "GUEST", null, null, 0, refreshedAt)

    // ---------- a successful answer is remembered ----------

    @Test fun theAnswerIsStoredPerAccount_withTheClockDifferenceBetweenPhoneAndServer() = runBlocking {
        // The phone's date is two days ahead of the server's.
        val s = Setup(ScriptedServer(ok(guest(), deviceNow = serverNow + 2 * DAY)), now = serverNow + 2 * DAY)

        val outcome = s.repo.refresh("uid-A") as RefreshOutcome.Updated

        assertEquals(s.store.load("uid-A"), outcome.session)
        assertEquals("usr_1", outcome.session.userId)
        assertEquals(-2 * DAY, outcome.session.clockOffsetMs)
        assertEquals(serverNow + 2 * DAY, outcome.session.refreshedAtMs)
        assertEquals("true days left despite the wrong phone date", GuestPeriod.Status.Running(14), outcome.session.guestStatus(serverNow + 2 * DAY))
        assertEquals(outcome.session, s.repo.cached("uid-A"))
        assertNull(s.repo.cached("uid-B"))
    }

    @Test fun aGuestWhoLinksGoogle_becomesRegistered_andTheCountdownDisappears() = runBlocking {
        val s = Setup(ScriptedServer(ok(guest()), ok(registered())))
        s.repo.refresh("uid-A")
        assertTrue(s.repo.cached("uid-A")!!.guestStatus(serverNow) is GuestPeriod.Status.Running)

        s.repo.refresh("uid-A")

        val now = s.repo.cached("uid-A")!!
        assertEquals("REGISTERED", now.kind)
        assertEquals(GuestPeriod.Status.NotAGuest, now.guestStatus(serverNow))
    }

    @Test fun twoAccountsOnOnePhone_neverShareTheirAnswers() = runBlocking {
        val tokens = FakeTokens("uid-A")
        val s = Setup(ScriptedServer(ok(guest()), ok(registered())), tokens)
        s.repo.refresh("uid-A")
        tokens.uid = "uid-B"
        s.repo.refresh("uid-B")

        assertEquals("GUEST", s.repo.cached("uid-A")!!.kind)
        assertEquals("REGISTERED", s.repo.cached("uid-B")!!.kind)
    }

    // ---------- temporary trouble: try again later, change nothing ----------

    @Test fun noInternetOrAServerNotReady_isRetryLater_andTheOldAnswerStays() = runBlocking {
        for (trouble in listOf(SessionApiResult.Offline, SessionApiResult.ServerUnavailable, SessionApiResult.RateLimited)) {
            val s = Setup(ScriptedServer(trouble))
            s.store.save("uid-A", stored(refreshedAt = 123))

            val outcome = s.repo.refresh("uid-A")

            assertTrue("$trouble should be retry-later, was $outcome", outcome is RefreshOutcome.Retry)
            assertEquals("the remembered answer is untouched", stored(123), s.store.load("uid-A"))
        }
    }

    @Test fun aTokenThatCannotBeFetchedWithoutInternet_isRetryLater_notACrash() = runBlocking {
        for (error in listOf<Throwable>(IOException("no network"), IllegalStateException("odd"), RuntimeException("firebase"))) {
            val tokens = FakeTokens().apply { failWith = error }
            val s = Setup(ScriptedServer(ok(guest())), tokens)

            assertTrue("$error", s.repo.refresh("uid-A") is RefreshOutcome.Retry)
            assertEquals("the server was never asked", 0, (s.server as ScriptedServer).calls)
        }
    }

    // ---------- the server refuses the token ----------

    @Test fun anExpiredToken_isReplacedOnce_andTheCallRepeatedOnce() = runBlocking {
        val server = ScriptedServer(SessionApiResult.Unauthorized, ok(guest()))
        val s = Setup(server)

        val outcome = s.repo.refresh("uid-A")

        assertTrue(outcome is RefreshOutcome.Updated)
        assertEquals(listOf("t1", "t2"), server.tokensSeen)
        assertEquals(1, s.tokens.forceRefreshes)
    }

    @Test fun aTokenTheServerKeepsRefusing_givesUpAfterOneRetry_neverLoops() = runBlocking {
        val server = ScriptedServer(SessionApiResult.Unauthorized)
        val s = Setup(server)
        s.store.save("uid-A", stored(refreshedAt = 1))

        val outcome = s.repo.refresh("uid-A")

        assertTrue(outcome is RefreshOutcome.GaveUp)
        assertEquals("exactly two calls: the first and one retry", 2, server.calls)
        assertEquals(stored(1), s.store.load("uid-A"))
    }

    @Test fun anUnusableAnswer_isGivenUpOn_andNothingIsStored() = runBlocking {
        val s = Setup(ScriptedServer(SessionApiResult.Failed("the server's answer could not be read")))

        assertTrue(s.repo.refresh("uid-A") is RefreshOutcome.GaveUp)
        assertNull(s.store.load("uid-A"))
    }

    // ---------- nobody signed in / the account changed ----------

    @Test fun nobodySignedIn_isNoToken_andTheServerIsNotAsked() = runBlocking {
        val server = ScriptedServer(ok(guest()))
        val tokens = FakeTokens().apply { token = null }
        assertEquals(RefreshOutcome.NoToken, Setup(server, tokens).repo.refresh("uid-A"))

        val tokens2 = FakeTokens().apply { refreshedToken = null }
        val server2 = ScriptedServer(SessionApiResult.Unauthorized)
        assertEquals("no new token after a 401", RefreshOutcome.NoToken, Setup(server2, tokens2).repo.refresh("uid-A"))
        assertEquals(0, server.calls)
    }

    @Test fun aRefreshForAnAccountThatIsNotSignedInAnyMore_doesNothing() = runBlocking {
        val server = ScriptedServer(ok(guest()))
        val s = Setup(server, FakeTokens("uid-B"))

        assertEquals(RefreshOutcome.AccountChanged, s.repo.refresh("uid-A"))
        assertEquals(0, server.calls)
        assertNull(s.store.load("uid-A"))
    }

    @Test fun ifTheAccountChangesDuringTheCall_theAnswerIsNotStoredForTheWrongAccount() = runBlocking {
        val tokens = FakeTokens("uid-A")
        val server = ScriptedServer(ok(guest()), onCall = { tokens.uid = "uid-B" })   // switches while the call is in flight
        val s = Setup(server, tokens)

        assertEquals(RefreshOutcome.AccountChanged, s.repo.refresh("uid-A"))
        assertNull(s.store.load("uid-A"))
        assertNull(s.store.load("uid-B"))
    }

    @Test fun cancellingTheWork_isNeverSwallowed() = runBlocking {
        val tokens = FakeTokens().apply { failWith = CancellationException("stop") }
        try {
            Setup(ScriptedServer(ok(guest())), tokens).repo.refresh("uid-A")
            fail("the cancellation must propagate")
        } catch (expected: CancellationException) { }
    }

    // ---------- refreshing only when needed ----------

    @Test fun refreshIfStale_asksTheServer_whenNothingIsRemembered() = runBlocking {
        val server = ScriptedServer(ok(guest()))
        val s = Setup(server)

        assertTrue(s.repo.refreshIfStale("uid-A") is RefreshOutcome.Updated)
        assertEquals(1, server.calls)
    }

    @Test fun refreshIfStale_leavesARecentAnswerAlone_andRefreshesAnOldOne() = runBlocking {
        val hour = 60L * 60 * 1000
        for ((age, shouldAsk) in listOf(0L to false, 1 * hour to false, 11 * hour + 59 * 60_000 to false, 12 * hour to true, 13 * hour to true, 40 * hour to true)) {
            val server = ScriptedServer(ok(guest()))
            val s = Setup(server)
            s.store.save("uid-A", stored(refreshedAt = s.now - age))

            val outcome = s.repo.refreshIfStale("uid-A")

            assertEquals("age ${age / hour} h", shouldAsk, server.calls == 1)
            assertEquals("age ${age / hour} h", if (shouldAsk) true else false, outcome is RefreshOutcome.Updated)
            if (!shouldAsk) assertEquals(RefreshOutcome.Fresh, outcome)
        }
    }

    @Test fun anAnswerRememberedFromTheFuture_countsAsStale_neverAsFreshForever() = runBlocking {
        val server = ScriptedServer(ok(guest()))
        val s = Setup(server)
        s.store.save("uid-A", stored(refreshedAt = s.now + 30 * DAY))   // the phone clock was moved back

        s.repo.refreshIfStale("uid-A")

        assertEquals(1, server.calls)
    }

    @Test fun manyAtOnce_ask_theServerOnlyOnce() = runBlocking {
        val server = ScriptedServer(ok(guest()), delayMs = 50)
        val s = Setup(server)

        val outcomes = (1..6).map { async { s.repo.refreshIfStale("uid-A") } }.awaitAll()

        assertEquals("one call, not six", 1, server.calls)
        assertEquals(1, outcomes.count { it is RefreshOutcome.Updated })
        assertEquals(5, outcomes.count { it == RefreshOutcome.Fresh })
    }

    // ---------- the whole thing over real HTTP ----------

    @Test fun overRealHttp_aFirstSignIn_opensTheSession_andIsRemembered() = runBlocking {
        val fake = FakeServer()
        try {
            val http = HttpSessionServer(fake.baseUrl, allowHttp = true, connectTimeoutMs = 2000, readTimeoutMs = 3000)
            val s = Setup(http)

            val outcome = s.repo.refresh("uid-A")

            assertTrue("$outcome", outcome is RefreshOutcome.Updated)
            assertEquals("Bearer t1", fake.requests.single().headers["authorization"])
            assertEquals("usr_abc", s.repo.cached("uid-A")!!.userId)
        } finally { fake.stop() }
    }

    @Test fun overRealHttp_anExpiredToken_isRetriedOnceWithAFreshOne() = runBlocking {
        val fake = FakeServer()
        try {
            fake.handler = { r -> if (r.headers["authorization"] == "Bearer t1") Reply(401) else Reply(200, FakeServer.GOOD) }
            val s = Setup(HttpSessionServer(fake.baseUrl, allowHttp = true, connectTimeoutMs = 2000, readTimeoutMs = 3000))

            assertTrue(s.repo.refresh("uid-A") is RefreshOutcome.Updated)
            assertEquals(listOf("Bearer t1", "Bearer t2"), fake.requests.map { it.headers["authorization"] })
        } finally { fake.stop() }
    }

    @Test fun overRealHttp_whenTheServerIsDown_itIsRetryLater_andNothingIsLost() = runBlocking {
        val fake = FakeServer()
        val url = fake.baseUrl
        fake.stop()
        val s = Setup(HttpSessionServer(url, allowHttp = true, connectTimeoutMs = 1000, readTimeoutMs = 1000))
        s.store.save("uid-A", stored(refreshedAt = 5))

        assertTrue(s.repo.refresh("uid-A") is RefreshOutcome.Retry)
        assertEquals(stored(5), s.store.load("uid-A"))
        assertFalse(s.repo.cached("uid-A") == null)
    }
}
