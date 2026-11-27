package com.mojrucek.app.sync

import android.content.Context
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.ImportResult
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The snapshot port on top of the backup code (all-or-nothing import, ids remapped, validated). */
class BackupSnapshotPort(private val backup: BackupRepository) : SnapshotPort {
    override suspend fun export(): String = backup.exportForSync()
    override suspend fun import(json: String): Boolean = backup.importForSync(json) is ImportResult.Success
}

/**
 * Keeps a copy of the phone's data in the app's private folder before sync replaces it. Written, synced to disk and read back
 * before it counts. Only the newest [KEEP] copies are kept, so the folder cannot grow without limit.
 */
class FileSafetyCopy(private val context: Context, private val keep: Int = KEEP) : SafetyCopy {

    /** The file name carries a short hash of the account id, so one account's copies can be found (and deleted) without the id itself. */
    private fun tag(uid: String): String = com.mojrucek.app.repository.AccountFileTag.of(uid)

    override fun save(uid: String, snapshotJson: String): Boolean = try {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
        val file = File(dir, "mojrucek-pred-sinhronizacija-${tag(uid)}-$stamp.json")
        val tmp = File(dir, "${file.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(snapshotJson.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        val ok = !file.exists() && tmp.renameTo(file) && file.readText(Charsets.UTF_8) == snapshotJson
        if (!ok) tmp.delete()
        if (ok) prune(dir, tag(uid))
        ok
    } catch (e: Exception) {
        false
    }

    override fun deleteAll(uid: String) {
        val dir = File(context.filesDir, DIR)
        dir.listFiles { f -> f.name.contains("-${tag(uid)}-") }?.forEach { it.delete() }
    }

    private fun prune(dir: File, tag: String) {
        val copies = dir.listFiles { f -> f.name.endsWith(".json") && f.name.contains("-$tag-") }?.sortedBy { it.name } ?: return
        copies.dropLast(keep).forEach { it.delete() }
    }

    companion object {
        const val DIR = "sync-backups"
        const val KEEP = 5
    }
}
