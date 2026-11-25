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

    override fun save(snapshotJson: String): Boolean = try {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
        val file = File(dir, "mojrucek-pred-sinhronizacija-$stamp.json")
        val tmp = File(dir, "${file.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(snapshotJson.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        val ok = !file.exists() && tmp.renameTo(file) && file.readText(Charsets.UTF_8) == snapshotJson
        if (!ok) tmp.delete()
        if (ok) prune(dir)
        ok
    } catch (e: Exception) {
        false
    }

    private fun prune(dir: File) {
        val copies = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: return
        copies.dropLast(keep).forEach { it.delete() }
    }

    companion object {
        const val DIR = "sync-backups"
        const val KEEP = 5
    }
}
