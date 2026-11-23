package com.mojrucek.app.session

import com.mojrucek.app.auth.AccountKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeRefresher(var next: RefreshOutcome = RefreshOutcome.Fresh) : SessionRefresher {
    data class Call(val uid: String, val expectedKind: String?, val forced: Boolean)
    val calls = mutableListOf<Call>()
    var failWith: Throwable? = null
    override suspend fun refresh(uid: String): RefreshOutcome { failWith?.let { throw it }; calls += Call(uid, null, true); return next }
    override suspend fun refreshIfStale(uid: String, expectedKind: String?): RefreshOutcome { failWith?.let { throw it }; calls += Call(uid, expectedKind, false); return next }
}

private class FakeRetry : RetryScheduler {
    var scheduled = 0
    var cancelled = 0
    override fun scheduleRetry() { scheduled++ }
    override fun cancelRetry() { cancelled++ }
}

private fun stored(kind: String) = StoredSession("usr_1", kind, "GUEST", null, null, 0, 0)

class SessionTriggersTest {

    private fun run(kind: AccountKind?, outcome: RefreshOutcome, uid: String? = "uid-A"): Pair<FakeRefresher, FakeRetry> {
        val refresher = FakeRefresher(outcome)
        val retry = FakeRetry()
        runBlocking { SessionTriggers(refresher, retry).onAccount(uid, kind) }
        return refresher to retry
    }

    // ---------- when the server is asked ----------

    @Test fun nobodySignedIn_doesNothing() {
        val (refresher, retry) = run(null, RefreshOutcome.Retry("x"), uid = null)

        assertTrue(refresher.calls.isEmpty())
        assertEquals(0, retry.scheduled + retry.cancelled)
    }

    @Test fun anAccountIsAsked_onlyIfStale_andNeverForcedByTheTrigger() {
        val (refresher, _) = run(AccountKind.GUEST, RefreshOutcome.Fresh)

        assertEquals(listOf(FakeRefresher.Call("uid-A", "GUEST", forced = false)), refresher.calls)
    }

    @Test fun theExpectedServerTypeFollowsTheAccountType() {
        assertEquals("GUEST", ExpectedKind.of(AccountKind.GUEST))
        assertEquals("REGISTERED", ExpectedKind.of(AccountKind.GOOGLE))
        assertEquals("REGISTERED", ExpectedKind.of(AccountKind.EMAIL))
        assertNull("an unknown type never forces a refresh", ExpectedKind.of(AccountKind.OTHER))
        assertNull(ExpectedKind.of(null))
        for (kind in AccountKind.entries) ExpectedKind.of(kind)   // every type is covered (no crash on a new one)
        assertEquals("REGISTERED", run(AccountKind.GOOGLE, RefreshOutcome.Fresh).first.calls.single().expectedKind)
        assertNull(run(AccountKind.OTHER, RefreshOutcome.Fresh).first.calls.single().expectedKind)
    }

    // ---------- what happens with the outcome ----------

    @Test fun temporaryTrouble_plansOneBackgroundRetry() {
        val (_, retry) = run(AccountKind.GOOGLE, RefreshOutcome.Retry("no connection"))

        assertEquals(1, retry.scheduled)
        assertEquals(0, retry.cancelled)
    }

    @Test fun aSuccessfulRefresh_cancelsAPendingRetry() {
        val (_, retry) = run(AccountKind.GOOGLE, RefreshOutcome.Updated(stored("REGISTERED")))

        assertEquals(1, retry.cancelled)
        assertEquals(0, retry.scheduled)
    }

    @Test fun everyOtherOutcome_leavesTheRetryAlone() {
        for (outcome in listOf(RefreshOutcome.Fresh, RefreshOutcome.GaveUp("refused"), RefreshOutcome.NoToken, RefreshOutcome.AccountChanged)) {
            val (_, retry) = run(AccountKind.GUEST, outcome)
            assertEquals("$outcome", 0, retry.scheduled + retry.cancelled)
        }
    }

