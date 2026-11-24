package com.mojrucek.app.session

import com.mojrucek.app.auth.AccountKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailKindTest {

    @Test fun anEmailAccount_isRegisteredOnlyOnceItsAddressIsConfirmed() {
        assertEquals("REGISTERED", ExpectedKind.of(AccountKind.EMAIL, emailVerified = true))
        assertNull("before that the server decides (it counts the account as a guest)", ExpectedKind.of(AccountKind.EMAIL, emailVerified = false))
    }

    @Test fun otherKinds_ignoreTheVerifiedFlag() {
        for (v in listOf(true, false)) {
            assertEquals("GUEST", ExpectedKind.of(AccountKind.GUEST, v))
            assertEquals("REGISTERED", ExpectedKind.of(AccountKind.GOOGLE, v))
            assertNull(ExpectedKind.of(AccountKind.OTHER, v))
        }
        assertNull(ExpectedKind.of(null))
    }

    private class Recorder : SessionRefresher {
        val kinds = mutableListOf<String?>()
        override suspend fun refresh(uid: String): RefreshOutcome = RefreshOutcome.Fresh
        override suspend fun refreshIfStale(uid: String, expectedKind: String?): RefreshOutcome { kinds += expectedKind; return RefreshOutcome.Fresh }
    }

    private object NoRetry : RetryScheduler { override fun scheduleRetry() {}; override fun cancelRetry() {} }

    @Test fun theTriggerPassesTheVerifiedStateOn_soConfirmingTheAddressRefreshesTheServerAnswer() = runBlocking {
        val r = Recorder()
        val t = SessionTriggers(r, NoRetry)
        t.onAccount("u", AccountKind.EMAIL, emailVerified = false)
        t.onAccount("u", AccountKind.EMAIL, emailVerified = true)
        assertEquals(listOf<String?>(null, "REGISTERED"), r.kinds)
    }

    @Test fun aGuestWhoVerifiesTheirEmail_isRefreshedEvenThoughTheOldAnswerIsRecent() = runBlocking {
        val now = 1_800_000_000_000L
        val answers = ArrayDeque(listOf(
            SessionApiResult.Ok(ServerSession("usr", "GUEST", "GUEST", 1, 1, now + 1000, now), now),
            SessionApiResult.Ok(ServerSession("usr", "REGISTERED", "FREE", 1, null, null, now), now)
        ))
        val server = object : SessionServer {
            var calls = 0
            override suspend fun open(idToken: String, deviceNowMs: () -> Long): SessionApiResult { calls++; return answers.removeFirst() }
        }
        val tokens = object : IdTokenSource {
            override val currentUid = "u"
            override suspend fun idToken(forceRefresh: Boolean) = "t"
        }
        val store = InMemorySessionStore()
        val triggers = SessionTriggers(SessionRepository(server, store, tokens) { now }, NoRetry)

        triggers.onAccount("u", AccountKind.EMAIL, emailVerified = false)   // linked an email: still a guest
        assertEquals("GUEST", store.load("u")!!.kind)
        triggers.onAccount("u", AccountKind.EMAIL, emailVerified = false)   // nothing changed: no second call
        assertEquals(1, server.calls)
        triggers.onAccount("u", AccountKind.EMAIL, emailVerified = true)    // the link was opened
        assertEquals(2, server.calls)
        assertEquals("REGISTERED", store.load("u")!!.kind)
    }
}
