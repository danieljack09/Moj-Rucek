package com.mojrucek.app.sync

import com.mojrucek.app.session.FakeServer
import com.mojrucek.app.session.FakeServer.Reply
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpSyncServerTest {
    private lateinit var fake: FakeServer
    private val token = "tok-SECRET-123"
    private val snapshot = """{"formatVersion":1,"ingredients":[{"name":"Јајца \"домашни\""}]}"""

    @Before fun start() { fake = FakeServer() }
    @After fun stop() = fake.stop()

    private fun client(baseUrl: String = fake.baseUrl, allowHttp: Boolean = true) = HttpSyncServer(baseUrl, allowHttp, 2000, 3000)

    @Test fun meta_sendsOneGet_andReadsTheAnswer() {
        fake.handler = { Reply(200, """{"revision":4,"updatedAt":99,"size":1234,"serverTime":100}""") }
        val m = (runBlocking { client().meta(token) } as SyncApiResult.Ok).value
        val r = fake.requests.single()
        assertEquals("GET", r.method); assertEquals("/api/sync", r.path)
        assertEquals("Bearer $token", r.headers["authorization"])
        assertEquals(4, m.revision); assertEquals(1234L, m.size)
    }

    @Test fun data_returnsTheExactTextThatWasStored() {
        val stored = snapshot
        val answer = kotlinx.serialization.json.Json.encodeToString(SyncData.serializer(), SyncData(7, 1, stored.length.toLong(), stored, 5))
        fake.handler = { Reply(200, answer) }
        val d = (runBlocking { client().data(token) } as SyncApiResult.Ok).value
        assertEquals("/api/sync/data", fake.requests.single().path)
        assertEquals(7, d.revision)
        assertEquals(stored, d.data)
    }

    @Test fun put_sendsTheBaseRevisionAndTheDataAsJson_andReturnsTheNewRevision() {
        fake.handler = { Reply(200, """{"revision":5,"updatedAt":1,"size":10,"serverTime":2}""") }
        val r = runBlocking { client().put(token, 4, snapshot) }
        assertEquals(5, (r as SyncApiResult.Ok).value)
        val sent = fake.requests.single()
        assertEquals("PUT", sent.method); assertEquals("/api/sync", sent.path)
        assertEquals("Bearer $token", sent.headers["authorization"])
        assertTrue(sent.headers["content-type"]!!.startsWith("application/json"))
        val body = kotlinx.serialization.json.Json.parseToJsonElement(String(sent.body, Charsets.UTF_8)) as kotlinx.serialization.json.JsonObject
        assertEquals("4", body["baseRevision"].toString())
        assertEquals(snapshot, (body["data"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test fun aConflictIsReadWithTheCurrentRevision() {
        fake.handler = { Reply(409, """{"error":"друг","code":"SYNC_CONFLICT","revision":9,"updatedAt":1,"serverTime":2}""") }
        assertEquals(SyncApiResult.Conflict(9), runBlocking { client().put(token, 3, snapshot) })
    }

    @Test fun nothingStored_isRecognised() {
        fake.handler = { Reply(404, """{"error":"нема","code":"NO_SNAPSHOT"}""") }
        assertEquals(SyncApiResult.NothingStored, runBlocking { client().data(token) })
    }

    @Test fun otherStatuses_areMapped() {
        fake.handler = { Reply(401) }; assertEquals(SyncApiResult.Unauthorized, runBlocking { client().meta(token) })
        fake.handler = { Reply(413, """{"code":"TOO_LARGE"}""") }; assertEquals(SyncApiResult.TooLarge, runBlocking { client().put(token, 0, snapshot) })
        fake.handler = { Reply(429) }; assertEquals(SyncApiResult.RateLimited, runBlocking { client().meta(token) })
        fake.handler = { Reply(503) }; assertEquals(SyncApiResult.ServerUnavailable, runBlocking { client().meta(token) })
    }

    @Test fun unreadableRefusals_andOddStatuses_areFailures() {
        fake.handler = { Reply(404, "not json") }; assertTrue(runBlocking { client().data(token) } is SyncApiResult.Failed)
        fake.handler = { Reply(409, """{"code":"OTHER"}""") }; assertTrue(runBlocking { client().put(token, 0, snapshot) } is SyncApiResult.Failed)
        fake.handler = { Reply(418) }; assertTrue(runBlocking { client().meta(token) } is SyncApiResult.Failed)
    }

    @Test fun garbageAnswers_areFailures_neverACrash() {
        for (body in listOf("", "not json", "[]", "{}", """{"revision":"x"}""", "null")) {
            fake.handler = { Reply(200, body) }
            assertTrue(body, runBlocking { client().meta(token) } is SyncApiResult.Failed)
            assertTrue(body, runBlocking { client().data(token) } is SyncApiResult.Failed)
            assertTrue(body, runBlocking { client().put(token, 0, snapshot) } is SyncApiResult.Failed)
        }
    }

    @Test fun aRedirect_isNeverFollowed_onTheGetCall() {
        fake.handler = { Reply(302, headers = mapOf("Location" to "/elsewhere")) }
        assertTrue(runBlocking { client().meta(token) } is SyncApiResult.Failed)
        assertEquals(1, fake.requests.size)
    }

    @Test fun aServerThatIsGone_isOffline() {
        val dead = FakeServer().also { it.stop() }
        assertEquals(SyncApiResult.Offline, runBlocking { client(dead.baseUrl).meta(token) })
    }

    @Test fun plainHttp_isRefused_andNothingIsSent() {
        assertTrue(runBlocking { client(allowHttp = false).put(token, 0, snapshot) } is SyncApiResult.Failed)
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun neitherTheTokenNorTheDataAppearsInAResult() {
        for (code in listOf(200, 401, 404, 409, 413, 500)) {
            fake.handler = { Reply(code, body = "echo $token $snapshot") }
            for (r in listOf(runBlocking { client().meta(token) }, runBlocking { client().put(token, 0, snapshot) }, runBlocking { client().data(token) })) {
                assertFalse(r.toString().contains(token))
                assertFalse(r.toString().contains("домашни"))
            }
        }
    }
}
