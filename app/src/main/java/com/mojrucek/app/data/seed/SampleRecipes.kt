package com.mojrucek.app.data.seed

import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.domain.unit.MeasurementUnit

/**
 * A starter set of realistic Macedonian home-cooked lunches, used to
 * pre-populate the database on first run. These are only examples — the
 * user adds their own 50-100 recipes through the Рецепти screen.
 */
data class SeedIngredient(val name: String, val quantity: Double, val unit: MeasurementUnit)

data class SeedRecipe(
    val name: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val category: RecipeCategory,
    val tags: List<String>,
    val steps: List<String>,
    val ingredients: List<SeedIngredient>
)

object SampleRecipes {

    private fun ing(name: String, qty: Double, unit: MeasurementUnit) = SeedIngredient(name, qty, unit)

    val all: List<SeedRecipe> = listOf(
        SeedRecipe(
            name = "Тавче гравче",
            description = "Традиционално македонско јадење со грав печен во тавче.",
            servings = 4,
            prepTimeMinutes = 20,
            cookTimeMinutes = 90,
            category = RecipeCategory.GRAV_I_LEKJA,
            tags = listOf("традиционално", "вегетаријанско"),
            steps = listOf(
                "Гравот да се намочи претходно во вода преку ноќ.",
                "Прво завриј го гравот во вода со кромид додека омекне.",
                "Направи запршка од брашно, црвен пипер и масло.",
                "Пренеси го гравот во тавче, додади ја запршката, сецкан лук и пипер.",
                "Печи во рерна на 180°C околу 40 минути додека се образува кора."
            ),
            ingredients = listOf(
                ing("грав", 500.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("брашно", 2.0, MeasurementUnit.PIECE),
                ing("црвен пипер (зачин)", 1.0, MeasurementUnit.PIECE),
                ing("масло", 60.0, MeasurementUnit.MILLILITER),
                ing("лук", 3.0, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Мусака",
            description = "Слоевито јадење со компир, мелено месо и јајца.",
            servings = 6,
            prepTimeMinutes = 30,
            cookTimeMinutes = 50,
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("печено", "семејно"),
            steps = listOf(
                "Компирите исечи на кругчиња и пропржи ги лесно.",
                "Мелено месо пропржи со кромид, сол и бибер.",
                "Редај слоеви компир и месо во плех.",
                "Истепај јајца со млеко и прелеј врз последниот слој.",
                "Печи на 190°C околу 40 минути."
            ),
            ingredients = listOf(
                ing("компир", 1.0, MeasurementUnit.KILOGRAM),
                ing("мелено месо", 500.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("јајца", 3.0, MeasurementUnit.PIECE),
                ing("млеко", 300.0, MeasurementUnit.MILLILITER),
                ing("масло", 40.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Полнети пиперки",
            description = "Пиперки полнети со ориз и мелено месо, варени во сос од домати.",
            servings = 4,
            prepTimeMinutes = 25,
            cookTimeMinutes = 45,
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("домашно"),
            steps = listOf(
                "Измешај мелено месо со ориз, сецкан кромид и зачини.",
                "Исчисти ги пиперките и наполни ги со смесата.",
                "Подреди ги во тенџере и прелеј со сос од домати.",
                "Вари на тивок оган околу 40 минути."
            ),
            ingredients = listOf(
                ing("пиперки", 6.0, MeasurementUnit.PIECE),
                ing("мелено месо", 400.0, MeasurementUnit.GRAM),
                ing("ориз", 150.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("домати (сос)", 400.0, MeasurementUnit.GRAM)
            )
        ),
        SeedRecipe(
            name = "Пилешко со ориз",
            description = "Брзо и ситно јадење, идеално за секојдневен ручек.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 35,
            category = RecipeCategory.PILESKO,
            tags = listOf("брзо", "секојдневно"),
            steps = listOf(
                "Испржи ги парчињата пилешко месо во масло.",
                "Додади ситно сецкан кромид и морков и динстај неколку минути.",
                "Додади го оризот и прелеј со вода.",
                "Вари на тивок оган околу 20 минути."
            ),
            ingredients = listOf(
                ing("пилешко месо", 500.0, MeasurementUnit.GRAM),
                ing("ориз", 200.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("масло", 30.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Ќофтиња со компир пире",
            description = "Класични домашни ќофтиња, послужени со пире.",
            servings = 4,
            prepTimeMinutes = 25,
            cookTimeMinutes = 30,
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("семејно"),
            steps = listOf(
                "Измешај мелено месо со јајце, лук, сол и бибер.",
                "Обликувај ги ќофтињата и испржи ги во тава.",
                "Свари компир и изгмечи го со млеко и масло за пире."
            ),
            ingredients = listOf(
                ing("мелено месо", 500.0, MeasurementUnit.GRAM),
                ing("јајца", 1.0, MeasurementUnit.PIECE),
                ing("лук", 2.0, MeasurementUnit.PIECE),
                ing("компир", 800.0, MeasurementUnit.GRAM),
                ing("млеко", 100.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Сарма",
            description = "Зелка полнета со ориз и мелено месо.",
            servings = 6,
            prepTimeMinutes = 40,
            cookTimeMinutes = 90,
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("празнично", "традиционално"),
            steps = listOf(
                "Одвои ги листовите од кисела зелка.",
                "Измешај мелено месо, ориз, кромид и зачини за фил.",
                "Завиткај го филот во листовите зелка.",
                "Подреди ги сармите во тенџере и вари околу 1 час и 30 минути."
            ),
            ingredients = listOf(
                ing("кисела зелка", 1.0, MeasurementUnit.PIECE),
                ing("мелено месо", 500.0, MeasurementUnit.GRAM),
                ing("ориз", 150.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("масло", 40.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Леќа чорба",
            description = "Згуснета чорба од леќа, лесна за подготовка.",
            servings = 4,
            prepTimeMinutes = 10,
            cookTimeMinutes = 40,
            category = RecipeCategory.GRAV_I_LEKJA,
            tags = listOf("вегетаријанско", "брзо"),
            steps = listOf(
                "Испржи кромид, морков и целер на масло.",
                "Додади ја леќата и прелеј со вода.",
                "Вари околу 35 минути додека леќата омекне.",
                "Задими со брашно по желба за погуста чорба."
            ),
            ingredients = listOf(
                ing("леќа", 300.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("масло", 30.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Грашок со месо",
            description = "Варен грашок со парчиња месо, домашен стил.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 50,
            category = RecipeCategory.TELESKO,
            tags = listOf("домашно"),
            steps = listOf(
                "Испржи го месото на коцки заедно со кромид.",
                "Додади го грашокот и морков, прелеј со вода.",
                "Вари на тивок оган околу 40 минути."
            ),
            ingredients = listOf(
                ing("телешко месо", 400.0, MeasurementUnit.GRAM),
                ing("грашок", 400.0, MeasurementUnit.GRAM),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("кромид", 1.0, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Компир манџа",
            description = "Едноставна варена манџа со компир и месо.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 40,
            category = RecipeCategory.KOMPIR,
            tags = listOf("брзо", "евтино"),
            steps = listOf(
                "Испржи кромид и парчиња месо.",
                "Додади компир исечен на коцки и прелеј со вода.",
                "Вари додека компирот омекне, околу 30 минути."
            ),
            ingredients = listOf(
                ing("компир", 800.0, MeasurementUnit.GRAM),
                ing("свинско месо", 300.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Пилешка супа",
            description = "Лесна супа, одлична за секој ден.",
            servings = 4,
            prepTimeMinutes = 10,
            cookTimeMinutes = 60,
            category = RecipeCategory.PILESKO,
            tags = listOf("лесно сварливо"),
            steps = listOf(
                "Вари пилешко месо со морков, целер и кромид околу 50 минути.",
                "Извади го месото, исечи го на парчиња и врати го во супата.",
                "Додади тенки резанки и довари уште неколку минути."
            ),
            ingredients = listOf(
                ing("пилешко месо", 500.0, MeasurementUnit.GRAM),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("целер", 1.0, MeasurementUnit.PIECE),
                ing("резанки", 100.0, MeasurementUnit.GRAM)
            )
        ),
        SeedRecipe(
            name = "Шпагети болоњезе",
            description = "Паста со сос од мелено месо и домати.",
            servings = 4,
            prepTimeMinutes = 10,
            cookTimeMinutes = 35,
            category = RecipeCategory.PASTA,
            tags = listOf("брзо", "семејно"),
            steps = listOf(
                "Испржи мелено месо со кромид и лук.",
                "Додади сос од домати и вари на тивок оган 20 минути.",
                "Свари ги шпагетите и послужи со сосот."
            ),
            ingredients = listOf(
                ing("шпагети", 400.0, MeasurementUnit.GRAM),
                ing("мелено месо", 350.0, MeasurementUnit.GRAM),
                ing("домати (сос)", 400.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("лук", 2.0, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Риба на тава",
            description = "Испржена риба со едноставна зеленчукова гарнитура.",
            servings = 3,
            prepTimeMinutes = 15,
            cookTimeMinutes = 20,
            category = RecipeCategory.RIBA,
            tags = listOf("лесно"),
            steps = listOf(
                "Исчисти ја рибата и обложи ја во брашно.",
                "Испржи ја во масло од двете страни.",
                "Послужи со свежа зеленчукова салата."
            ),
            ingredients = listOf(
                ing("риба (филети)", 600.0, MeasurementUnit.GRAM),
                ing("брашно", 100.0, MeasurementUnit.GRAM),
                ing("масло", 50.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Пченкарница",
            description = "Домашна погача со кашкавал и јајца, без месо.",
            servings = 6,
            prepTimeMinutes = 15,
            cookTimeMinutes = 35,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("вегетаријанско", "погача"),
            steps = listOf(
                "Измешај пченкарно брашно, јајца, кисело млеко и кашкавал.",
                "Истури ја смесата во намастен плех.",
                "Печи на 200°C околу 30 минути."
            ),
            ingredients = listOf(
                ing("пченкарно брашно", 300.0, MeasurementUnit.GRAM),
                ing("јајца", 3.0, MeasurementUnit.PIECE),
                ing("кисело млеко", 400.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Гравче на супа",
            description = "Леплива чорба-гравче, полесна верзија од тавче гравче.",
            servings = 4,
            prepTimeMinutes = 10,
            cookTimeMinutes = 70,
            category = RecipeCategory.GRAV_I_LEKJA,
            tags = listOf("вегетаријанско"),
            steps = listOf(
                "Намоченот грав свари во вода со морков и кромид.",
                "Пропржи го со црвен пипер и масло, прелеј назад во гравот.",
                "Довари уште 10-тина минути."
            ),
            ingredients = listOf(
                ing("грав", 400.0, MeasurementUnit.GRAM),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("кромид", 1.0, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Пилешки батаци во рерна",
            description = "Печени батаци со зачини и компир.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 50,
            category = RecipeCategory.PILESKO,
            tags = listOf("печено", "лесно"),
            steps = listOf(
                "Намачкај ги батаците со зачини и масло.",
                "Подреди ги во плех со компир на четвртинки.",
                "Печи на 200°C околу 45 минути."
            ),
            ingredients = listOf(
                ing("пилешки батаци", 800.0, MeasurementUnit.GRAM),
                ing("компир", 600.0, MeasurementUnit.GRAM),
                ing("масло", 40.0, MeasurementUnit.MILLILITER)
            )
        )
    )
}
