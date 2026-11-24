package com.mojrucek.app.domain.limits

import com.mojrucek.app.session.PermissionResult

/** A message for the user: [linkGoogle] adds the "link Google" button (for a guest whose period has ended). */
data class LimitNotice(val title: String, val text: String, val linkGoogle: Boolean = false)

/** The words the user sees about the list rules (Macedonian), kept apart from the screens so they can be tested. */
object LimitTexts {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** "2д 5ч", "3ч 20 мин", "12 мин". Anything not in the future is "сега". Rounded UP, so it never promises too early. */
    fun countdown(nextAllowedAt: Long, serverNowMs: Long): String {
        val remaining = nextAllowedAt - serverNowMs
        if (remaining <= 0) return "сега"
        val totalMinutes = (remaining + MINUTE - 1) / MINUTE
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> if (hours > 0) "${days}д ${hours}ч" else "${days}д"
            hours > 0 -> if (minutes > 0) "${hours}ч $minutes мин" else "${hours}ч"
            else -> "$minutes мин"
        }
    }

    /** The line under the generate button: when the next list is possible, or null when it is possible now / unknown. */
    fun nextListLine(nextAllowedAt: Long?, serverNowMs: Long): String? {
        if (nextAllowedAt == null || nextAllowedAt <= serverNowMs) return null
        return "Следна листа може за " + countdown(nextAllowedAt, serverNowMs)
    }

    /** The message for a refused or failed attempt; null when the list may go ahead. */
    fun notice(result: PermissionResult): LimitNotice? = when (result) {
        PermissionResult.Allowed, PermissionResult.NotNeeded -> null
        is PermissionResult.TooSoon -> LimitNotice(
            "Нова листа уште не може",
            "Следната листа може да се направи за ${countdown(result.nextAllowedAt, result.serverNowMs)}. " +
                "Рецептите од последната листа остануваат отворени до тогаш."
        )
        PermissionResult.GuestExpired -> LimitNotice(
            "Гостинскиот период заврши",
            "Поврзи Google сметка за да направиш нова листа. Постојната листа можеш и натаму да ја користиш и уредуваш.",
            linkGoogle = true
        )
        is PermissionResult.TooMany -> LimitNotice(
            "Најмногу ${result.max} рецепти по листа",
            "За една листа избери најмногу ${result.max} различни рецепти од серверот."
        )
        PermissionResult.NeedsInternet -> LimitNotice(
            "Потребна е интернет врска",
            "За нова листа апликацијата мора да ја провери со серверот. Обиди се повторно кога ќе имаш врска."
        )
        PermissionResult.TryLater -> LimitNotice("Серверот не е достапен", "Обиди се повторно за малку.")
        is PermissionResult.Problem -> LimitNotice("Не успеа", result.message)
    }

    const val LOCKED_TITLE = "Чекорите се заклучени"
    const val LOCKED_TEXT = "Целосниот рецепт (опис, чекори, белешки) се отвора кога ќе направиш листа за пазарење со овој рецепт."
}
