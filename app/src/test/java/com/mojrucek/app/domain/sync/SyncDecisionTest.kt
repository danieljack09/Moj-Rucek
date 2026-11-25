package com.mojrucek.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDecisionTest {

    private fun decide(last: Int?, changed: Boolean, empty: Boolean, server: Int) =
        SyncDecision.decide(LocalSyncState(last, changed, empty), server)

    // ---------- nothing on the server ----------

    @Test fun nothingOnTheServer_andDataOnThePhone_uploadsAsTheFirstRevision() {
        assertEquals(SyncAction.Upload(0), decide(last = null, changed = true, empty = false, server = 0))
    }

    @Test fun nothingOnTheServer_andNothingOnThePhone_doesNothing() {
        assertEquals(SyncAction.Nothing, decide(null, changed = false, empty = true, server = 0))
    }

    @Test fun nothingOnTheServer_butThePhoneRememberedARevision_stillUploadsFromZero() {
        // The server lost the snapshot (for example the account was reset): the phone's data is the only copy.
        assertEquals(SyncAction.Upload(0), decide(last = 4, changed = false, empty = false, server = 0))
    }

    // ---------- first time on this phone ----------

    @Test fun newEmptyPhone_andTheServerHasData_downloads() {
        assertEquals(SyncAction.Download, decide(null, changed = false, empty = true, server = 3))
    }

    @Test fun newPhoneWithItsOwnData_andTheServerHasData_asksTheUser() {
        assertEquals(SyncAction.Conflict, decide(null, changed = true, empty = false, server = 3))
    }

    // ---------- synced before ----------

    @Test fun inSync_andUnchanged_doesNothing() {
        assertEquals(SyncAction.Nothing, decide(last = 5, changed = false, empty = false, server = 5))
    }

    @Test fun phoneChanged_serverUnchanged_uploadsFromTheKnownRevision() {
        assertEquals(SyncAction.Upload(5), decide(last = 5, changed = true, empty = false, server = 5))
    }

    @Test fun phoneUnchanged_serverNewer_downloads() {
        assertEquals(SyncAction.Download, decide(last = 5, changed = false, empty = false, server = 6))
        assertEquals(SyncAction.Download, decide(last = 5, changed = false, empty = true, server = 9))
    }

    @Test fun bothChanged_asksTheUser_neverDecidesForThem() {
        assertEquals(SyncAction.Conflict, decide(last = 5, changed = true, empty = false, server = 6))
        assertEquals(SyncAction.Conflict, decide(last = 5, changed = true, empty = true, server = 6))
    }

    @Test fun theServerIsBehindWhatThePhoneSaw_asksTheUser() {
        assertEquals(SyncAction.Conflict, decide(last = 5, changed = false, empty = false, server = 3))
        assertEquals(SyncAction.Conflict, decide(last = 5, changed = true, empty = false, server = 3))
    }

    @Test fun aPhoneThatBecameEmptyAfterSyncing_isAChange_notAReasonToDownloadSilently() {
        // The user emptied everything on purpose: that is a change to upload, not something the cloud copy may overwrite.
        assertEquals(SyncAction.Upload(5), decide(last = 5, changed = true, empty = true, server = 5))
    }

    @Test fun everyCombination_givesOneOfTheFourActions_andNeverDownloadsOverUnsyncedChanges() {
        for (last in listOf(null, 0, 1, 5)) for (changed in listOf(true, false)) for (empty in listOf(true, false)) for (server in 0..7) {
            val a = decide(last, changed, empty, server)
            if (a == SyncAction.Download) {
                val safe = empty || (last != null && !changed)
                assertEquals("download over unsynced changes: last=$last changed=$changed empty=$empty server=$server", true, safe)
            }
        }
    }
}
