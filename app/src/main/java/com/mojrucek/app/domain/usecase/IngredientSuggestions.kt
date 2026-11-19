package com.mojrucek.app.domain.usecase

import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.IngredientCategory.JAJCA
import com.mojrucek.app.data.entity.IngredientCategory.KONZERVIRANA_HRANA
import com.mojrucek.app.data.entity.IngredientCategory.LEB_I_PEKARSKI_PROIZVODI
import com.mojrucek.app.data.entity.IngredientCategory.MASLA_I_DODATOCI
import com.mojrucek.app.data.entity.IngredientCategory.MESO
import com.mojrucek.app.data.entity.IngredientCategory.MLECNI_PROIZVODI
import com.mojrucek.app.data.entity.IngredientCategory.OVOSJE
import com.mojrucek.app.data.entity.IngredientCategory.PIJALOCI
import com.mojrucek.app.data.entity.IngredientCategory.ZACINI
import com.mojrucek.app.data.entity.IngredientCategory.ZAMRZNATA_HRANA
import com.mojrucek.app.data.entity.IngredientCategory.ZELENCUK
import com.mojrucek.app.data.entity.IngredientCategory.ZITARICI_I_TESTENINI
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.unit.MeasurementUnit.CAN
import com.mojrucek.app.domain.unit.MeasurementUnit.GRAM
import com.mojrucek.app.domain.unit.MeasurementUnit.KILOGRAM
import com.mojrucek.app.domain.unit.MeasurementUnit.LITER
import com.mojrucek.app.domain.unit.MeasurementUnit.MILLILITER
import com.mojrucek.app.domain.unit.MeasurementUnit.PACKAGE
import com.mojrucek.app.domain.unit.MeasurementUnit.PIECE

/**
 * A name the add-ingredient form can offer while the user types. [category]
 * and [unit] are null for names that only come from the user's own data
 * (recipes, inventory) and are not in the built-in [IngredientSuggestions.catalog].
 */
data class IngredientSuggestion(
    val name: String,
    val category: IngredientCategory? = null,
    val unit: MeasurementUnit? = null
)

/**
 * Autocomplete for ingredient names. Matches the start of the name or of any
 * word in it, and accepts the query in Cyrillic or Latin ("pav" and "пав"
 * both find "павлака"; "sirenje" and "sirene" both find "сирење").
 */
object IngredientSuggestions {

    private fun s(name: String, category: IngredientCategory, unit: MeasurementUnit) =
        IngredientSuggestion(name, category, unit)

