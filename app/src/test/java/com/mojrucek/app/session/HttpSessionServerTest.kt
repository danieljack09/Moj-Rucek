package com.mojrucek.app.session

import com.mojrucek.app.session.FakeServer.Reply
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpSessionServerTest {

    private lateinit var fake: FakeServer
    private val token = "tok-SECRET-123"

    @Before fun start() { fake = FakeServer() }
    @After fun stop() = fake.stop()

    private fun client(baseUrl: String = fake.baseUrl, allowHttp: Boolean = true, readTimeoutMs: Int = 3000) =
        HttpSessionServer(baseUrl, allowHttp = allowHttp, connectTimeoutMs = 2000, readTimeoutMs = readTimeoutMs)

    private fun open(c: HttpSessionServer = client(), deviceNow: () -> Long = { 5000L }) = runBlocking { c.open(token, deviceNow) }

    // ---------- the request ----------

    @Test fun sendsOnePost_toTheSessionAddress_withTheTokenAndNothingElse() {
        open()

        val r = fake.requests.single()
        assertEquals("POST", r.method)
        assertEquals("/api/session", r.path)
        assertEquals("Bearer $token", r.headers["authorization"])
        assertEquals(0, r.body.size)
        assertFalse("no cookies are sent", r.headers.containsKey("cookie"))
    }

    // ---------- a good answer ----------

    @Test fun aGoodAnswer_isReadIntoASession() {
        val result = open() as SessionApiResult.Ok

        assertEquals("usr_abc", result.session.userId)
        assertEquals("GUEST", result.session.kind)
        assertEquals("GUEST", result.session.plan)
        assertEquals(1000L, result.session.guestStartedAt)
        assertEquals(1209601000L, result.session.guestExpiresAt)
        assertEquals(1000L, result.session.serverTime)
    }

    @Test fun thePhoneClockIsTakenAtTheMiddleOfTheCall_soTheClockDifferenceIsFair() {
        val times = listOf(1000L, 1200L).iterator()
        val result = open(deviceNow = { times.next() }) as SessionApiResult.Ok
        assertEquals(1100L, result.deviceNowAtResponseMs)
    }

    @Test fun unknownExtraFields_aNewPlanName_andMissingDates_doNotBreakIt() {
        fake.handler = { Reply(200, """{"userId":"usr_x","kind":"REGISTERED","plan":"PRO","serverTime":77,"somethingNew":{"a":1}}""") }
        val result = open() as SessionApiResult.Ok
        assertEquals("PRO", result.session.plan)
        assertEquals(null, result.session.guestExpiresAt)
    }

    // ---------- the server says no, or is not ready ----------

    @Test fun statusCodes_areTurnedIntoTheRightKindOfOutcome() {
        fun outcome(code: Int): SessionApiResult { fake.handler = { Reply(code, "x") }; return open() }

        assertEquals(SessionApiResult.Unauthorized, outcome(401))
        assertEquals(SessionApiResult.RateLimited, outcome(429))
        for (code in listOf(500, 502, 503, 504, 599)) assertEquals("HTTP $code", SessionApiResult.ServerUnavailable, outcome(code))
        for (code in listOf(400, 403, 404, 418, 201, 204)) {
            val r = outcome(code)
            assertTrue("HTTP $code should be 'failed', was $r", r is SessionApiResult.Failed && r.reason.contains(code.toString()))
        }
    }

    @Test fun anUnreadableOrIncompleteAnswer_isFailed_neverAHalfSession() {
        val bad = listOf(
            "not json", "", "[]", "{}", "null",
            """{"userId":"","kind":"GUEST","plan":"GUEST","serverTime":5}""",
            """{"userId":"   ","kind":"GUEST","plan":"GUEST","serverTime":5}""",
            """{"userId":"usr_a","kind":"GUEST","plan":"GUEST","serverTime":0}""",
            """{"userId":"usr_a","kind":"GUEST","plan":"GUEST"}""",
            """{"userId":"usr_a","plan":"GUEST","serverTime":5}"""
        )
        for (body in bad) {
            fake.handler = { Reply(200, body) }
            assertTrue("should be failed: <$body>", open() is SessionApiResult.Failed)
        }
    }

    @Test fun anOversizedAnswer_isRefused_notReadWithoutLimit() {
        fake.handler = { Reply(200, "x".repeat(70_000)) }
        assertTrue(open() is SessionApiResult.Failed)
    }

    // ---------- no connection ----------

    @Test fun serverNotReachable_isOffline() {
        val url = fake.baseUrl
        fake.stop()
        assertEquals(SessionApiResult.Offline, open(client(url)))
    }

    @Test fun aServerThatAnswersTooSlowly_isOffline() {
        fake.handler = { Reply(200, FakeServer.GOOD, delayMs = 1500) }
        assertEquals(SessionApiResult.Offline, open(client(readTimeoutMs = 300)))
    }

    @Test fun anUnknownHost_isOffline() {
        assertEquals(SessionApiResult.Offline, open(client(baseUrl = "http://no-such-host.invalid")))
    }

    // ---------- keeping the token safe ----------

    @Test fun theTokenIsNeverSentOverPlainHttp_inProduction() {
        val result = open(client(allowHttp = false))

        assertTrue(result is SessionApiResult.Failed)
        assertEquals("nothing at all was sent", 0, fake.requests.size)
    }

    // Guards the behaviour, which has two independent defences (see HttpSessionServer): it only fails if both are removed.
    @Test fun aRedirect_isNeverFollowed_soTheTokenCannotBeCarriedElsewhere() {
        val other = FakeServer()
        try {
            fake.handler = { Reply(302, "", mapOf("Location" to "${other.baseUrl}/api/session")) }

            val result = open()

            assertTrue("a redirect is not an answer: $result", result is SessionApiResult.Failed)
            assertEquals("the other address was never contacted", 0, other.requests.size)
        } finally { other.stop() }
    }

    @Test fun noOutcomeEverContainsTheToken() {
        val results = mutableListOf<SessionApiResult>()
        for (code in listOf(302, 400, 404, 418, 200)) {
            fake.handler = { Reply(code, "garbage $token") }
            results += open()
        }
        results += open(client(allowHttp = false))
        for (r in results) assertFalse("leaked in $r", r.toString().contains(token))
    }
}
