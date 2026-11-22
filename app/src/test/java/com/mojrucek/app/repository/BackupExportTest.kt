package com.mojrucek.app.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime

private class RecordingOut(
    private val failOnWrite: Boolean = false,
    private val failOnClose: Boolean = false
) : OutputStream() {
    val bytes = ByteArrayOutputStream()
    var closeCount = 0
    override fun write(b: Int) { if (failOnWrite) throw IOException("disk full"); bytes.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) { if (failOnWrite) throw IOException("disk full"); bytes.write(b, off, len) }
    override fun close() { closeCount++; if (failOnClose) throw IOException("close failed") }
}

class BackupFilesTest {

    private val data = "{\"recipes\":[\"Тавче гравче\"]}".toByteArray(Charsets.UTF_8)

    @Test fun suggestedFileName_hasTheRequestedFormat() {
        assertEquals(
            "moj-rucek-backup-2026-11-22-09-05.json",
            BackupFiles.suggestedFileName(LocalDateTime.of(2026, 11, 22, 9, 5))
        )
    }

    @Test fun write_writesExactlyTheGivenBytes_andClosesTheStream() {
        val out = RecordingOut()
        val result = BackupFiles.write({ out }, { ByteArrayInputStream(out.bytes.toByteArray()) }, data)
        assertEquals(SaveOutcome.Saved(data.size), result)
        assertTrue(data.contentEquals(out.bytes.toByteArray()))
        assertEquals(1, out.closeCount)
    }

    @Test fun write_failsWhenTheDestinationCannotBeOpened() {
        assertTrue(BackupFiles.write({ null }, { null }, data) is SaveOutcome.Failed)
    }

    @Test fun write_failsOnIoErrors_andStillClosesTheStream() {
        val out = RecordingOut(failOnWrite = true)
        assertTrue(BackupFiles.write({ out }, { null }, data) is SaveOutcome.Failed)
        assertEquals(1, out.closeCount)
    }

    @Test fun write_failsWhenClosingFails_becauseTheDataMayNotBeStored() {
        val out = RecordingOut(failOnClose = true)
        assertTrue(BackupFiles.write({ out }, { null }, data) is SaveOutcome.Failed)
    }

    @Test fun write_failsOnMissingPermission() {
        val result = BackupFiles.write({ throw SecurityException("no grant") }, { null }, data)
        assertTrue(result is SaveOutcome.Failed)
    }

    @Test fun write_failsOnUnexpectedProviderErrors() {
        val result = BackupFiles.write({ throw IllegalArgumentException("bad uri") }, { null }, data)
        assertTrue(result is SaveOutcome.Failed)
    }

    @Test fun write_failsWhenTheStoredBytesDifferFromWhatWasWritten() {
        val out = RecordingOut()
        val result = BackupFiles.write({ out }, { ByteArrayInputStream("truncated".toByteArray()) }, data)
        assertTrue(result is SaveOutcome.Failed)
    }

    @Test fun write_stillSucceedsWhenTheDestinationCannotBeReadBack() {
        val unreadable = object : InputStream() { override fun read(): Int = throw IOException("not readable") }
        assertEquals(SaveOutcome.Saved(data.size), BackupFiles.write({ RecordingOut() }, { unreadable }, data))
        assertEquals(SaveOutcome.Saved(data.size), BackupFiles.write({ RecordingOut() }, { null }, data))
    }

    @Test fun destinationHint_knownProviders() {
        assertEquals("Интерна меморија › Download", BackupFiles.destinationHint("com.android.externalstorage.documents", "primary:Download/x.json"))
        assertEquals("Интерна меморија", BackupFiles.destinationHint("com.android.externalstorage.documents", "primary:x.json"))
        assertEquals("SD картичка › Backups", BackupFiles.destinationHint("com.android.externalstorage.documents", "1A2B-3C4D:Backups/x.json"))
        assertEquals("Преземања", BackupFiles.destinationHint("com.android.providers.downloads.documents", "msf:42"))
        assertEquals("Google Drive", BackupFiles.destinationHint("com.google.android.apps.docs.storage", "abc"))
    }

    @Test fun destinationHint_unknownOrMalformed_isNull() {
        assertNull(BackupFiles.destinationHint("com.example.provider", "1"))
        assertNull(BackupFiles.destinationHint(null, null))
        assertNull(BackupFiles.destinationHint("com.android.externalstorage.documents", null))
        assertNull(BackupFiles.destinationHint("com.android.externalstorage.documents", "no-colon"))
    }

    @Test fun successMessage_confirmsAndShowsNameAndPlaceWhenKnown() {
        val full = BackupFiles.successMessage("a.json", "Преземања")
        assertTrue(full.startsWith("Резервната копија е успешно зачувана."))
        assertTrue("a.json" in full && "Преземања" in full)
        val bare = BackupFiles.successMessage(null, null)
        assertEquals("Резервната копија е успешно зачувана.", bare)
        assertFalse("Датотека" in bare)
    }
}

class BackupSaverTest {

    private fun saver(
        text: String = "{\"x\":1}",
        out: RecordingOut = RecordingOut(),
        export: suspend () -> String = { text }
    ) = BackupSaver<String>(
        export = export,
        openOut = { out },
        openIn = { ByteArrayInputStream(out.bytes.toByteArray()) },
        io = Dispatchers.Unconfined
    )

    @Test fun cancelledPicker_isNotAnError_andNothingIsExportedOrWritten() = runBlocking {
        var exported = false
        val out = RecordingOut()
        val result = saver(out = out, export = { exported = true; "x" }).save(null)
        assertEquals(SaveOutcome.Cancelled, result)
        assertFalse(exported)
        assertEquals(0, out.bytes.size())
        assertEquals(0, out.closeCount)
    }

    @Test fun savesExactlyTheExportedBackupText_asUtf8() = runBlocking {
        val backup = "{\"name\":\"Тавче гравче\"}"
        val out = RecordingOut()
        val result = saver(text = backup, out = out).save("dest")
        assertTrue(result is SaveOutcome.Saved)
        assertEquals(backup, out.bytes.toString(Charsets.UTF_8))
        assertEquals(1, out.closeCount)
    }

    @Test fun writeFailure_isReported_notSilentlySuccessful() = runBlocking {
        val result = saver(out = RecordingOut(failOnWrite = true)).save("dest")
        assertTrue(result is SaveOutcome.Failed)
    }

    @Test fun unavailableStorage_isReported() = runBlocking {
        val s = BackupSaver<String>(export = { "x" }, openOut = { null }, openIn = { null }, io = Dispatchers.Unconfined)
        assertTrue(s.save("dest") is SaveOutcome.Failed)
    }

    @Test fun exportFailure_isReported_andNothingIsWritten() = runBlocking {
        val out = RecordingOut()
        val result = saver(out = out, export = { throw IllegalStateException("db closed") }).save("dest")
        assertTrue(result is SaveOutcome.Failed)
        assertEquals(0, out.bytes.size())
    }
}
