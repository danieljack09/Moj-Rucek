package com.mojrucek.app.repository

import android.content.Context
import androidx.room.withTransaction
import com.mojrucek.app.data.AppDatabase
import com.mojrucek.app.data.BackupSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** A local backup that was written, read back and validated. The file is never deleted by the app. */
data class VerifiedSnapshot(val file: File, val summary: BackupSummary)

sealed interface SnapshotResult {
    data class Ok(val snapshot: VerifiedSnapshot) : SnapshotResult
    data class Failed(val reason: String) : SnapshotResult
}

/**
 * Data side of switching from a guest to an existing account (credential-already-in-use):
 * 1. [createVerifiedSnapshot]: full export (consistent read transaction) to internal storage,
 *    synced, read back byte-for-byte and validated;
 * 2. (caller) explicit confirmation, [matchesSnapshot], then sign-in to the existing account;
 * 3. [clearLocalUserData]: deletes only if the database STILL equals the snapshot file, checked
 *    inside the same transaction as the delete. Otherwise nothing is deleted.
 * Accounts are never merged; the server catalog and settings are kept.
 */
class AccountSwitchRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val backupRepository: BackupRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createVerifiedSnapshot(): SnapshotResult = withContext(Dispatchers.IO) {
        try {
            // One read transaction: the export and its counts describe the same database state.
            val (exported, live) = database.withTransaction {
                backupRepository.exportToJson() to backupRepository.currentSummary()
            }
            val dir = File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
            val file = File(dir, "mojrucek-pred-promena-$stamp.json")
            if (file.exists()) return@withContext SnapshotResult.Failed("Копијата не можеше да се зачува. Обиди се повторно.")
            val tmp = File(dir, "${file.name}.tmp")
            FileOutputStream(tmp).use { out ->
                out.write(exported.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return@withContext SnapshotResult.Failed("Копијата не можеше да се зачува.")
            }
            val readBack = file.readText(Charsets.UTF_8)
            if (readBack != exported) {
                return@withContext SnapshotResult.Failed("Зачуваната копија не се совпаѓа со податоците.")
            }
            val summary = when (val v = backupRepository.validate(readBack)) {
                is ImportResult.Success -> v.summary
                is ImportResult.InvalidFile ->
                    return@withContext SnapshotResult.Failed("Зачуваната копија не е валидна: ${v.reason}")
            }
            if (summary != live) {
                return@withContext SnapshotResult.Failed("Зачуваната копија не ги содржи сите податоци.")
            }
            SnapshotResult.Ok(VerifiedSnapshot(file, summary))
        } catch (e: Exception) {
            SnapshotResult.Failed("Копијата не можеше да се направи (${e.javaClass.simpleName}).")
        }
    }

    /** True when the snapshot file is still intact and the database still has exactly its content. */
    suspend fun matchesSnapshot(snapshot: VerifiedSnapshot): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = database.withTransaction { backupRepository.exportToJson() }
            sameData(snapshot.file.readText(Charsets.UTF_8), current)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Removes the user's own data from this phone in ONE transaction, and only if the database
     * still equals [snapshot] (re-checked inside that transaction). Returns false, deleting
     * nothing, when it does not. Settings, the server catalog and server recipes stay.
     */
    suspend fun clearLocalUserData(snapshot: VerifiedSnapshot): Boolean = withContext(Dispatchers.IO) {
        val saved = snapshot.file.readText(Charsets.UTF_8)
        database.withTransaction {
            if (!sameData(saved, backupRepository.exportToJson())) return@withTransaction false
            database.plannerDao().deleteAllPlans()          // planned meals cascade
            database.shoppingListDao().deleteAll()
            database.ingredientDao().deleteAll()            // the pantry; catalog_ingredients is untouched
            database.recipeDao().deleteLocalOnly()          // serverId IS NULL; recipe ingredients cascade
            database.recipeDao().clearFavorites()           // favourite marks are user data too
            true
        }
    }

    /** Compares two exports, ignoring only the export timestamp. */
    private fun sameData(a: String, b: String): Boolean = withoutTimestamp(a) == withoutTimestamp(b)

    private fun withoutTimestamp(text: String): JsonElement {
        val obj = json.parseToJsonElement(text) as JsonObject
        return JsonObject(obj - "exportedAtEpochMillis")
    }

    companion object {
        /** Under filesDir; shared through the FileProvider path "account_switch". */
        const val SNAPSHOT_DIR = "account-switch"
    }
}
