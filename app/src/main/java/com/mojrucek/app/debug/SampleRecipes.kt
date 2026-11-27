package com.mojrucek.app.debug

import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.RecipeRepository
import java.time.LocalDate

/**
 * Three complete sample recipes and a few kitchen items, for testing DEBUG builds only.
 * Called from MojRucekApplication behind BuildConfig.DEBUG; the photos live in the debug source set
 * (app/src/debug/assets/sample_recipes), so a release build has neither the assets nor a caller.
 *
 * Everything goes through the normal repository calls, exactly as the app saves a recipe or a kitchen
 * item, so the data belongs to the signed-in account like anything the user creates. Idempotent: a
 * recipe whose name already exists and a kitchen item that is already there are skipped.
 *
 * Coverage is designed on purpose:
 *  - Пилешко со ориз: every ingredient is in the kitchen in a sufficient amount (fully covered);
 *  - Тавче гравче: 4 of 9 ingredients are in the kitchen (partly covered);
 *  - Мусака: none of its ingredient names is in the kitchen (all missing).
 * One kitchen item is low on stock (Морков) and one expires in 2 days (Пилешки гради).
 */
object SampleRecipes {

    private const val ASSETS = "file:///android_asset/sample_recipes/"

    private class Line(val name: String, val quantity: Double, val unit: MeasurementUnit, val notes: String? = null)

    private class Sample(val recipe: Recipe, val lines: List<Line>)

    private class KitchenItem(
        val name: String,
        val category: IngredientCategory,
        val quantity: Double,
        val unit: MeasurementUnit,
        val expiresInDays: Long? = null,
        val lowStockThreshold: Double? = null,
        val notes: String? = null
    )

    private val tavcheGravche = Sample(
        Recipe(
            name = "Тавче гравче",
            description = "Традиционално македонско јадење од бел грав печен во земјена тава. " +
                "Гравот прво се вари, а потоа се пече со запршка од кромид и црвен пипер додека не се создаде убава корка. " +
                "Најубаво е со свеж леб и туршија.",
            servings = 4,
            prepTimeMinutes = 20,
            cookTimeMinutes = 150,
            instructionSteps = listOf(
                "Гравот исчистете го, измијте го и потопете го во ладна вода најмалку осум часа или преку ноќ.",
                "Исцедете го гравот, прелијте го со свежа вода и варете го десет минути, па фрлете ја првата вода.",
                "Прелијте го гравот со околу два литри топла вода и варете го на тивок оган околу еден час, додека не омекне.",
                "Додека гравот се вари, исечкајте го кромидот ситно и пропржете го на зејтин додека не стане мек и златен.",
                "Тргнете ја тавата од оганот, додадете ги брашното и мелениот црвен пипер и брзо измешајте за да не загори пиперот.",
                "Запршката додадете ја во гравот заедно со сушените пиперки, нането, солта и црниот пипер, и промешајте.",
                "Префрлете го гравот со дел од течноста во земјена тава; течноста треба малку да го надминува гравот.",
                "Печете во рерна загреана на 200 °C околу 45 до 60 минути, додека одозгора не се создаде корка и сосот не се згусне.",
                "Оставете го да отстои десетина минути пред сервирање."
            ),
            category = RecipeCategory.GRAV_I_LEKJA,
            tags = listOf("традиционално", "посно", "во рерна"),
            notes = "Најдобар е гравот тетовец. Ако сакате посилен вкус, додадете една лута пиперка заедно со сушените пиперки.",
            imageUri = ASSETS + "tavche_gravche.jpg",
            isFavorite = true
        ),
        listOf(
            Line("Грав тетовец", 1.0, MeasurementUnit.PACKAGE, "500 g"),
            Line("Кромид", 2.0, MeasurementUnit.PIECE),
            Line("Сушени црвени пиперки", 4.0, MeasurementUnit.PIECE),
            Line("Зејтин", 100.0, MeasurementUnit.MILLILITER),
            Line("Брашно", 20.0, MeasurementUnit.GRAM),
            Line("Слатка пиперка (мелена)", 10.0, MeasurementUnit.GRAM),
            Line("Сушено нане", 5.0, MeasurementUnit.GRAM),
            Line("Сол", 10.0, MeasurementUnit.GRAM),
            Line("Црн пипер", 3.0, MeasurementUnit.GRAM)
        )
    )

