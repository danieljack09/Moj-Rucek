package com.mojrucek.app.data

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Safety net for database upgrades: before the first start on a newer database version, the whole old
 * database file is copied aside, so a failed or surprising migration can always be undone by hand.
 *
 * The copy is made once per old version and is never overwritten, so it always holds the state from
 * right before the upgrade. It lives in the app's private files folder (not in Android's cloud backup).
 */
object DatabaseUpgradeBackup {

    /**
     * @return the backup file if one was made now; null when nothing was needed (no database yet, an
     *         empty one, already on [targetVersion] or newer, or a backup for this version already exists).
     */
    fun copyAsideIfNeeded(dbFile: File, backupDir: File, targetVersion: Int): File? {
        if (!dbFile.isFile) return null
        val oldVersion = readVersion(dbFile) ?: return null
        if (oldVersion <= 0 || oldVersion >= targetVersion) return null

        val backup = File(backupDir, "mojrucek-v$oldVersion-before-upgrade-to-v$targetVersion.db")
        if (backup.exists()) return null
        backupDir.mkdirs()

        // The newest changes may still sit in the write-ahead log: fold them into the main file first.
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE, NEVER_DELETE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        val tmp = File(backupDir, backup.name + ".tmp")
        dbFile.copyTo(tmp, overwrite = true)
        if (tmp.length() != dbFile.length() || !tmp.renameTo(backup)) {
            tmp.delete()
            return null
        }
        return backup
    }

    /** The database's `user_version` (Room's schema version), or null if the file cannot be opened. */
    fun readVersion(dbFile: File): Int? = try {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY, NEVER_DELETE).use { it.version }
    } catch (e: Exception) {
        null
    }

    /**
     * Android's default reaction to a database it thinks is corrupt is to DELETE the file. This code only
     * looks at the database to protect it, so it must never do that: on a damaged file it just gives up.
     */
    private val NEVER_DELETE = DatabaseErrorHandler { }
}
