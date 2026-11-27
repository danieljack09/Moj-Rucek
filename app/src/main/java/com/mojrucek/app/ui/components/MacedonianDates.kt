package com.mojrucek.app.ui.components

import java.time.LocalDate

/** Macedonian day and month names for date labels, independent of the device locale. */
val MacedonianDayNames = listOf("Понеделник", "Вторник", "Среда", "Четврток", "Петок", "Сабота", "Недела")

val MacedonianMonthNames = listOf(
    "јануари", "февруари", "март", "април", "мај", "јуни",
    "јули", "август", "септември", "октомври", "ноември", "декември"
)

/** "Четврток, 26 ноември". */
fun LocalDate.longDateMk(): String =
    "${MacedonianDayNames[dayOfWeek.value - 1]}, $dayOfMonth ${MacedonianMonthNames[monthValue - 1]}"
