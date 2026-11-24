package com.mojrucek.app.session

import android.content.Context
import com.mojrucek.app.domain.session.GuestPeriod
import com.mojrucek.app.domain.session.ServerClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * What the phone remembers of the server's last answer for ONE account: enough to show "Guest, 11 days left"
 * and to plan reminders while offline. [clockOffsetMs] is how far the phone's clock was from the server's
 * when the answer arrived; [refreshedAtMs] is the phone's clock then.
 */
@Serializable
data class StoredSession(
    val userId: String,
    val kind: String,
    val plan: String,
    val guestStartedAt: Long?,
    val guestExpiresAt: Long?,
    val clockOffsetMs: Long,
    val refreshedAtMs: Long
) {
    /** Where the guest period stands at the phone's time [deviceNowMs], corrected to server time. */
    fun guestStatus(deviceNowMs: Long): GuestPeriod.Status =
        GuestPeriod.status(guestExpiresAt, ServerClock(clockOffsetMs).now(deviceNowMs))

    /** The server's time at the phone's time [deviceNowMs]. */
    fun serverNow(deviceNowMs: Long): Long = ServerClock(clockOffsetMs).now(deviceNowMs)
}

/** Per-account memory of the server's answer. Two accounts on one phone never share anything. */
interface SessionStore {
    fun load(uid: String): StoredSession?
    fun save(uid: String, session: StoredSession)

    /** The calendar day the "guest period ended" dialog was last shown to this account, if ever. */
    fun expiredDialogShownOn(uid: String): LocalDate?
    fun setExpiredDialogShownOn(uid: String, day: LocalDate)

    /** Forgets everything remembered for [uid] (account deletion). */
    fun forget(uid: String)
}

class InMemorySessionStore : SessionStore {
    private val sessions = HashMap<String, StoredSession>()
    private val shown = HashMap<String, LocalDate>()
    override fun load(uid: String) = sessions[uid]
    override fun save(uid: String, session: StoredSession) { sessions[uid] = session }
    override fun expiredDialogShownOn(uid: String) = shown[uid]
    override fun setExpiredDialogShownOn(uid: String, day: LocalDate) { shown[uid] = day }
    override fun forget(uid: String) { sessions.remove(uid); shown.remove(uid) }
}

/** Kept in the app's private preferences (not in the database): one entry per account id. */
class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(uid: String): StoredSession? {
        val text = prefs.getString("s_$uid", null) ?: return null
        return try {
            json.decodeFromString(StoredSession.serializer(), text)
        } catch (e: SerializationException) {
            null   // unreadable: treated as "nothing remembered", the next refresh replaces it
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun save(uid: String, session: StoredSession) {
        prefs.edit().putString("s_$uid", json.encodeToString(StoredSession.serializer(), session)).apply()
    }

    override fun expiredDialogShownOn(uid: String): LocalDate? =
        try { prefs.getString("d_$uid", null)?.let(LocalDate::parse) } catch (e: DateTimeParseException) { null }

    override fun setExpiredDialogShownOn(uid: String, day: LocalDate) {
        prefs.edit().putString("d_$uid", day.toString()).apply()
    }

    override fun forget(uid: String) {
        prefs.edit().remove("s_$uid").remove("d_$uid").apply()
    }
}
