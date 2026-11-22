package com.mojrucek.app.ui.components

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.mojrucek.app.repository.BackupFiles
import com.mojrucek.app.repository.BackupSaver
import java.time.LocalDateTime

/** Name the system file picker should suggest for a new backup file. */
fun suggestedBackupFileName(): String = BackupFiles.suggestedFileName(LocalDateTime.now())

/** A [BackupSaver] that writes to a Uri chosen with the system file picker (no storage permission needed). */
fun backupSaverFor(context: Context, export: suspend () -> String): BackupSaver<Uri> {
    val resolver = context.applicationContext.contentResolver
    return BackupSaver(
        export = export,
        // "wt" truncates: saving over an existing, longer file must not leave old bytes behind.
        openOut = { uri -> resolver.openOutputStream(uri, "wt") },
        openIn = { uri -> resolver.openInputStream(uri) }
    )
}

/** "Saved" text with the file name and, when it can be told, where the file went. Best effort. */
fun savedBackupMessage(context: Context, uri: Uri): String {
    val name = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
    val place = try {
        BackupFiles.destinationHint(uri.authority, DocumentsContract.getDocumentId(uri))
    } catch (e: Exception) {
        null
    }
    return BackupFiles.successMessage(name, place)
}
