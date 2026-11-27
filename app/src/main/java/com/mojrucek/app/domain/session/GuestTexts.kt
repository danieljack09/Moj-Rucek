package com.mojrucek.app.domain.session

import com.mojrucek.app.domain.text.MkPlural

/** The words the user sees about the guest period (Macedonian), kept apart from the screens so they can be tested. */
object GuestTexts {

    /** "1 ден", "21 ден", "5 дена", "11 дена": see [MkPlural]. */
    fun days(n: Int): String = MkPlural.days(n)

    /** The line under "Сметка" in Settings for a guest. */
    fun settingsLine(status: GuestPeriod.Status): String = when (status) {
        GuestPeriod.Status.NotAGuest -> "Гостин"
        is GuestPeriod.Status.Running -> "Гостин · уште ${days(status.daysLeft)}"
        GuestPeriod.Status.Expired -> "Гостин · гостинскиот период заврши"
    }

    fun reminderTitle(daysBefore: Int): String =
        if (daysBefore == 1) "Гостинскиот период завршува утре" else "Гостинскиот период завршува за ${days(daysBefore)}"

    const val REMINDER_TEXT = "Поврзи Google сметка за да ја задржиш сметката. Твоите податоци остануваат исти."

    const val EXPIRED_TITLE = "Гостинскиот период заврши"
    const val EXPIRED_TEXT = "Сè уште можеш да користиш сè како досега. Поврзи Google сметка за да ја задржиш сметката."
}
