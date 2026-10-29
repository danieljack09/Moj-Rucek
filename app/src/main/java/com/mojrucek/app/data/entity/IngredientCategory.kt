package com.mojrucek.app.data.entity

import kotlinx.serialization.Serializable

@Serializable
enum class IngredientCategory(val displayName: String) {
    MESO("Месо"),
    ZELENCUK("Зеленчук"),
    OVOSJE("Овошје"),
    MLECNI_PROIZVODI("Млечни производи"),
    ZITARICI_I_TESTENINI("Житарици и тестенини"),
    KONZERVIRANA_HRANA("Конзервирана храна"),
    ZACINI("Зачини"),
    MASLA_I_DODATOCI("Масла и додатоци"),
    ZAMRZNATA_HRANA("Замрзната храна"),
    DRUGO("Друго");

    companion object {
        fun fromStorageName(name: String?): IngredientCategory =
            entries.firstOrNull { it.name == name } ?: DRUGO
    }
}