    @Test fun cancellingTheWork_isNeverSwallowed() {
        val refresher = FakeRefresher().apply { failWith = CancellationException("a newer account change arrived") }
        try {
            runBlocking { SessionTriggers(refresher, FakeRetry()).onAccount("uid-A", AccountKind.GUEST) }
            fail("the cancellation must propagate")
        } catch (expected: CancellationException) { }
    }

    // ---------- together with the real repository ----------

    private class Server(vararg answers: SessionApiResult) : SessionServer {
        private val script = answers.toMutableList()
        var calls = 0
        override suspend fun open(idToken: String, deviceNowMs: () -> Long): SessionApiResult { calls++; return if (script.size > 1) script.removeAt(0) else script.first() }
    }

    private class Tokens(override var currentUid: String? = "uid-A") : IdTokenSource {
        override suspend fun idToken(forceRefresh: Boolean): String? = "t"
    }

    private fun answer(kind: String, expires: Long?) =
        SessionApiResult.Ok(ServerSession("usr_1", kind, if (kind == "GUEST") "GUEST" else "FREE", 1, 1, expires, serverTime = 1_800_000_000_000L), 1_800_000_000_000L)

    @Test fun aGuestWhoLinksGoogle_isRefreshedAtOnce_evenThoughTheOldAnswerIsRecent() = runBlocking {
        val server = Server(answer("GUEST", 1_900_000_000_000L), answer("REGISTERED", null))
        val store = InMemorySessionStore()
        val repo = SessionRepository(server, store, Tokens(), nowMs = { 1_800_000_000_000L })
        val triggers = SessionTriggers(repo, FakeRetry())

        triggers.onAccount("uid-A", AccountKind.GUEST)          // app start as a guest: first call
        triggers.onAccount("uid-A", AccountKind.GUEST)          // nothing changed: no second call
        assertEquals(1, server.calls)

        triggers.onAccount("uid-A", AccountKind.GOOGLE)         // the guest links Google a minute later
        assertEquals("the type changed, so it asked again", 2, server.calls)
        assertEquals("REGISTERED", store.load("uid-A")!!.kind)
        assertNull(store.load("uid-A")!!.guestExpiresAt)

        triggers.onAccount("uid-A", AccountKind.GOOGLE)         // and now it is up to date
        assertEquals(2, server.calls)
    }

    @Test fun ifLinkingHappensOffline_theMismatchSurvives_soTheNextStartCatchesUp() = runBlocking {
        val server = Server(answer("GUEST", 1_900_000_000_000L), SessionApiResult.Offline, answer("REGISTERED", null))
        val store = InMemorySessionStore()
        val retry = FakeRetry()
        val repo = SessionRepository(server, store, Tokens(), nowMs = { 1_800_000_000_000L })
        val triggers = SessionTriggers(repo, retry)

        triggers.onAccount("uid-A", AccountKind.GUEST)
        triggers.onAccount("uid-A", AccountKind.GOOGLE)         // offline: cannot update now
        assertEquals("GUEST", store.load("uid-A")!!.kind)
        assertEquals(1, retry.scheduled)

        val cancelledBefore = retry.cancelled
        triggers.onAccount("uid-A", AccountKind.GOOGLE)         // next start, back online: still seen as out of date
        assertEquals("REGISTERED", store.load("uid-A")!!.kind)
        assertEquals("the catch-up cancels the now useless retry", cancelledBefore + 1, retry.cancelled)
    }

    @Test fun offlineAtFirstSignIn_plansARetry_andNothingIsRemembered() = runBlocking {
        val server = Server(SessionApiResult.Offline)
        val store = InMemorySessionStore()
        val retry = FakeRetry()

        SessionTriggers(SessionRepository(server, store, Tokens()), retry).onAccount("uid-A", AccountKind.GUEST)

        assertEquals(1, retry.scheduled)
        assertNull(store.load("uid-A"))
    }
}
