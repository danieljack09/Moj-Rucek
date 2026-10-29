package com.mojrucek.app.domain.unit

/**
 * Pure, testable unit conversion logic.
 *
 * Weight (g/kg) and volume (ml/l) units convert freely within their group.
 * Count-style units (парче, пакување, конзерва) are each their own group and
 * only "convert" to themselves — the app must never guess, for example, that
 * 2 парче equals 1 пакување.
 */
object UnitConverter {

    /** Base unit multiplier: how many base units (g or ml) one unit equals. */
    private fun baseMultiplier(unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> 1.0
        MeasurementUnit.KILOGRAM -> 1000.0
        MeasurementUnit.MILLILITER -> 1.0
        MeasurementUnit.LITER -> 1000.0
        MeasurementUnit.PIECE, MeasurementUnit.PACKAGE, MeasurementUnit.CAN -> 1.0
    }

    /**
     * True if [a] and [b] can be meaningfully compared/added: same group,
     * and for COUNT units, the exact same unit.
     */
    fun areCompatible(a: MeasurementUnit, b: MeasurementUnit): Boolean {
        if (a.group != b.group) return false
        if (a.group == UnitGroup.COUNT) return a == b
        return true
    }

    /**
     * Converts [amount] of [from] into the equivalent amount of [to].
     * Returns null when the units are not compatible (see [areCompatible]).
     */
    fun convert(amount: Double, from: MeasurementUnit, to: MeasurementUnit): Double? {
        if (!areCompatible(from, to)) return null
        if (from == to) return amount
        val inBase = amount * baseMultiplier(from)
        return inBase / baseMultiplier(to)
    }

    /**
     * Converts [amount] of [unit] into its group's base unit (grams for
     * weight, milliliters for volume, the unit itself for count units).
     * Useful for summing/comparing quantities of the same ingredient.
     */
    fun toBase(amount: Double, unit: MeasurementUnit): Double = amount * baseMultiplier(unit)

    /** The unit that [toBase] expresses amounts in, for a given group. */
    fun baseUnitFor(group: UnitGroup, countUnit: MeasurementUnit? = null): MeasurementUnit = when (group) {
        UnitGroup.WEIGHT -> MeasurementUnit.GRAM
        UnitGroup.VOLUME -> MeasurementUnit.MILLILITER
        UnitGroup.COUNT -> countUnit ?: MeasurementUnit.PIECE
    }

    /** Formats a quantity for display, trimming trailing zeros sensibly. */
    fun formatQuantity(amount: Double): String {
        val rounded = Math.round(amount * 100.0) / 100.0
        return if (rounded == Math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