    /** Offline fallback, used only until the first sync from the server succeeds. */
    val catalog: List<IngredientSuggestion> = listOf(
        // Зеленчук
        s("домати", ZELENCUK, KILOGRAM), s("краставици", ZELENCUK, PIECE), s("пиперки", ZELENCUK, PIECE),
        s("љути пиперки", ZELENCUK, PIECE), s("кромид", ZELENCUK, PIECE), s("млад кромид", ZELENCUK, PIECE),
        s("лук", ZELENCUK, PIECE), s("млад лук", ZELENCUK, PIECE), s("компир", ZELENCUK, KILOGRAM),
        s("морков", ZELENCUK, PIECE), s("зелка", ZELENCUK, PIECE), s("кисела зелка", ZELENCUK, PIECE),
        s("тиквички", ZELENCUK, PIECE), s("модри патлиџани", ZELENCUK, PIECE), s("спанаќ", ZELENCUK, GRAM),
        s("зелена салата", ZELENCUK, PIECE), s("магдонос", ZELENCUK, PIECE), s("копар", ZELENCUK, PIECE),
        s("целер", ZELENCUK, PIECE), s("праз", ZELENCUK, PIECE), s("карфиол", ZELENCUK, PIECE),
        s("брокула", ZELENCUK, PIECE), s("цвекло", ZELENCUK, PIECE), s("печурки", ZELENCUK, GRAM),
        s("боранија", ZELENCUK, GRAM), s("грашок", ZELENCUK, GRAM), s("пченка", ZELENCUK, PIECE),
        s("тиква", ZELENCUK, PIECE), s("ротквици", ZELENCUK, PIECE), s("блитва", ZELENCUK, GRAM),
        // Овошје
        s("јаболка", OVOSJE, KILOGRAM), s("банани", OVOSJE, PIECE), s("портокали", OVOSJE, KILOGRAM),
        s("лимон", OVOSJE, PIECE), s("мандарини", OVOSJE, KILOGRAM), s("круши", OVOSJE, KILOGRAM),
        s("грозје", OVOSJE, KILOGRAM), s("јагоди", OVOSJE, GRAM), s("праски", OVOSJE, KILOGRAM),
        s("кајсии", OVOSJE, KILOGRAM), s("сливи", OVOSJE, KILOGRAM), s("цреши", OVOSJE, GRAM),
        s("лубеница", OVOSJE, PIECE), s("диња", OVOSJE, PIECE), s("киви", OVOSJE, PIECE),
        s("суви сливи", OVOSJE, GRAM), s("суво грозје", OVOSJE, GRAM), s("ореви", OVOSJE, GRAM),
        // Месо
        s("пилешко месо", MESO, GRAM), s("пилешки гради", MESO, GRAM), s("пилешки батаци", MESO, GRAM),
        s("пилешки крилца", MESO, GRAM), s("свинско месо", MESO, GRAM), s("телешко месо", MESO, GRAM),
        s("јагнешко месо", MESO, GRAM), s("мелено месо", MESO, GRAM), s("колбаси", MESO, PIECE),
        s("виршли", MESO, PIECE), s("сланина", MESO, GRAM), s("шунка", MESO, GRAM),
        s("пршута", MESO, GRAM), s("салама", MESO, GRAM), s("кебапи", MESO, PIECE),
        s("плескавици", MESO, PIECE), s("риба (филети)", MESO, GRAM), s("пастрмка", MESO, PIECE),
        s("скуша", MESO, PIECE), s("ослич", MESO, GRAM),
        // Млечни производи
        s("млеко", MLECNI_PROIZVODI, LITER), s("кисело млеко", MLECNI_PROIZVODI, LITER),
        s("павлака", MLECNI_PROIZVODI, PACKAGE), s("кајмак", MLECNI_PROIZVODI, GRAM),
        s("сирење", MLECNI_PROIZVODI, GRAM), s("кашкавал", MLECNI_PROIZVODI, GRAM),
        s("путер", MLECNI_PROIZVODI, GRAM), s("јогурт", MLECNI_PROIZVODI, PACKAGE),
        s("урда", MLECNI_PROIZVODI, GRAM), s("моцарела", MLECNI_PROIZVODI, PACKAGE),
        s("пармезан", MLECNI_PROIZVODI, GRAM), s("слатка павлака", MLECNI_PROIZVODI, MILLILITER),
        s("крем сирење", MLECNI_PROIZVODI, PACKAGE), s("матеница", MLECNI_PROIZVODI, LITER),
        // Јајца
        s("јајца", JAJCA, PIECE),
        // Житарици и тестенини
        s("брашно", ZITARICI_I_TESTENINI, KILOGRAM), s("пченкарно брашно", ZITARICI_I_TESTENINI, GRAM),
        s("ориз", ZITARICI_I_TESTENINI, GRAM), s("шпагети", ZITARICI_I_TESTENINI, GRAM),
        s("макарони", ZITARICI_I_TESTENINI, GRAM), s("пенне", ZITARICI_I_TESTENINI, GRAM),
        s("резанки", ZITARICI_I_TESTENINI, GRAM), s("грав", ZITARICI_I_TESTENINI, GRAM),
        s("леќа", ZITARICI_I_TESTENINI, GRAM), s("наут", ZITARICI_I_TESTENINI, GRAM),
        s("овесни снегулки", ZITARICI_I_TESTENINI, GRAM), s("булгур", ZITARICI_I_TESTENINI, GRAM),
        s("гриз", ZITARICI_I_TESTENINI, GRAM), s("лазања кори", ZITARICI_I_TESTENINI, PACKAGE),
        // Леб и пекарски производи
        s("леб", LEB_I_PEKARSKI_PROIZVODI, PIECE), s("интегрален леб", LEB_I_PEKARSKI_PROIZVODI, PIECE),
        s("кори за пита", LEB_I_PEKARSKI_PROIZVODI, PACKAGE), s("лиснато тесто", LEB_I_PEKARSKI_PROIZVODI, PACKAGE),
        s("тортиљи", LEB_I_PEKARSKI_PROIZVODI, PACKAGE), s("презла", LEB_I_PEKARSKI_PROIZVODI, GRAM),
        s("квасец", LEB_I_PEKARSKI_PROIZVODI, PACKAGE), s("прашок за пециво", LEB_I_PEKARSKI_PROIZVODI, PACKAGE),
        // Конзервирана храна
        s("домати (сос)", KONZERVIRANA_HRANA, GRAM), s("пелати", KONZERVIRANA_HRANA, CAN),
        s("доматно пире", KONZERVIRANA_HRANA, PACKAGE), s("туна", KONZERVIRANA_HRANA, CAN),
        s("пченка (конзерва)", KONZERVIRANA_HRANA, CAN), s("грашок (конзерва)", KONZERVIRANA_HRANA, CAN),
        s("ајвар", KONZERVIRANA_HRANA, PIECE), s("маслинки", KONZERVIRANA_HRANA, GRAM),
        s("кисели краставички", KONZERVIRANA_HRANA, PIECE), s("сардини", KONZERVIRANA_HRANA, CAN),
        s("џем", KONZERVIRANA_HRANA, PIECE), s("мед", KONZERVIRANA_HRANA, PIECE),
        // Зачини
        s("сол", ZACINI, GRAM), s("шеќер", ZACINI, GRAM), s("црн пипер", ZACINI, PACKAGE),
        s("црвен пипер (зачин)", ZACINI, PACKAGE), s("лут пипер", ZACINI, PACKAGE), s("ловоров лист", ZACINI, PACKAGE),
        s("вегета", ZACINI, PACKAGE), s("чубрица", ZACINI, PACKAGE), s("босилек", ZACINI, PACKAGE),
        s("оригано", ZACINI, PACKAGE), s("ким", ZACINI, PACKAGE), s("цимет", ZACINI, PACKAGE),
        s("ванилин шеќер", ZACINI, PACKAGE), s("нане", ZACINI, PACKAGE),
        // Масла и сосови
        s("масло", MASLA_I_DODATOCI, LITER), s("маслиново масло", MASLA_I_DODATOCI, LITER),
        s("оцет", MASLA_I_DODATOCI, LITER), s("кечап", MASLA_I_DODATOCI, PIECE),
        s("мајонез", MASLA_I_DODATOCI, PIECE), s("сенф", MASLA_I_DODATOCI, PIECE),
        s("соја сос", MASLA_I_DODATOCI, PIECE), s("маргарин", MASLA_I_DODATOCI, GRAM),
        // Замрзната храна
        s("замрзнат зеленчук", ZAMRZNATA_HRANA, PACKAGE), s("помфрит", ZAMRZNATA_HRANA, PACKAGE),
        s("замрзнат грашок", ZAMRZNATA_HRANA, PACKAGE), s("замрзнати пици", ZAMRZNATA_HRANA, PIECE),
        s("сладолед", ZAMRZNATA_HRANA, PACKAGE),
        // Пијалаци
        s("вода", PIJALOCI, LITER), s("минерална вода", PIJALOCI, LITER), s("сок", PIJALOCI, LITER),
        s("кафе", PIJALOCI, GRAM), s("чај", PIJALOCI, PACKAGE), s("вино", PIJALOCI, LITER),
        s("пиво", PIJALOCI, LITER)
    )

