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

/**
 * A local backup that was written, read back and validated. The file is never deleted by the app.
 * [ownerKey] is the account whose data it holds (the guest): the later clear-out touches ONLY that account's rows.
 */
data class VerifiedSnapshot(val file: File, val summary: BackupSummary, val ownerKey: String)

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
    private val backupRepository: BackupRepository,
    private val owner: CurrentOwner,
    /** Records which account a copy ended up with, so deleting that account deletes the copy too. */
    private val index: SwitchCopyIndex = InMemorySwitchCopyIndex()
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createVerifiedSnapshot(): SnapshotResult = withContext(Dispatchers.IO) {
        try {
            // The snapshot is of the account that is signed in right now (the guest).
            val ownerKey = owner.key
            // One read transaction: the export and its counts describe the same database state.
            val (exported, live) = database.withTransaction {
                backupRepository.exportToJson(ownerKey) to backupRepository.currentSummary(ownerKey)
            }
            val dir = File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
            // Named after the guest that made it (a short tag), so deleting that guest account can find it.
            val file = File(dir, "mojrucek-pred-promena-${AccountFileTag.of(ownerKey)}-$stamp.json")
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
            SnapshotResult.Ok(VerifiedSnapshot(file, summary, ownerKey))
        } catch (e: Exception) {
            SnapshotResult.Failed("Копијата не можеше да се направи (${e.javaClass.simpleName}).")
        }
    }

    /** The switch worked: the copy now also belongs to [destinationUid], the account the person signed into. */
    fun recordDestination(snapshot: VerifiedSnapshot, destinationUid: String?) {
        if (destinationUid != null) index.attach(destinationUid, snapshot.file.name)
    }

    /** True when the snapshot file is still intact and the database still has exactly its content. */
    suspend fun matchesSnapshot(snapshot: VerifiedSnapshot): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = database.withTransaction { backupRepository.exportToJson(snapshot.ownerKey) }
            sameData(snapshot.file.readText(Charsets.UTF_8), current)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Removes the snapshot account's own data from this phone in ONE transaction, and only if that
     * account's data still equals [snapshot] (re-checked inside that transaction). Returns false,
     * deleting nothing, when it does not. Only rows of [VerifiedSnapshot.ownerKey] are deleted, never
     * another account's (the account signed in NOW is usually a different one). Settings, the server
     * catalog and server recipes stay.
     */
    suspend fun clearLocalUserData(snapshot: VerifiedSnapshot): Boolean = withContext(Dispatchers.IO) {
        val saved = snapshot.file.readText(Charsets.UTF_8)
        database.withTransaction {
            val ownerKey = snapshot.ownerKey
            if (!sameData(saved, backupRepository.exportToJson(ownerKey))) return@withTransaction false
            database.plannerDao().deleteAllPlans(ownerKey)  // planned meals cascade
            database.shoppingListDao().deleteAll(ownerKey)
            database.ingredientDao().deleteAll(ownerKey)    // the pantry; catalog_ingredients is untouched
            database.recipeDao().deleteLocalOnly(ownerKey)  // the account's own recipes; ingredient lines cascade
            database.favoriteDao().removeAll(ownerKey)      // the guest's favourites; other accounts' favourites stay
            database.unlockedDetailsDao().deleteAll(ownerKey) // the guest's unlocked recipe details
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
