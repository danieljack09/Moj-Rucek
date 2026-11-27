package com.mojrucek.app.domain.text

import com.mojrucek.app.domain.unit.MeasurementUnit
import kotlin.math.abs
import kotlin.math.floor

/**
 * Macedonian number agreement for the words the user sees.
 *
 * The noun takes its singular form when the number ends in 1 but not in 11
 * ("1 намирница", "21 намирница", "101 рецепт"), and the plural (or, for
 * masculine nouns such as "ден", the count form "дена") otherwise
 * ("2 намирници", "11 намирници", "5 дена"). A fractional amount always takes
 * the plural ("1.5 парчиња").
 */
object MkPlural {

    fun isSingular(n: Long): Boolean {
        val a = abs(n)
        return a % 10 == 1L && a % 100 != 11L
    }

    /** The right form of the noun for [n], without the number. */
    fun word(n: Int, one: String, many: String): String = if (isSingular(n.toLong())) one else many

    /** "[n] [noun]" with the noun in the right form, e.g. count(3, "рецепт", "рецепти") = "3 рецепти". */
    fun count(n: Int, one: String, many: String): String = "$n ${word(n, one, many)}"

    /** The right form of the noun for a possibly fractional amount. */
    fun wordFor(amount: Double, one: String, many: String): String {
        val rounded = Math.round(amount * 100.0) / 100.0
        val whole = rounded == floor(rounded)
        return if (whole && isSingular(rounded.toLong())) one else many
    }

    // Nouns used with counts across the app (singular, plural).
    fun recipes(n: Int) = count(n, "рецепт", "рецепти")
    fun ingredients(n: Int) = count(n, "намирница", "намирници")
    fun items(n: Int) = count(n, "ставка", "ставки")
    fun plans(n: Int) = count(n, "план", "планови")
    fun servings(n: Int) = count(n, "порција", "порции")
    fun days(n: Int) = count(n, "ден", "дена")
    fun lunches(n: Int) = count(n, "ручек", "ручеци")

    /** Unit name that agrees with [amount]: "1 парче", "2 парчиња"; g, kg, ml and l never change. */
    fun unitLabel(amount: Double, unit: MeasurementUnit): String = when (unit) {
        MeasurementUnit.PIECE -> wordFor(amount, "парче", "парчиња")
        MeasurementUnit.PACKAGE -> wordFor(amount, "пакување", "пакувања")
        MeasurementUnit.CAN -> wordFor(amount, "конзерва", "конзерви")
        else -> unit.displayName
    }
}
