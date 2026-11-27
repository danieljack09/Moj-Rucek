package com.mojrucek.app.repository

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** A short tag made from an account id, so files can be named per account (and found again) without the id itself being in the name. */
object AccountFileTag {
    fun of(uid: String): String =
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it) }

    /** True when [fileName] was made for [uid]: its name holds "-<tag>-". */
    fun matches(fileName: String, uid: String): Boolean = fileName.contains("-${of(uid)}-")
}

/**
 * Remembers which account a guest's copy (made when switching from a guest into an existing account) ended up in. The file is
 * named after the GUEST that made it; this tells us it also belongs, in effect, to the account the person switched into, so
 * deleting THAT account deletes it too.
 */
interface SwitchCopyIndex {
    fun attach(destinationUid: String, fileName: String)
    fun filesFor(destinationUid: String): Set<String>
    fun forget(destinationUid: String)
}

class InMemorySwitchCopyIndex : SwitchCopyIndex {
    private val map = HashMap<String, MutableSet<String>>()
    override fun attach(destinationUid: String, fileName: String) { map.getOrPut(destinationUid) { mutableSetOf() }.add(fileName) }
    override fun filesFor(destinationUid: String): Set<String> = map[destinationUid]?.toSet() ?: emptySet()
    override fun forget(destinationUid: String) { map.remove(destinationUid) }
}

class SharedPreferencesSwitchCopyIndex(context: Context) : SwitchCopyIndex {
    private val prefs = context.applicationContext.getSharedPreferences("switch_copies", Context.MODE_PRIVATE)
    private fun key(uid: String) = "d_${AccountFileTag.of(uid)}"
    override fun attach(destinationUid: String, fileName: String) {
        val all = prefs.getStringSet(key(destinationUid), emptySet()).orEmpty() + fileName
        prefs.edit().putStringSet(key(destinationUid), all).commit()
    }
    override fun filesFor(destinationUid: String): Set<String> = prefs.getStringSet(key(destinationUid), emptySet()).orEmpty().toSet()
    override fun forget(destinationUid: String) { prefs.edit().remove(key(destinationUid)).commit() }
}

/**
 * The files an account leaves in the app's folders besides the database. Deleting an account must remove them, because the
 * privacy page promises that its data on the phone is deleted:
 *  - the copies made when a guest switched into an existing account (named after the guest, and recorded for the account the
 *    person switched into);
 *  - the copies of the whole database kept before an app upgrade: they hold EVERY account's data of that moment and only exist
 *    to undo an upgrade that has long since worked, so they all go;
 *  - the temporary files of "share backup" in the cache.
 * Sync's safety copies are handled by their own class, per account. Files that are not ours are never touched.
 */
class AccountFiles(
    private val filesDir: File,
    private val cacheDir: File,
    private val index: SwitchCopyIndex
) {
    fun deleteFor(uid: String) {
        val switchDir = File(filesDir, AccountSwitchRepository.SNAPSHOT_DIR)
        val attached = index.filesFor(uid)
        switchDir.listFiles()?.filter { AccountFileTag.matches(it.name, uid) || it.name in attached }?.forEach { it.delete() }
        index.forget(uid)

        File(filesDir, UPGRADE_COPIES_DIR).listFiles()?.forEach { it.delete() }
        File(cacheDir, SHARED_BACKUPS_DIR).listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val UPGRADE_COPIES_DIR = "db-backups"
        const val SHARED_BACKUPS_DIR = "backups"
    }
}
