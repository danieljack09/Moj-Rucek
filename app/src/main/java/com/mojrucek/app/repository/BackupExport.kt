package com.mojrucek.app.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Result of "save backup to device". Cancelling the file picker is not an error. */
sealed interface SaveOutcome {
    data object Cancelled : SaveOutcome
    data class Saved(val bytes: Int) : SaveOutcome
    /** [reason] is already a user-facing Macedonian sentence. */
    data class Failed(val reason: String) : SaveOutcome
}

/**
 * Android-free pieces of saving a backup file through the system file picker (Storage Access
 * Framework), kept separate so they can be unit tested on the JVM.
 */
object BackupFiles {

    private val nameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")

    /** moj-rucek-backup-YYYY-MM-DD-HH-mm.json */
    fun suggestedFileName(now: LocalDateTime): String = "moj-rucek-backup-${now.format(nameFormat)}.json"

    /**
     * Writes [data] to the stream from [openOut], closes it, then (when the destination can be read
     * back) checks that what is stored equals [data]. Never reports success after a failed write.
     */
    fun write(openOut: () -> OutputStream?, openIn: () -> InputStream?, data: ByteArray): SaveOutcome {
        try {
            val out = openOut() ?: return SaveOutcome.Failed("Избраното место не е достапно. Избери друго место.")
            out.use {
                it.write(data)
                it.flush()
            }
        } catch (e: SecurityException) {
            return SaveOutcome.Failed("Нема дозвола за запишување на избраното место. Избери друго место.")
        } catch (e: IOException) {
            return SaveOutcome.Failed("Датотеката не можеше да се запише. Можеби нема доволно простор или местото не е достапно.")
        } catch (e: RuntimeException) {
            return SaveOutcome.Failed("Датотеката не можеше да се запише на избраното место. Избери друго место.")
        }

        // Verification is best effort: some providers cannot be read back, which is not a failure.
        val stored = try {
            openIn()?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        if (stored != null && !stored.contentEquals(data)) {
            return SaveOutcome.Failed("Зачуваната датотека не се совпаѓа со податоците. Обиди се повторно.")
        }
        return SaveOutcome.Saved(data.size)
    }

    /**
     * A friendly place name from a picked document's provider ([authority]) and document id, or null
     * when it cannot be told. Purely informational: nothing depends on it.
     */
    fun destinationHint(authority: String?, documentId: String?): String? = when (authority) {
        "com.android.externalstorage.documents" -> if (documentId == null || ':' !in documentId) null else {
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':').substringBeforeLast('/', "")
            val root = if (volume == "primary") "Интерна меморија" else "SD картичка"
            if (path.isEmpty()) root else "$root › $path"
        }
        "com.android.providers.downloads.documents" -> "Преземања"
        "com.google.android.apps.docs.storage", "com.google.android.apps.docs.storage.legacy" -> "Google Drive"
        else -> null
    }

    fun successMessage(fileName: String?, place: String?): String = buildString {
        append("Резервната копија е успешно зачувана.")
        if (fileName != null) append("\n\nДатотека: $fileName")
        if (place != null) append("\nМесто: $place")
    }
}

/**
 * "Save backup to device": [export] produces the backup text (the existing backup JSON), and
 * [save] writes it to the destination the user picked. [D] is the destination type (a content Uri
 * in the app, anything in tests). A null destination means the picker was cancelled.
 */
class BackupSaver<D : Any>(
    private val export: suspend () -> String,
    private val openOut: (D) -> OutputStream?,
    private val openIn: (D) -> InputStream?,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun save(destination: D?): SaveOutcome {
        destination ?: return SaveOutcome.Cancelled
        val text = try {
            export()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SaveOutcome.Failed("Податоците не можеа да се подготват за зачувување. Обиди се повторно.")
        }
        return withContext(io) {
            BackupFiles.write({ openOut(destination) }, { openIn(destination) }, text.toByteArray(Charsets.UTF_8))
        }
    }
}
