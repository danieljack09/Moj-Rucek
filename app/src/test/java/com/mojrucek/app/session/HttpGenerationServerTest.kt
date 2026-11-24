package com.mojrucek.app.session

import com.mojrucek.app.session.FakeServer.Reply
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpGenerationServerTest {
    private lateinit var fake: FakeServer
    private val token = "tok-SECRET-123"

    @Before fun start() { fake = FakeServer() }
    @After fun stop() = fake.stop()

    private fun client(baseUrl: String = fake.baseUrl, allowHttp: Boolean = true) = HttpGenerationServer(baseUrl, allowHttp, 2000, 3000)
    private fun generate(c: HttpGenerationServer = client()) = runBlocking { c.generate(token, listOf(1L, 2L), "request-AAAA") }
    private fun state(c: HttpGenerationServer = client()) = runBlocking { c.state(token) }

    private val goodGeneration = """{"generationId":5,"createdAt":1000,"unlockedUntil":260201000,"nextAllowedAt":260201000,
        "recipes":[{"id":1,"name":"x","description":"опис","instructionSteps":["а","б"],"notes":null,"tags":["т"],"ingredients":[]}],"serverTime":1000}"""
    private val goodState = """{"plan":"FREE","guestExpired":false,"canGenerateNow":false,"nextAllowedAt":99,"unlockedUntil":99,"recipes":[],"serverTime":5}"""

    @Test fun generate_sendsOnePost_withTheTokenAndAJsonBody() {
        fake.handler = { Reply(200, goodGeneration) }
        generate()

        val r = fake.requests.single()
        assertEquals("POST", r.method)
        assertEquals("/api/generations", r.path)
        assertEquals("Bearer $token", r.headers["authorization"])
        assertTrue(r.headers["content-type"]!!.startsWith("application/json"))
        assertEquals("""{"recipeIds":[1,2],"requestId":"request-AAAA"}""", String(r.body, Charsets.UTF_8))
        assertFalse(r.headers.containsKey("cookie"))
    }

    @Test fun generate_readsAGoodAnswer_includingTheFullDetails() {
        fake.handler = { Reply(200, goodGeneration) }
        val g = (generate() as GenerationApiResult.Ok).value
        assertEquals(5L, g.generationId)
        assertEquals(260201000L, g.unlockedUntil)
        assertEquals(listOf("а", "б"), g.recipes.single().instructionSteps)
        assertEquals("опис", g.recipes.single().description)
    }

    @Test fun state_sendsOneGet_andReadsTheAnswer() {
        fake.handler = { Reply(200, goodState) }
        val s = (state() as GenerationApiResult.Ok).value
        val r = fake.requests.single()
        assertEquals("GET", r.method)
        assertEquals("/api/unlocks", r.path)
        assertEquals("Bearer $token", r.headers["authorization"])
        assertEquals("FREE", s.plan)
        assertEquals(99L, s.nextAllowedAt)
        assertFalse(s.canGenerateNow)
    }

    @Test fun aRuleSayingNo_isReadAsARefusalWithItsDetails() {
        fake.handler = { Reply(409, """{"error":"рано","code":"TOO_SOON","nextAllowedAt":777,"serverTime":5}""") }
        val r = generate() as GenerationApiResult.Refused
        assertEquals("TOO_SOON", r.code)
        assertEquals(777L, r.nextAllowedAt)
        assertEquals(5L, r.serverTime)

        fake.handler = { Reply(400, """{"error":"многу","code":"TOO_MANY_RECIPES","max":3}""") }
        assertEquals(3, (generate() as GenerationApiResult.Refused).max)

        fake.handler = { Reply(403, """{"error":"крај","code":"GUEST_EXPIRED"}""") }
        assertEquals("GUEST_EXPIRED", (generate() as GenerationApiResult.Refused).code)
    }

    @Test fun otherStatuses_areMapped() {
        fake.handler = { Reply(401) }; assertEquals(GenerationApiResult.Unauthorized, generate())
        fake.handler = { Reply(429) }; assertEquals(GenerationApiResult.RateLimited, generate())
        fake.handler = { Reply(503) }; assertEquals(GenerationApiResult.ServerUnavailable, generate())
        fake.handler = { Reply(500) }; assertEquals(GenerationApiResult.ServerUnavailable, generate())
    }

    @Test fun anUnreadableRefusal_orAnUnexpectedStatus_isAPlainFailure() {
        fake.handler = { Reply(404, "not json") }; assertTrue(generate() is GenerationApiResult.Failed)
        fake.handler = { Reply(400, """{"error":"x"}""") }; assertTrue(generate() is GenerationApiResult.Failed)
        val before = fake.requests.size
        fake.handler = { Reply(302, headers = mapOf("Location" to "/elsewhere")) }
        assertTrue("a redirect is never followed", generate() is GenerationApiResult.Failed)
        assertEquals("only the first address was asked", before + 1, fake.requests.size)
        assertTrue(fake.requests.last().path == "/api/generations")
    }

    @Test fun aRedirectOnTheStateCall_isNeverFollowed_soTheTokenCannotBeCarriedElsewhere() {
        fake.handler = { Reply(302, headers = mapOf("Location" to "/elsewhere")) }
        assertTrue(state() is GenerationApiResult.Failed)
        assertEquals("only the first address was asked", 1, fake.requests.size)
        assertEquals("/api/unlocks", fake.requests.single().path)
    }

    @Test fun garbageOrIncompleteAnswers_areFailures_neverACrash() {
        for (body in listOf("", "not json", "[]", "{}", """{"generationId":"x"}""", "null")) {
            fake.handler = { Reply(200, body) }
            assertTrue(body, generate() is GenerationApiResult.Failed)
            assertTrue(body, state() is GenerationApiResult.Failed)
        }
    }

    @Test fun aServerThatIsGone_isOffline() {
        val dead = FakeServer().also { it.stop() }
        assertEquals(GenerationApiResult.Offline, generate(client(dead.baseUrl)))
        assertEquals(GenerationApiResult.Offline, state(client(dead.baseUrl)))
    }

    @Test fun plainHttp_isRefused_andNothingIsSent() {
        assertTrue(generate(client(allowHttp = false)) is GenerationApiResult.Failed)
        assertTrue(state(client(allowHttp = false)) is GenerationApiResult.Failed)
        assertTrue("the token was not sent", fake.requests.isEmpty())
    }

    @Test fun theTokenNeverAppearsInAResult() {
        for (code in listOf(200, 401, 404, 409, 500)) {
            fake.handler = { Reply(code, body = "echo $token") }
            assertFalse(generate().toString().contains(token))
            assertFalse(state().toString().contains(token))
        }
    }
}
