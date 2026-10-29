package com.mojrucek.app.domain.unit

import kotlinx.serialization.Serializable

/**
 * All units supported by the app. [group] decides which units are mutually
 * convertible: WEIGHT units convert to/from each other, VOLUME units convert
 * to/from each other, and each COUNT unit is only ever compatible with itself
 * (a "парче" is never silently treated as a "пакување").
 */
@Serializable
enum class MeasurementUnit(val displayName: String, val group: UnitGroup) {
    GRAM("g", UnitGroup.WEIGHT),
    KILOGRAM("kg", UnitGroup.WEIGHT),
    MILLILITER("ml", UnitGroup.VOLUME),
    LITER("l", UnitGroup.VOLUME),
    PIECE("парче", UnitGroup.COUNT),
    PACKAGE("пакување", UnitGroup.COUNT),
    CAN("конзерва", UnitGroup.COUNT);

    companion object {
        fun fromStorageName(name: String): MeasurementUnit =
            entries.firstOrNull { it.name == name } ?: PIECE
    }
}

enum class UnitGroup {
    WEIGHT, VOLUME, COUNT
}
