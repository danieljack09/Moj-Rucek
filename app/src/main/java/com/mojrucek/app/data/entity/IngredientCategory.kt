package com.mojrucek.app.data.entity

import kotlinx.serialization.Serializable

/**
 * Food categories for the ingredient inventory. Stored in the database by
 * [name] (see `Converters.fromIngredientCategory`/`toIngredientCategory`),
 * never by [displayName] or [emoji] — both of those can change freely
 * without touching existing data, and adding a new entry here never breaks
 * data saved under the previous set of categories ([fromStorageName] falls
 * back to [DRUGO] for anything it doesn't recognize).
 */
@Serializable
enum class IngredientCategory(val displayName: String, val emoji: String) {
    ZELENCUK("Зеленчук", "🥕"),
    OVOSJE("Овошје", "🍎"),
    MESO("Месо", "🍗"),
    MLECNI_PROIZVODI("Млечни производи", "🥛"),
    ZITARICI_I_TESTENINI("Житарици и тестенини", "🌾"),
    LEB_I_PEKARSKI_PROIZVODI("Леб и пекарски производи", "🍞"),
    KONZERVIRANA_HRANA("Конзервирана храна", "🥫"),
    ZACINI("Зачини", "🌶️"),
    MASLA_I_DODATOCI("Масла и сосови", "🫒"),
    ZAMRZNATA_HRANA("Замрзната храна", "❄️"),
    JAJCA("Јајца", "🥚"),
    PIJALOCI("Пијалаци", "🧃"),
    DRUGO("Друго", "🛒");

    companion object {
        fun fromStorageName(name: String?): IngredientCategory =
            entries.firstOrNull { it.name == name } ?: DRUGO
    }
}
