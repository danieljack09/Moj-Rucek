package com.mojrucek.app.session

import com.mojrucek.app.session.FakeServer.Reply
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class HttpAccountServerTest {
    private lateinit var fake: FakeServer
    private val token = "tok-SECRET-123"

    @Before fun start() { fake = FakeServer() }
    @After fun stop() = fake.stop()

    private fun client(baseUrl: String = fake.baseUrl, allowHttp: Boolean = true) = HttpAccountServer(baseUrl, allowHttp, 2000, 3000)
    private fun delete(c: HttpAccountServer = client()) = runBlocking { c.deleteMe(token) }

    @Test fun sendsOneDelete_toTheMeAddress_withTheTokenAndNothingElse() {
        delete()
        val r = fake.requests.single()
        assertEquals("DELETE", r.method)
        assertEquals("/api/me", r.path)
        assertEquals("Bearer $token", r.headers["authorization"])
        assertEquals(0, r.body.size)
        assertFalse(r.headers.containsKey("cookie"))
    }

    @Test fun successCodes_meanDeleted() {
        for (code in listOf(204, 200)) {
            fake.handler = { Reply(code) }
            assertEquals("$code", DeleteApiResult.Deleted, delete())
        }
    }

    @Test fun otherAnswers_areMappedToTheRightOutcome() {
        val cases = mapOf(
            401 to DeleteApiResult.Unauthorized, 429 to DeleteApiResult.RateLimited,
            500 to DeleteApiResult.ServerUnavailable, 503 to DeleteApiResult.ServerUnavailable
        )
        for ((code, expected) in cases) {
            fake.handler = { Reply(code) }
            assertEquals("$code", expected, delete())
        }
        fake.handler = { Reply(404) }
        assertTrue(delete() is DeleteApiResult.Failed)
        fake.handler = { Reply(302, headers = mapOf("Location" to "/elsewhere")) }
        assertTrue("a redirect is never followed", delete() is DeleteApiResult.Failed)
    }

    @Test fun aServerThatIsGone_isOffline() {
        val dead = FakeServer().also { it.stop() }
        assertEquals(DeleteApiResult.Offline, delete(client(dead.baseUrl)))
    }

    @Test fun plainHttp_isRefused_andNothingIsSent() {
        val r = delete(client(allowHttp = false))
        assertTrue(r is DeleteApiResult.Failed)
        assertTrue("the token was not sent", fake.requests.isEmpty())
    }

    @Test fun theTokenNeverAppearsInAResult() {
        for (code in listOf(204, 401, 404, 500)) {
            fake.handler = { Reply(code, body = "echo $token") }
            assertFalse(delete().toString().contains(token))
        }
    }
}

class SessionForgetTest {
    @Test fun forgettingAnAccount_removesOnlyItsAnswerAndDialogDay() {
        val store = InMemorySessionStore()
        val s = StoredSession("usr", "GUEST", "GUEST", null, 5, 0, 0)
        store.save("a", s); store.save("b", s)
        store.setExpiredDialogShownOn("a", LocalDate.of(2026, 1, 1))
        store.setExpiredDialogShownOn("b", LocalDate.of(2026, 1, 1))

        store.forget("a")

        assertNull(store.load("a"))
        assertNull(store.expiredDialogShownOn("a"))
        assertNotNull(store.load("b"))
        assertNotNull(store.expiredDialogShownOn("b"))
    }
}
