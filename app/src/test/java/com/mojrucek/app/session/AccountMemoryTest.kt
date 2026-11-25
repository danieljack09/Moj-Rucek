package com.mojrucek.app.session

import com.mojrucek.app.sync.InMemorySyncStateStore
import com.mojrucek.app.sync.SafetyCopy
import com.mojrucek.app.sync.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccountMemoryTest {

    private class RecordingSafety : SafetyCopy {
        val deleted = mutableListOf<String>()
        override fun save(uid: String, snapshotJson: String) = true
        override fun deleteAll(uid: String) { deleted += uid }
    }

    @Test fun deletingAnAccount_forgetsEverythingKeptForIt_andNothingForOthers() {
        val sessions = InMemorySessionStore()
        val limits = InMemoryLimitStateStore()
        val requests = InMemoryPendingRequests()
        val sync = InMemorySyncStateStore()
        val safety = RecordingSafety()
        for (uid in listOf("uid-A", "uid-B")) {
            sessions.save(uid, StoredSession("usr", "GUEST", "GUEST", null, 5, 0, 0))
            limits.save(uid, StoredLimits("FREE", false, null, null, 0, 0))
            requests.set(uid, "1,2", "req-$uid")
            sync.save(uid, SyncRecord(3, "fp", 1))
        }

        AccountMemory(sessions, limits, requests, sync, safety).forget("uid-A")

        assertNull(sessions.load("uid-A")); assertNull(limits.load("uid-A")); assertNull(requests.get("uid-A", "1,2")); assertNull(sync.load("uid-A"))
        assertEquals(listOf("uid-A"), safety.deleted)
        assertNotNull(sessions.load("uid-B")); assertNotNull(limits.load("uid-B")); assertNotNull(requests.get("uid-B", "1,2")); assertNotNull(sync.load("uid-B"))
    }

    @Test fun missingParts_areSkipped_notACrash() {
        val sessions = InMemorySessionStore().also { it.save("uid-A", StoredSession("usr", "GUEST", "GUEST", null, 5, 0, 0)) }
        AccountMemory(sessions, null, null, null, null).forget("uid-A")
        assertNull(sessions.load("uid-A"))
    }
}
