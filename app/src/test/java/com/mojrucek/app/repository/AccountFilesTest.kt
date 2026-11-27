package com.mojrucek.app.repository

import com.mojrucek.app.session.AccountMemory
import com.mojrucek.app.session.InMemoryLimitStateStore
import com.mojrucek.app.session.InMemoryPendingRequests
import com.mojrucek.app.session.InMemorySessionStore
import com.mojrucek.app.sync.InMemorySyncStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AccountFilesTest {

    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private val index = InMemorySwitchCopyIndex()

    @Before fun setUp() {
        root = Files.createTempDirectory("account-files-test").toFile()
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
    }

    @After fun tearDown() { root.deleteRecursively() }

    private fun file(dir: String, name: String, parent: File = filesDir): File =
        File(File(parent, dir).apply { mkdirs() }, name).apply { writeText("data of $name") }

    private fun switchCopy(guestUid: String, stamp: String) = file(AccountSwitchRepository.SNAPSHOT_DIR, "mojrucek-pred-promena-${AccountFileTag.of(guestUid)}-$stamp.json")
    private fun exists(dir: String, name: String, parent: File = filesDir) = File(File(parent, dir), name).exists()

    private fun files() = AccountFiles(filesDir, cacheDir, index)

    // ---------- the tag ----------

    @Test fun theTag_isShort_stable_andDifferentForDifferentAccounts() {
        assertEquals(12, AccountFileTag.of("uid-A").length)
        assertEquals(AccountFileTag.of("uid-A"), AccountFileTag.of("uid-A"))
        assertNotEquals(AccountFileTag.of("uid-A"), AccountFileTag.of("uid-B"))
        assertFalse("the account id itself is not in the tag", AccountFileTag.of("uid-A").contains("uid"))
    }

    @Test fun aFileMatchesOnlyItsOwnAccount() {
        val name = "mojrucek-pred-promena-${AccountFileTag.of("guest-1")}-20261125-120000-000.json"
        assertTrue(AccountFileTag.matches(name, "guest-1"))
        assertFalse(AccountFileTag.matches(name, "guest-2"))
        assertFalse(AccountFileTag.matches("mojrucek-pred-promena-20261125-120000-000.json", "guest-1"))
    }

    // ---------- switch copies ----------

    @Test fun deletingAGuestAccount_removesTheCopiesItMade_andNobodyElses() {
        val mine = switchCopy("guest-1", "20261125-120000-000")
        val other = switchCopy("guest-2", "20261125-130000-000")
        files().deleteFor("guest-1")
        assertFalse(mine.exists())
        assertTrue("another account's copy is untouched", other.exists())
    }

    @Test fun deletingTheAccountTheGuestSwitchedInto_removesTheGuestsCopy_too() {
        val copy = switchCopy("guest-1", "20261125-120000-000")
        val unrelated = switchCopy("guest-9", "20261125-140000-000")
        index.attach("google-1", copy.name)               // the switch to google-1 worked
        files().deleteFor("google-1")
        assertFalse(copy.exists())
        assertTrue(unrelated.exists())
        assertTrue("the record is forgotten too", index.filesFor("google-1").isEmpty())
    }

    @Test fun aCopyAttachedToAnotherAccount_isNotDeletedByThisOne() {
        val copy = switchCopy("guest-1", "20261125-120000-000")
        index.attach("google-1", copy.name)
        files().deleteFor("google-2")
        assertTrue(copy.exists())
        assertEquals(setOf(copy.name), index.filesFor("google-1"))
    }

    @Test fun anIndexEntryForAFileThatIsAlreadyGone_isHarmless() {
        index.attach("google-1", "mojrucek-pred-promena-x-y.json")
        files().deleteFor("google-1")
        assertTrue(index.filesFor("google-1").isEmpty())
    }

    // ---------- the other leftovers ----------

    @Test fun theUpgradeCopiesOfTheWholeDatabase_goWithAnAccountDeletion() {
        val a = file(AccountFiles.UPGRADE_COPIES_DIR, "mojrucek-v3-before-upgrade-to-v4.db")
        val b = file(AccountFiles.UPGRADE_COPIES_DIR, "mojrucek-v4-before-upgrade-to-v5.db")
        files().deleteFor("google-1")
        assertFalse(a.exists()); assertFalse(b.exists())
    }

    @Test fun theTemporaryShareFilesInTheCache_goToo() {
        val shared = file(AccountFiles.SHARED_BACKUPS_DIR, "mojrucek-backup-20261125-1200.json", cacheDir)
        files().deleteFor("google-1")
        assertFalse(shared.exists())
    }

    @Test fun filesThatAreNotOurs_areNeverTouched() {
        val sync = file("sync-backups", "mojrucek-pred-sinhronizacija-${AccountFileTag.of("google-1")}-1.json")
        val other = file("something-else", "keep-me.txt")
        val cacheOther = file("other-cache", "keep-me-too.txt", cacheDir)
        files().deleteFor("google-1")
        assertTrue("sync's own copies are removed by their own class", sync.exists())
        assertTrue(other.exists()); assertTrue(cacheOther.exists())
    }

    @Test fun missingFolders_areNotAnError() {
        files().deleteFor("google-1")            // nothing exists yet
    }

    // ---------- all together with the other things an account leaves ----------

    @Test fun accountMemory_alsoClearsTheFiles() {
        val copy = switchCopy("google-1", "20261125-120000-000")
        val db = file(AccountFiles.UPGRADE_COPIES_DIR, "mojrucek-v4-before-upgrade-to-v5.db")
        val sessions = InMemorySessionStore()
        AccountMemory(sessions, InMemoryLimitStateStore(), InMemoryPendingRequests(), InMemorySyncStateStore(), null, files()).forget("google-1")
        assertFalse(copy.exists()); assertFalse(db.exists())
    }
}