    private val catalogByName = catalog.associateBy { it.name.lowercase() }

    /** Built-in category/unit for an exact name, if the catalog knows it. */
    fun lookup(name: String, source: List<IngredientSuggestion> = catalog): IngredientSuggestion? =
        if (source === catalog) catalogByName[name.trim().lowercase()]
        else source.firstOrNull { it.name.trim().lowercase() == name.trim().lowercase() }

    /**
     * Up to [limit] suggestions for [query]. [extraNames] (names from the
     * user's recipes and inventory) are offered too; when one matches a
     * catalog entry the catalog's category and unit are kept.
     */
    fun suggest(
        query: String,
        extraNames: Collection<String> = emptyList(),
        limit: Int = 6,
        source: List<IngredientSuggestion> = catalog
    ): List<IngredientSuggestion> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val queryForms = setOf(q, toLatin(q), toPlainLatin(q))

        val candidates = LinkedHashMap<String, IngredientSuggestion>()
        source.forEach { candidates[it.name.lowercase()] = it }
        extraNames.forEach { raw ->
            val name = raw.trim()
            if (name.isNotEmpty()) candidates.getOrPut(name.lowercase()) { IngredientSuggestion(name) }
        }

        return candidates.values
            .mapNotNull { suggestion ->
                val rank = rank(suggestion.name.lowercase(), queryForms) ?: return@mapNotNull null
                rank to suggestion
            }
            // Never suggest exactly what is already typed.
            .filterNot { (_, suggestion) -> suggestion.name.lowercase() == q }
            .sortedWith(compareBy({ it.first }, { it.second.name.length }, { it.second.name }))
            .take(limit)
            .map { it.second }
    }

    /** 0 = name starts with the query, 1 = a later word does, null = no match. */
    private fun rank(name: String, queryForms: Set<String>): Int? {
        val nameForms = listOf(name, toLatin(name), toPlainLatin(name))
        var best: Int? = null
        for (n in nameForms) for (q in queryForms) {
            val r = when {
                n.startsWith(q) -> 0
                n.split(' ', '(', '-').any { it.startsWith(q) } -> 1
                else -> null
            }
            if (r != null && (best == null || r < best)) best = r
        }
        return best
    }

    private val cyrillicToLatin = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ѓ' to "gj", 'е' to "e",
        'ж' to "zh", 'з' to "z", 'ѕ' to "dz", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l",
        'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'ќ' to "kj", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
        'ч' to "ch", 'џ' to "dzh", 'ш' to "sh"
    )

    /** Macedonian romanization: "сирење" → "sirenje". */
    private fun toLatin(text: String): String =
        buildString { text.forEach { append(cyrillicToLatin[it] ?: it.toString()) } }

    /** Romanization without digraphs, as people often type: "шеќер" → "seker", "сирење" → "sirene". */
    private fun toPlainLatin(text: String): String =
        toLatin(text).replace("zh", "z").replace("sh", "s").replace("ch", "c").replace("kj", "k")
            .replace("gj", "g").replace("lj", "l").replace("nj", "n").replace("dzh", "dz")
            .replace('č', 'c').replace('š', 's').replace('ž', 'z').replace('ć', 'c').replace('đ', 'd')
}