    private val musaka = Sample(
        Recipe(
            name = "Мусака",
            description = "Домашна мусака со компири и мелено месо, прелиена со кремаст прелив од јајца и млеко. " +
                "Слоевите се печат во рерна додека врвот не стане златен. " +
                "Јадење што секогаш оди со кисело млеко и свежа салата.",
            servings = 6,
            prepTimeMinutes = 30,
            cookTimeMinutes = 60,
            instructionSteps = listOf(
                "Компирите излупете ги, измијте ги и исечете ги на кругови дебели околу половина сантиметар.",
                "Во длабока тава ставете го путерот и пропржете го меленото месо додека не ја испушти водата и не порумени.",
                "Додадете ги доматниот концентрат, сушениот магдонос и црниот пипер, посолете по вкус и пржете уште пет минути.",
                "Подмачкајте тава за печење и наредете слој компири, па слој месо; повторете, а последниот слој нека биде од компири.",
                "Налијте околу 200 ml вода по рабовите, покријте со фолија и печете 40 минути на 200 °C.",
                "Во меѓувреме изматете ги јајцата со млекото и брашното во мазен прелив, па додадете го ренданиот кашкавал.",
                "Тргнете ја фолијата, прелијте ја мусаката со преливот и вратете ја во рерната.",
                "Печете уште 20 минути без фолија, додека преливот не се стегне и не порумени.",
                "Оставете ја мусаката да отстои 15 минути пред сечење, за да се држат слоевите."
            ),
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("во рерна", "за ручек", "семејно"),
            notes = "Може да се подготви и со модар патлиџан наместо дел од компирите. Солта додајте ја по вкус кога го пржите месото.",
            imageUri = ASSETS + "musaka.jpg"
        ),
        listOf(
            Line("Мелено месо (мешано)", 500.0, MeasurementUnit.GRAM),
            Line("Компири", 1.2, MeasurementUnit.KILOGRAM),
            Line("Јајца", 3.0, MeasurementUnit.PIECE),
            Line("Млеко", 400.0, MeasurementUnit.MILLILITER),
            Line("Брашно", 40.0, MeasurementUnit.GRAM),
            Line("Путер", 50.0, MeasurementUnit.GRAM),
            Line("Доматен концентрат", 1.0, MeasurementUnit.CAN),
            Line("Кашкавал", 100.0, MeasurementUnit.GRAM),
            Line("Сушен магдонос", 5.0, MeasurementUnit.GRAM),
            Line("Црн пипер", 3.0, MeasurementUnit.GRAM)
        )
    )

    private val pileshkoSoOriz = Sample(
        Recipe(
            name = "Пилешко со ориз",
            description = "Едноставно и заситно јадење од пилешки гради и ориз печени заедно во една тава. " +
                "Морковот и грашокот му даваат боја, а мелената пиперка убав вкус. " +
                "Брзо се подготвува и е идеално за ручек преку неделата.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 45,
            instructionSteps = listOf(
                "Пилешките гради исечете ги на поголеми коцки, посолете ги и поспете ги со половина од мелената пиперка.",
                "Кромидот исечкајте го ситно, а морковот исечете го на тенки кругчиња.",
                "Во тава загрејте го зејтинот и пропржете ги пилешките коцки од сите страни, па тргнете ги на страна.",
                "Во истата тава пропржете ги кромидот и морковот пет минути, додека не омекнат.",
                "Додадете го измиениот ориз и пржете го две минути, мешајќи, додека не стане проѕирен.",
                "Додадете ја останатата мелена пиперка, грашокот и 600 ml топла вода, и посолете.",
                "Наредете го месото врз оризот, покријте и печете во рерна на 200 °C околу 30 минути.",
                "Проверете дали оризот ја впил водата; ако е сув, додадете малку вода и печете уште пет минути."
            ),
            category = RecipeCategory.PILESKO,
            tags = listOf("брзо", "за ручек", "во рерна"),
            notes = "Наместо гради можат да се користат и бутчиња без коска; тогаш печете ги десетина минути подолго.",
            imageUri = ASSETS + "pileshko_so_oriz.jpg"
        ),
        listOf(
            Line("Пилешки гради", 600.0, MeasurementUnit.GRAM),
            Line("Ориз", 300.0, MeasurementUnit.GRAM),
            Line("Морков", 2.0, MeasurementUnit.PIECE),
            Line("Кромид", 1.0, MeasurementUnit.PIECE),
            Line("Грашок (замрзнат)", 1.0, MeasurementUnit.PACKAGE, "450 g"),
            Line("Зејтин", 50.0, MeasurementUnit.MILLILITER),
            Line("Сол", 8.0, MeasurementUnit.GRAM),
            Line("Слатка пиперка (мелена)", 5.0, MeasurementUnit.GRAM)
        )
    )

