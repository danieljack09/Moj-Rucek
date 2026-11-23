package com.mojrucek.app.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SessionChangesTest {
    private val now = 1_800_000_000_000L
    private class Srv(val r: SessionApiResult) : SessionServer { override suspend fun open(idToken: String, deviceNowMs: () -> Long) = r }
    private class Tok : IdTokenSource { override val currentUid = "u"; override suspend fun idToken(forceRefresh: Boolean) = "t" }
    private val ok = SessionApiResult.Ok(ServerSession("usr", "GUEST", "GUEST", 1, 1, now + 1000, now), now)

    @Test fun changesTicksOnlyWhenAnAnswerIsRemembered() = runBlocking {
        val good = SessionRepository(Srv(ok), InMemorySessionStore(), Tok(), { now })
        val bad = SessionRepository(Srv(SessionApiResult.Offline), InMemorySessionStore(), Tok(), { now })
        val g0 = good.changes.value; val b0 = bad.changes.value

        good.refresh("u"); bad.refresh("u")

        assertEquals(g0 + 1, good.changes.value)
        assertEquals(b0, bad.changes.value)
    }

    @Test fun theDialogDayIsRememberedPerAccount() {
        val repo = SessionRepository(Srv(ok), InMemorySessionStore(), Tok(), { now })
        val day = LocalDate.of(2026, 12, 11)
        assertNull(repo.expiredDialogShownOn("a"))
        repo.markExpiredDialogShown("a", day)
        assertEquals(day, repo.expiredDialogShownOn("a"))
        assertNull(repo.expiredDialogShownOn("b"))
    }
}
