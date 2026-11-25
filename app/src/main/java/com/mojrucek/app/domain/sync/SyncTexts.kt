package com.mojrucek.app.domain.sync

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The words the user sees about sync (Macedonian), kept apart from the screens so they can be tested. */
object SyncTexts {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    const val TITLE = "Синхронизација"
    const val BUTTON = "Синхронизирај сега"
    const val NEVER = "Сè уште не е синхронизирано."

    /** "Последна синхронизација: пред 5 мин", or the date for older ones. A time in the future (wrong phone clock) is shown as just now. */
    fun lastSynced(syncedAtMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (syncedAtMs == null) return NEVER
        val age = nowMs - syncedAtMs
        val text = when {
            age < MINUTE -> "пред малку"
            age < HOUR -> "пред ${age / MINUTE} мин"
            age < DAY -> "пред ${age / HOUR} ч"
            else -> DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(syncedAtMs).atZone(zone))
        }
        return "Последна синхронизација: $text"
    }

    const val OFFLINE = "Нема интернет врска. Ќе се обидам повторно кога ќе има."
    const val TRY_LATER = "Серверот не е достапен. Ќе се обидам повторно."
    const val TOO_LARGE = "Податоците се преголеми за синхронизација."
    const val IN_SYNC = "Сè е синхронизирано."
    const val SYNCING = "Се синхронизира…"

    const val CONFLICT_TITLE = "Податоците се разликуваат"
    const val CONFLICT_TEXT = "Овој телефон и сервисот имаат различни промени. Избери која верзија да се задржи. " +
        "Пред да се замени нешто на телефонот, се зачувува копија од неговите податоци."
    const val KEEP_PHONE = "Задржи ги од овој телефон"
    const val USE_CLOUD = "Користи ги од сервисот"
    const val LATER = "Подоцна"
    const val CONFLICT_PENDING = "Податоците се разликуваат. Допри за да избереш."
}