    /** All of Пилешко со ориз; that also covers 4 of the 9 Тавче гравче lines; nothing of Мусака. */
    private val kitchen = listOf(
        KitchenItem("Пилешки гради", IngredientCategory.MESO, 800.0, MeasurementUnit.GRAM, expiresInDays = 2),
        KitchenItem("Ориз", IngredientCategory.ZITARICI_I_TESTENINI, 1.0, MeasurementUnit.KILOGRAM),
        KitchenItem("Морков", IngredientCategory.ZELENCUK, 2.0, MeasurementUnit.PIECE, lowStockThreshold = 3.0),
        KitchenItem("Кромид", IngredientCategory.ZELENCUK, 3.0, MeasurementUnit.PIECE),
        KitchenItem("Грашок (замрзнат)", IngredientCategory.ZAMRZNATA_HRANA, 2.0, MeasurementUnit.PACKAGE),
        KitchenItem("Зејтин", IngredientCategory.MASLA_I_DODATOCI, 1.0, MeasurementUnit.LITER),
        KitchenItem("Сол", IngredientCategory.ZACINI, 500.0, MeasurementUnit.GRAM),
        KitchenItem("Слатка пиперка (мелена)", IngredientCategory.ZACINI, 100.0, MeasurementUnit.GRAM)
    )

    /**
     * Inserts what is missing for the signed-in account. Returns how many recipes and kitchen items it added.
     * Call only when an account is signed in (the repositories stamp every row with the current owner).
     */
    suspend fun seed(recipes: RecipeRepository, ingredients: IngredientRepository): Pair<Int, Int> {
        val existingNames = recipes.getAllWithIngredientsOnce()
            .map { RecipeIngredient.normalize(it.recipe.name) }
            .toSet()
        var addedRecipes = 0
        for (sample in listOf(tavcheGravche, musaka, pileshkoSoOriz)) {
            if (RecipeIngredient.normalize(sample.recipe.name) in existingNames) continue
            val lines = sample.lines.mapIndexed { index, line ->
                RecipeIngredient(
                    recipeId = 0,
                    name = line.name,
                    normalizedName = RecipeIngredient.normalize(line.name),
                    quantity = line.quantity,
                    unit = line.unit,
                    notes = line.notes,
                    sortOrder = index
                )
            }
            val id = recipes.upsert(sample.recipe, lines)
            // The favourite mark lives in its own table (per account); this is the call the heart button makes.
            if (sample.recipe.isFavorite) recipes.setFavorite(id, true)
            addedRecipes++
        }

        val today = LocalDate.now().toEpochDay()
        var addedItems = 0
        for (item in kitchen) {
            if (ingredients.findExistingByName(item.name) != null) continue
            ingredients.insert(
                name = item.name,
                category = item.category,
                quantity = item.quantity,
                unit = item.unit,
                expirationDateEpochDay = item.expiresInDays?.let { today + it },
                lowStockThreshold = item.lowStockThreshold,
                notes = item.notes
            )
            addedItems++
        }
        return addedRecipes to addedItems
    }
}
