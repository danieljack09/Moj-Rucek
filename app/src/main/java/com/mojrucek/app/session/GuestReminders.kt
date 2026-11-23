package com.mojrucek.app.session

import com.mojrucek.app.domain.session.GuestPeriod
import java.time.ZoneId

/** Plans the reminder notifications. Replaceable in tests. */
interface GuestReminderScheduling {
    /** Plans one reminder for the guest account [uid] whose period ends at [expiresAtMs]. */
    fun schedule(uid: String, expiresAtMs: Long, daysBefore: Int, atEpochMs: Long)
    fun cancelAll()
}

/**
 * Keeps the planned reminders in step with what the phone knows: after every change of the signed-in account or of
 * the remembered answer, the old plan is dropped and the right reminders (3 and 1 days before the end, 10:00 local)
 * are planned again. Not a guest, signed out, or no answer yet: nothing is planned.
 */
class GuestReminderCoordinator(
    private val scheduling: GuestReminderScheduling,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    fun onSession(uid: String?, session: StoredSession?) {
        scheduling.cancelAll()
        val expires = session?.guestExpiresAt
        if (uid == null || session == null || session.kind != "GUEST" || expires == null) return
        val serverNow = session.serverNow(nowMs())
        // Planned in phone time: the reminder moment is a server-time instant, shifted by the measured clock offset.
        for (r in GuestPeriod.reminders(expires, serverNow, zone())) {
            scheduling.schedule(uid, expires, r.daysBefore, r.atEpochMs - session.clockOffsetMs)
        }
    }
}

/** At the moment a reminder fires: is it still right to show it? */
object GuestReminderDecision {
    fun shouldNotify(currentUid: String?, plannedUid: String, session: StoredSession?, plannedExpiresAt: Long, deviceNowMs: Long): Boolean {
        if (currentUid == null || currentUid != plannedUid) return false          // another account (or nobody) is signed in
        if (session == null || session.kind != "GUEST") return false              // linked to Google meanwhile
        if (session.guestExpiresAt != plannedExpiresAt) return false              // the plan is out of date
        return session.guestStatus(deviceNowMs) is GuestPeriod.Status.Running     // already over: the dialog handles that
    }
}
