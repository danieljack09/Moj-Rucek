package com.mojrucek.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The copy-aside before an upgrade: throw-away files only, never the app's real database. */
@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeBackupInstrumentedTest {

    private lateinit var dir: File
    private lateinit var dbFile: File
    private lateinit var backupDir: File

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        dir = File(cache, "upgrade-backup-test-${System.nanoTime()}").apply { mkdirs() }
        dbFile = File(dir, "test.db")
        backupDir = File(dir, "backups")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Opens the test database in WAL mode (like Room) with `user_version` = [version] and the given rows. */
    private fun open(version: Int?, vararg rows: String): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).also { db ->
            db.enableWriteAheadLogging()
            db.execSQL("CREATE TABLE IF NOT EXISTS t (v TEXT)")
            rows.forEach { db.execSQL("INSERT INTO t VALUES ('$it')") }
            if (version != null) db.version = version
        }

    private fun rowsOf(file: File): List<String> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT v FROM t ORDER BY rowid", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        }

    @Test
    fun anOldDatabase_isCopiedAside_withAllItsRows_includingTheNewestOnes() {
        val live = open(3, "а", "б", "в")   // still open, newest rows may only be in the write-ahead log
        val backup = DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, targetVersion = 4)
        live.close()

        assertNotNull(backup)
        assertEquals("mojrucek-v3-before-upgrade-to-v4.db", backup!!.name)
        assertEquals(listOf("а", "б", "в"), rowsOf(backup))
        assertEquals("the copy remembers the old version", 3, DatabaseUpgradeBackup.readVersion(backup))
        assertEquals("the original is untouched", listOf("а", "б", "в"), rowsOf(dbFile))
        assertEquals(3, DatabaseUpgradeBackup.readVersion(dbFile))
        assertTrue("no temp file is left behind", backupDir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun theCopy_isNeverOverwritten_soItKeepsTheStateFromRightBeforeTheUpgrade() {
        open(3, "први").close()
        val first = DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4)
        open(3, "втор").close()   // data changes afterwards

        val second = DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4)

        assertNotNull(first)
        assertNull("no second copy is made", second)
        assertEquals(listOf("први"), rowsOf(first!!))
    }

    @Test
    fun nothingIsCopied_whenThereIsNothingToProtect() {
        assertNull("no database file yet", DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4))
        open(0).close()
        assertNull("a brand-new empty database (version 0)", DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4))
        open(4, "x").close()
        assertNull("already on the target version", DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4))
        open(5, "y").close()
        assertNull("newer than the target version", DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4))
        assertTrue(!backupDir.exists() || backupDir.listFiles()!!.isEmpty())
    }

    @Test
    fun aFileThatIsNotADatabase_isLeftAlone() {
        dbFile.writeText("this is not a database")
        assertNull(DatabaseUpgradeBackup.copyAsideIfNeeded(dbFile, backupDir, 4))
        assertEquals("this is not a database", dbFile.readText())
    }
}
