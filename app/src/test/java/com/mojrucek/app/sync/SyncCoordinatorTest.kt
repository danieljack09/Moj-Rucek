package com.mojrucek.app.sync

import com.mojrucek.app.session.IdTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncCoordinatorTest {

    // ---------- fakes: a cloud that behaves like the real server, and phones that hold a list of pantry items ----------

    private class Cloud : SyncServer {
        var revision = 0
        var data: String? = null
        var puts = 0
        var failWith: SyncApiResult<*>? = null
        var beforePut: (() -> Unit)? = null
        @Suppress("UNCHECKED_CAST")
        private fun <T> failure(): SyncApiResult<T>? = failWith as SyncApiResult<T>?
        override suspend fun meta(idToken: String): SyncApiResult<SyncMeta> = failure() ?: SyncApiResult.Ok(SyncMeta(revision))
        override suspend fun data(idToken: String): SyncApiResult<SyncData> =
            failure() ?: data?.let { SyncApiResult.Ok(SyncData(revision, data = it)) } ?: SyncApiResult.NothingStored
        override suspend fun put(idToken: String, baseRevision: Int, data: String): SyncApiResult<Int> {
            beforePut?.invoke()
            failure<Int>()?.let { return it }
            puts++
            if (baseRevision != revision) return SyncApiResult.Conflict(revision)
            this.data = data; revision++
            return SyncApiResult.Ok(revision)
        }
    }

    private class Phone : SnapshotPort {
        var items = mutableListOf<String>()
        var importFails = false
        var imports = 0
        var exportedAt = 1L
        override suspend fun export(): String {
            val list = items.joinToString(",") { """{"id":${it.hashCode()},"name":"$it"}""" }
            return """{"formatVersion":1,"exportedAtEpochMillis":${exportedAt++},"ingredients":[$list],"recipes":[],"recipeIngredients":[],"shoppingListItems":[],"weeklyPlans":[],"plannedMeals":[]}"""
        }
        override suspend fun import(json: String): Boolean {
            if (importFails) return false
            imports++
            items = Regex(""""name":"([^"]*)"""").findAll(json).map { it.groupValues[1] }.toMutableList()
            return true
        }
    }

    private class Safety : SafetyCopy {
        val saved = mutableListOf<String>()
        var fails = false
        override fun save(uid: String, snapshotJson: String): Boolean { if (fails) return false; saved += snapshotJson; return true }
        override fun deleteAll(uid: String) { saved.clear() }
    }

    private class Tokens(override var currentUid: String? = "uid-A") : IdTokenSource {
        var throwing = false
        override suspend fun idToken(forceRefresh: Boolean): String? { if (throwing) throw IllegalStateException("no network"); return "tok" }
    }

    private class Rig(val cloud: Cloud, val tokens: Tokens = Tokens(), val phone: Phone = Phone(), val safety: Safety = Safety(), val store: SyncStateStore = InMemorySyncStateStore(), var now: Long = 1000) {
        val sync = SyncCoordinator(cloud, tokens, phone, safety, store) { now }
    }

    private fun rig(cloud: Cloud = Cloud(), vararg items: String) = Rig(cloud).also { it.phone.items = items.toMutableList() }
    private fun sync(r: Rig) = runBlocking { r.sync.sync() }

    // ---------- first uploads ----------

    @Test fun emptyPhone_emptyCloud_nothingHappens() {
        val r = rig()
        assertEquals(SyncOutcome.AlreadyInSync, sync(r))
        assertEquals(0, r.cloud.puts)
        assertNull(r.store.load("uid-A"))
    }

    @Test fun aPhoneWithData_uploadsTheFirstRevision_andIsThenInSync() {
        val r = rig(Cloud(), "Јајца", "Ориз")
        assertEquals(SyncOutcome.Uploaded, sync(r))
        assertEquals(1, r.cloud.revision)
        assertTrue(r.cloud.data!!.contains("Јајца"))
        assertEquals(SyncRecord(1, com.mojrucek.app.domain.sync.SnapshotFingerprint.of(r.cloud.data!!), 1000), r.store.load("uid-A"))
        assertEquals(SyncOutcome.AlreadyInSync, sync(r))
        assertEquals("no second upload for unchanged data", 1, r.cloud.puts)
    }

    @Test fun aChangeOnThePhone_isUploadedAsTheNextRevision() {
        val r = rig(Cloud(), "Јајца")
        sync(r)
        r.phone.items.add("Ориз")
        assertEquals(SyncOutcome.Uploaded, sync(r))
        assertEquals(2, r.cloud.revision)
        assertEquals(SyncOutcome.AlreadyInSync, sync(r))
    }

    // ---------- a second phone ----------

    @Test fun aNewEmptyPhone_downloadsTheAccountsData_withoutAnyQuestion() {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца", "Ориз"); sync(a)
        val b = rig(cloud)
        assertEquals(SyncOutcome.Downloaded, sync(b))
        assertEquals(listOf("Јајца", "Ориз"), b.phone.items)
        assertTrue("nothing to lose, so no safety copy was needed", b.safety.saved.isEmpty())
        assertEquals("what was just downloaded is not uploaded back", SyncOutcome.AlreadyInSync, sync(b))
        assertEquals(1, cloud.puts)
    }

    @Test fun anUnchangedPhone_takesTheNewerCloudData_afterKeepingASafetyCopy() {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца"); sync(a)
        val b = rig(cloud); sync(b)                              // B downloaded revision 1
        a.phone.items.add("Ориз"); sync(a)                       // A uploads revision 2
        assertEquals(SyncOutcome.Downloaded, sync(b))
        assertEquals(listOf("Јајца", "Ориз"), b.phone.items)
        assertEquals(1, b.safety.saved.size)
        assertTrue("the copy holds what the phone had before", b.safety.saved.single().contains("Јајца") && !b.safety.saved.single().contains("Ориз"))
    }

    // ---------- conflicts ----------

    private fun conflict(): Triple<Rig, Rig, Cloud> {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца"); sync(a)
        val b = rig(cloud); sync(b)
        a.phone.items.add("од A"); sync(a)                       // cloud revision 2
        b.phone.items.add("од B")                                // B changed too, without syncing
        return Triple(a, b, cloud)
    }

    @Test fun bothChanged_theUserMustChoose_andNothingIsTouched() {
        val (_, b, cloud) = conflict()
        val before = b.phone.items.toList()
        assertEquals(SyncOutcome.NeedsChoice(2), sync(b))
        assertEquals(before, b.phone.items)
        assertEquals(2, cloud.revision)
        assertEquals(0, b.phone.imports - 1)    // only B's very first download imported anything
        assertTrue(b.safety.saved.isEmpty())
    }

    @Test fun keepThisPhone_uploadsOverTheCloud() {
        val (a, b, cloud) = conflict()
        assertEquals(SyncOutcome.Uploaded, runBlocking { b.sync.resolveKeepThisPhone() })
        assertEquals(3, cloud.revision)
        assertTrue(cloud.data!!.contains("од B") && !cloud.data!!.contains("од A"))
        assertEquals(SyncOutcome.AlreadyInSync, sync(b))
        // A later gets B's version (A is unchanged since its own upload)
        assertEquals(SyncOutcome.Downloaded, sync(a))
        assertTrue(a.phone.items.contains("од B"))
    }

    @Test fun useTheCloud_keepsASafetyCopyOfThePhoneFirst_thenReplaces() {
        val (_, b, _) = conflict()
        assertEquals(SyncOutcome.Downloaded, runBlocking { b.sync.resolveUseCloud() })
        assertTrue(b.phone.items.contains("од A") && !b.phone.items.contains("од B"))
        assertEquals(1, b.safety.saved.size)
        assertTrue("the safety copy has the phone's own change", b.safety.saved.single().contains("од B"))
        assertEquals(SyncOutcome.AlreadyInSync, sync(b))
    }

    @Test fun aRaceDuringUpload_isAConflictForTheUser_notAnOverwrite() {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца"); sync(a)
        a.phone.items.add("ново")
        cloud.beforePut = { cloud.revision++; cloud.data = """{"formatVersion":1,"ingredients":[{"name":"друг"}]}""" }   // another phone got in first
        assertEquals(SyncOutcome.NeedsChoice(2), sync(a))
        assertTrue("the other phone's data is untouched", cloud.data!!.contains("друг"))
        assertEquals("the phone did not record a sync that did not happen", 1, a.store.load("uid-A")!!.revision)
    }

    // ---------- failures never lose data ----------

    @Test fun ifTheSafetyCopyCannotBeKept_nothingIsReplaced() {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца"); sync(a)
        val b = rig(cloud); sync(b)
        a.phone.items.add("Ориз"); sync(a)
        b.safety.fails = true
        val recordBefore = b.store.load("uid-A")
        val r = sync(b)
        assertTrue(r is SyncOutcome.Problem)
        assertEquals(listOf("Јајца"), b.phone.items)
        assertEquals(recordBefore, b.store.load("uid-A"))
    }

    @Test fun ifTheImportFails_theDataAndTheRecordStayAsTheyWere() {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца"); sync(a)
        val b = rig(cloud); sync(b)
        a.phone.items.add("Ориз"); sync(a)
        b.phone.importFails = true
        val recordBefore = b.store.load("uid-A")
        assertTrue(sync(b) is SyncOutcome.Problem)
        assertEquals(listOf("Јајца"), b.phone.items)
        assertEquals(recordBefore, b.store.load("uid-A"))
    }

    @Test fun troubleOutcomes_aremappedAndChangeNothing() {
        val cases = listOf<Pair<SyncApiResult<*>, SyncOutcome>>(
            SyncApiResult.Offline to SyncOutcome.Offline,
            SyncApiResult.RateLimited to SyncOutcome.TryLater,
            SyncApiResult.ServerUnavailable to SyncOutcome.TryLater,
            SyncApiResult.TooLarge to SyncOutcome.TooLarge
        )
        for ((answer, expected) in cases) {
            val r = rig(Cloud(), "Јајца").also { it.cloud.failWith = answer }
            assertEquals("$answer", expected, sync(r))
            assertNull(r.store.load("uid-A"))
            assertEquals(listOf("Јајца"), r.phone.items)
        }
        for (answer in listOf(SyncApiResult.Unauthorized, SyncApiResult.Failed("x"))) {
            val r = rig(Cloud(), "Јајца").also { it.cloud.failWith = answer }
            assertTrue("$answer", sync(r) is SyncOutcome.Problem)
        }
    }

    @Test fun noTokenBecauseNoInternet_isOffline() {
        val r = rig(Cloud(), "Јајца").also { it.tokens.throwing = true }
        assertEquals(SyncOutcome.Offline, sync(r))
        assertEquals(0, r.cloud.puts)
    }

    @Test fun nobodySignedIn_doesNothing() {
        val r = rig(Cloud(), "Јајца").also { it.tokens.currentUid = null }
        assertEquals(SyncOutcome.NotSignedIn, sync(r))
        assertEquals(0, r.cloud.puts)
    }

    @Test fun cancellingTheWork_isNeverSwallowed() {
        val cloud = Cloud()
        val server = object : SyncServer by cloud {
            override suspend fun meta(idToken: String): SyncApiResult<SyncMeta> = throw CancellationException("screen closed")
        }
        val r = Rig(cloud)
        val sync = SyncCoordinator(server, r.tokens, r.phone, r.safety, r.store)
        try { runBlocking { sync.sync() }; fail("must propagate") } catch (e: CancellationException) { }
    }

    // ---------- accounts and timing ----------

    @Test fun ifAnotherAccountSignsInDuringADownload_nothingIsImported() {
        val cloud = Cloud()
        val a = rig(cloud, "Јајца"); sync(a)
        val b = rig(cloud)
        val server = object : SyncServer by cloud {
            override suspend fun data(idToken: String): SyncApiResult<SyncData> { b.tokens.currentUid = "uid-B"; return cloud.data(idToken) }
        }
        val coordinator = SyncCoordinator(server, b.tokens, b.phone, b.safety, b.store)
        val r = runBlocking { coordinator.sync() }
        assertTrue(r is SyncOutcome.Problem)
        assertTrue(b.phone.items.isEmpty())
        assertNull(b.store.load("uid-A")); assertNull(b.store.load("uid-B"))
    }

    @Test fun twoAccountsKeepSeparateRecords() {
        val cloud = Cloud()
        val r = rig(cloud, "Јајца"); sync(r)
        r.tokens.currentUid = "uid-B"
        assertNull(r.store.load("uid-B"))
        assertNotNull(r.store.load("uid-A"))
    }

    @Test fun aChangeMadeDuringTheUpload_isNoticedAtTheNextRun() {
        val cloud = Cloud()
        val r = rig(cloud, "Јајца")
        cloud.beforePut = { r.phone.items.add("добавено за време на качувањето") }
        assertEquals(SyncOutcome.Uploaded, sync(r))
        cloud.beforePut = null
        assertEquals("the change made meanwhile was not in the upload", SyncOutcome.Uploaded, sync(r))
        assertTrue(cloud.data!!.contains("добавено"))
    }

    @Test fun aPhoneEmptiedOnPurposeAfterSyncing_uploadsTheEmptyState_insteadOfRefillingFromTheCloud() {
        val r = rig(Cloud(), "Јајца"); sync(r)
        r.phone.items.clear()
        assertEquals(SyncOutcome.Uploaded, sync(r))
        assertEquals(0, r.phone.imports)
        assertFalse(r.cloud.data!!.contains("Јајца"))
    }

    @Test fun theTimeOfTheLastSync_isRemembered() {
        val r = rig(Cloud(), "Јајца"); r.now = 5555
        sync(r)
        assertEquals(5555L, r.sync.lastSyncedAt("uid-A"))
        assertNull(r.sync.lastSyncedAt("uid-X"))
    }

    // ---------- the facts read from a snapshot ----------

    @Test fun anEmptySnapshot_isRecognised_andUnreadableTextIsNeverTreatedAsEmpty() {
        assertTrue(SnapshotInfo.isEmpty("""{"formatVersion":1,"ingredients":[],"recipes":[],"recipeIngredients":[],"shoppingListItems":[],"weeklyPlans":[],"plannedMeals":[]}"""))
        assertTrue(SnapshotInfo.isEmpty("""{"formatVersion":1}"""))
        assertFalse(SnapshotInfo.isEmpty("""{"formatVersion":1,"ingredients":[{"name":"x"}]}"""))
        assertFalse(SnapshotInfo.isEmpty("""{"formatVersion":1,"plannedMeals":[{}]}"""))
        for (bad in listOf("", "not json", "[]", "null")) assertFalse(bad, SnapshotInfo.isEmpty(bad))
    }
}
