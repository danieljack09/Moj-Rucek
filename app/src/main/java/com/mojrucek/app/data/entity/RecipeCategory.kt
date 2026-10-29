package com.mojrucek.app.data.entity

import kotlinx.serialization.Serializable

@Serializable
enum class RecipeCategory(val displayName: String) {
    PILESKO("Пилешко"),
    SVINSKO("Свинско"),
    TELESKO("Телешко"),
    MELENO_MESO("Мелено месо"),
    RIBA("Риба"),
    PASTA("Паста"),
    ORIZ("Ориз"),
    KOMPIR("Компир"),
    GRAV_I_LEKJA("Грав и леќа"),
    BEZ_MESO("Без месо"),
    DRUGI_JADENJA("Други јадења");

    companion object {
        fun fromStorageName(name: String?): RecipeCategory? =
            entries.firstOrNull { it.name == name }
    }
}
