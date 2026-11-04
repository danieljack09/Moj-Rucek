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

    /** Breakfast, lunch and dinner additions; tagged "појадок" / "ручек" / "вечера". */
    val mealTimeRecipes: List<SeedRecipe> = listOf(
        // ---- Појадок ----
        SeedRecipe(
            name = "Мекици",
            description = "Пржени меки тесто-лепчиња, се служат со сирење, џем или мед.",
            servings = 4,
            prepTimeMinutes = 70,
            cookTimeMinutes = 20,
            category = RecipeCategory.DRUGI_JADENJA,
            tags = listOf("појадок", "традиционално"),
            steps = listOf(
                "Квасецот раствори го во малку млако млеко со лажичка шеќер и остави 10 минути.",
                "Во сад измешај брашно, сол, кисело млеко, јајце и квасецот.",
                "Замеси меко лепливо тесто, покриј и остави да втаса околу 1 час.",
                "Со намастени раце откинувај парчиња тесто и развлечи ги во тенки лепчиња.",
                "Пржи во загреано масло од двете страни додека порумени, па стави ги на хартиена салфетка.",
                "Служи топли со сирење, џем или мед."
            ),
            ingredients = listOf(
                ing("брашно", 500.0, MeasurementUnit.GRAM),
                ing("кисело млеко", 300.0, MeasurementUnit.MILLILITER),
                ing("млеко", 100.0, MeasurementUnit.MILLILITER),
                ing("јајца", 1.0, MeasurementUnit.PIECE),
                ing("квасец", 1.0, MeasurementUnit.PACKAGE),
                ing("шеќер", 5.0, MeasurementUnit.GRAM),
                ing("сол", 8.0, MeasurementUnit.GRAM),
                ing("масло", 400.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Попара",
            description = "Стар леб натопен во топло млеко со сирење и путер — брз домашен појадок.",
            servings = 2,
            prepTimeMinutes = 5,
            cookTimeMinutes = 5,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("појадок", "брзо", "вегетаријанско"),
            steps = listOf(
                "Лебот исечи на коцки и стави го во длабок сад.",
                "Млекото загреј го до вриење со малку сол.",
                "Прелеј го топлото млеко врз лебот и остави 2 минути да омекне.",
                "Додади издробено сирење и парче путер, промешај и служи топло."
            ),
            ingredients = listOf(
                ing("леб", 0.5, MeasurementUnit.PIECE),
                ing("млеко", 500.0, MeasurementUnit.MILLILITER),
                ing("сирење", 100.0, MeasurementUnit.GRAM),
                ing("путер", 20.0, MeasurementUnit.GRAM),
                ing("сол", 2.0, MeasurementUnit.GRAM)
            )
        ),
        SeedRecipe(
            name = "Зелник со сирење",
            description = "Пита од кори полнета со сирење, јајца и кисело млеко.",
            servings = 6,
            prepTimeMinutes = 20,
            cookTimeMinutes = 40,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("појадок", "печено", "вегетаријанско"),
            steps = listOf(
                "Во сад истепај ги јајцата, додади издробено сирење и половина од киселото млеко.",
                "Остатокот од киселото млеко измешај со маслото — со ова ќе ги прскаш корите.",
                "Подмачкај плех и редај по две кори, прскај ги со смесата од масло и млеко.",
                "На секои неколку кори распореди од филот со сирење.",
                "Заврши со две кори, премачкај одозгора и исечи на парчиња.",
                "Печи на 200°C околу 35–40 минути додека порумени."
            ),
            ingredients = listOf(
                ing("кори за пита", 1.0, MeasurementUnit.PACKAGE),
                ing("сирење", 400.0, MeasurementUnit.GRAM),
                ing("јајца", 4.0, MeasurementUnit.PIECE),
                ing("кисело млеко", 500.0, MeasurementUnit.MILLILITER),
                ing("масло", 100.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Кајгана со пиперки и домати",
            description = "Летна кајгана со пржени пиперки, домати и сирење.",
            servings = 2,
            prepTimeMinutes = 10,
            cookTimeMinutes = 15,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("појадок", "брзо", "вегетаријанско"),
            steps = listOf(
                "Пиперките исечи на ленти, а доматите на коцки.",
                "Пиперките пропржи на масло околу 5 минути.",
                "Додади ги доматите и динстај додека не испари течноста.",
                "Истепај ги јајцата со сол, прелеј ги врз зеленчукот и мешај додека се стегнат.",
                "Посипи со издробено сирење и служи со леб."
            ),
            ingredients = listOf(
                ing("јајца", 4.0, MeasurementUnit.PIECE),
                ing("пиперки", 3.0, MeasurementUnit.PIECE),
                ing("домати", 2.0, MeasurementUnit.PIECE),
                ing("сирење", 60.0, MeasurementUnit.GRAM),
                ing("масло", 30.0, MeasurementUnit.MILLILITER),
                ing("сол", 2.0, MeasurementUnit.GRAM)
            )
        ),
        SeedRecipe(
            name = "Пофезни",
            description = "Парчиња леб натопени во јајца и млеко, пржени до златно.",
            servings = 2,
            prepTimeMinutes = 5,
            cookTimeMinutes = 10,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("појадок", "брзо"),
            steps = listOf(
                "Лебот исечи на парчиња дебели околу 1,5 cm.",
                "Истепај ги јајцата со млеко и малку сол.",
                "Секое парче леб накратко натопи го во смесата од двете страни.",
                "Пржи во загреано масло по 2 минути од секоја страна.",
                "Служи со сирење, кисело млеко или посипано со шеќер."
            ),
            ingredients = listOf(
                ing("леб", 0.5, MeasurementUnit.PIECE),
                ing("јајца", 3.0, MeasurementUnit.PIECE),
                ing("млеко", 100.0, MeasurementUnit.MILLILITER),
                ing("масло", 60.0, MeasurementUnit.MILLILITER),
                ing("сол", 1.0, MeasurementUnit.GRAM)
            )
        ),
        SeedRecipe(
            name = "Палачинки",
            description = "Тенки палачинки со џем, мед или сирење.",
            servings = 4,
            prepTimeMinutes = 10,
            cookTimeMinutes = 25,
            category = RecipeCategory.DRUGI_JADENJA,
            tags = listOf("појадок", "слатко"),
            steps = listOf(
                "Истепај ги јајцата, додади млеко, малку сол и шеќер.",
                "Постепено додавај брашно и мешај до мазна ретка смеса без грутки.",
                "Додади малку масло во смесата и остави ја 10 минути да отстои.",
                "На загреана подмачкана тава истури тенок слој смеса и печи по 1 минута од секоја страна.",
                "Полни ги со џем, мед или издробено сирење и завиткај."
            ),
            ingredients = listOf(
                ing("брашно", 250.0, MeasurementUnit.GRAM),
                ing("млеко", 500.0, MeasurementUnit.MILLILITER),
                ing("јајца", 3.0, MeasurementUnit.PIECE),
                ing("шеќер", 15.0, MeasurementUnit.GRAM),
                ing("масло", 40.0, MeasurementUnit.MILLILITER),
                ing("џем", 1.0, MeasurementUnit.PIECE)
            )
        ),
        // ---- Ручек ----
        SeedRecipe(
            name = "Пастрмајлија",
            description = "Штипска пита од тесто со ситно сечено свинско месо и јајце.",
            servings = 4,
            prepTimeMinutes = 90,
            cookTimeMinutes = 25,
            category = RecipeCategory.SVINSKO,
            tags = listOf("ручек", "традиционално", "печено"),
            steps = listOf(
                "Месото исечи на ситни коцки, посоли и остави да отстои додека се прави тестото.",
                "Од брашно, квасец, млака вода, сол и малку масло замеси меко тесто и остави да втаса 1 час.",
                "Тестото подели на 4 топки и развлечи ги во издолжени лепчиња со подигнати рабови.",
                "Распореди го месото врз тестото и додади малку лута пиперка по желба.",
                "Печи на 250°C околу 15 минути, па во средината скрши јајце и печи уште 5 минути.",
                "Служи топло, премачкано со малку путер."
            ),
            ingredients = listOf(
                ing("брашно", 500.0, MeasurementUnit.GRAM),
                ing("квасец", 1.0, MeasurementUnit.PACKAGE),
                ing("свинско месо", 500.0, MeasurementUnit.GRAM),
                ing("јајца", 4.0, MeasurementUnit.PIECE),
                ing("путер", 30.0, MeasurementUnit.GRAM),
                ing("љути пиперки", 2.0, MeasurementUnit.PIECE),
                ing("сол", 10.0, MeasurementUnit.GRAM)
            )
        ),
        SeedRecipe(
            name = "Селско месо",
            description = "Свинско месо со печурки, пиперки и кромид, задушено во глинено тавче.",
            servings = 4,
            prepTimeMinutes = 20,
            cookTimeMinutes = 60,
            category = RecipeCategory.SVINSKO,
            tags = listOf("ручек", "традиционално"),
            steps = listOf(
                "Месото исечи на коцки и пропржи го на масло од сите страни.",
                "Додади сецкан кромид и пиперки и динстај 10 минути.",
                "Додади ги печурките, црвен пипер, сол и бибер и малку вода.",
                "Пренеси во тавче, покриј и печи на 180°C околу 45 минути.",
                "Во последните 10 минути посипи со рендан кашкавал и печи откриено."
            ),
            ingredients = listOf(
                ing("свинско месо", 700.0, MeasurementUnit.GRAM),
                ing("печурки", 300.0, MeasurementUnit.GRAM),
                ing("кромид", 2.0, MeasurementUnit.PIECE),
                ing("пиперки", 3.0, MeasurementUnit.PIECE),
                ing("кашкавал", 100.0, MeasurementUnit.GRAM),
                ing("црвен пипер (зачин)", 1.0, MeasurementUnit.PACKAGE),
                ing("масло", 50.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Турли тава",
            description = "Печен мешан зеленчук со месо — „турли“ значи „од сè по малку“.",
            servings = 6,
            prepTimeMinutes = 30,
            cookTimeMinutes = 90,
            category = RecipeCategory.TELESKO,
            tags = listOf("ручек", "традиционално", "печено"),
            steps = listOf(
                "Месото исечи на коцки и пропржи го со кромидот.",
                "Компирот, тиквичките, модрите патлиџани и пиперките исечи на парчиња.",
                "Во тавче измешај го месото, зеленчукот, бораниата и ориз, посоли и зачини.",
                "Одозгора подреди кругчиња домати и прелеј со масло и 300 ml вода.",
                "Покриј и печи на 200°C околу 1 час, па откриј и печи уште 30 минути."
            ),
            ingredients = listOf(
                ing("телешко месо", 500.0, MeasurementUnit.GRAM),
                ing("компир", 500.0, MeasurementUnit.GRAM),
                ing("тиквички", 2.0, MeasurementUnit.PIECE),
                ing("модри патлиџани", 1.0, MeasurementUnit.PIECE),
                ing("пиперки", 3.0, MeasurementUnit.PIECE),
                ing("боранија", 200.0, MeasurementUnit.GRAM),
                ing("домати", 3.0, MeasurementUnit.PIECE),
                ing("кромид", 2.0, MeasurementUnit.PIECE),
                ing("ориз", 50.0, MeasurementUnit.GRAM),
                ing("масло", 80.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Полнети тиквички",
            description = "Тиквички полнети со мелено месо и ориз, печени во доматен сос.",
            servings = 4,
            prepTimeMinutes = 30,
            cookTimeMinutes = 50,
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("ручек", "печено"),
            steps = listOf(
                "Тиквичките преполови ги по должина и со лажица извади ја средината.",
                "Мелено месо измешај со оризот, сецкан кромид, сол, бибер и магдонос.",
                "Наполни ги тиквичките и подреди ги во плех.",
                "Доматниот сос разреди го со малку вода и прелеј го околу тиквичките.",
                "Покриј со фолија и печи на 200°C 35 минути, па откриј и печи уште 15 минути.",
                "Служи со кисело млеко."
            ),
            ingredients = listOf(
                ing("тиквички", 4.0, MeasurementUnit.PIECE),
                ing("мелено месо", 400.0, MeasurementUnit.GRAM),
                ing("ориз", 80.0, MeasurementUnit.GRAM),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("домати (сос)", 300.0, MeasurementUnit.GRAM),
                ing("магдонос", 1.0, MeasurementUnit.PIECE),
                ing("кисело млеко", 250.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Ѓувеч",
            description = "Ориз печен со месо и зеленчук во тавче.",
            servings = 5,
            prepTimeMinutes = 25,
            cookTimeMinutes = 60,
            category = RecipeCategory.ORIZ,
            tags = listOf("ручек", "печено", "семејно"),
            steps = listOf(
                "Свинското месо исечи на коцки и пропржи со кромид.",
                "Додади сецкани пиперки, морков и домати и динстај 10 минути.",
                "Додади го оризот, промешај и зачини со сол, бибер и црвен пипер.",
                "Пренеси во тавче и налеј двојно повеќе вода од оризот.",
                "Печи на 200°C околу 45 минути додека оризот ја впие водата."
            ),
            ingredients = listOf(
                ing("свинско месо", 500.0, MeasurementUnit.GRAM),
                ing("ориз", 300.0, MeasurementUnit.GRAM),
                ing("пиперки", 2.0, MeasurementUnit.PIECE),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("домати", 2.0, MeasurementUnit.PIECE),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("црвен пипер (зачин)", 1.0, MeasurementUnit.PACKAGE),
                ing("масло", 50.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Боранија манџа",
            description = "Посна манџа од зелена боранија, компир и домати.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 40,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("ручек", "посно", "вегетаријанско"),
            steps = listOf(
                "Бораниата исчисти ја и исечи на парчиња од 3 cm.",
                "Кромидот и лукот пропржи ги на масло.",
                "Додади ја бораниата, компирот на коцки и морковот, и пржи уште 5 минути.",
                "Додади домати, црвен пипер, сол и вода колку да ги покрие.",
                "Вари на тивок оган околу 35 минути, на крај посипи со магдонос."
            ),
            ingredients = listOf(
                ing("боранија", 500.0, MeasurementUnit.GRAM),
                ing("компир", 300.0, MeasurementUnit.GRAM),
                ing("морков", 1.0, MeasurementUnit.PIECE),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("лук", 2.0, MeasurementUnit.PIECE),
                ing("домати", 2.0, MeasurementUnit.PIECE),
                ing("магдонос", 1.0, MeasurementUnit.PIECE),
                ing("масло", 50.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Охридска пастрмка",
            description = "Пастрмка печена во рерна со лук, лимон и магдонос.",
            servings = 2,
            prepTimeMinutes = 10,
            cookTimeMinutes = 25,
            category = RecipeCategory.RIBA,
            tags = listOf("ручек", "риба", "лесно"),
            steps = listOf(
                "Пастрмките исчисти ги, измиј ги и исуши ги.",
                "Посоли ги однадвор и одвнатре и во стомакот стави кругчиња лимон и лук.",
                "Подреди ги во подмачкан плех и прелеј со масло.",
                "Печи на 200°C околу 25 минути.",
                "Служи посипано со магдонос, со компир или салата."
            ),
            ingredients = listOf(
                ing("пастрмка", 2.0, MeasurementUnit.PIECE),
                ing("лимон", 1.0, MeasurementUnit.PIECE),
                ing("лук", 3.0, MeasurementUnit.PIECE),
                ing("магдонос", 1.0, MeasurementUnit.PIECE),
                ing("маслиново масло", 40.0, MeasurementUnit.MILLILITER),
                ing("сол", 5.0, MeasurementUnit.GRAM)
            )
        ),
        // ---- Вечера ----
        SeedRecipe(
            name = "Шопска салата",
            description = "Домати, краставици, пиперки и кромид, покриени со рендано сирење.",
            servings = 2,
            prepTimeMinutes = 10,
            cookTimeMinutes = 0,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("вечера", "салата", "брзо"),
            steps = listOf(
                "Доматите, краставиците и пиперките исечи на коцки.",
                "Кромидот исечи ситно и додај го во садот.",
                "Посоли и прелеј со масло и малку оцет, па промешај.",
                "Одозгора изрендај обилно сирење и посипи со магдонос."
            ),
            ingredients = listOf(
                ing("домати", 3.0, MeasurementUnit.PIECE),
                ing("краставици", 1.0, MeasurementUnit.PIECE),
                ing("пиперки", 1.0, MeasurementUnit.PIECE),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("сирење", 150.0, MeasurementUnit.GRAM),
                ing("масло", 30.0, MeasurementUnit.MILLILITER),
                ing("оцет", 10.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Пинџур со сирење",
            description = "Печени пиперки, модри патлиџани и домати истолчени со лук.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 30,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("вечера", "традиционално", "вегетаријанско"),
            steps = listOf(
                "Пиперките, модрите патлиџани и доматите испечи ги во рерна на 220°C.",
                "Покриј ги 10 минути за полесно да се излупат, па излупи ги.",
                "Ситно исецкај ги или истолчи ги со лукот.",
                "Динстај ги на масло 10 минути, посоли по вкус.",
                "Служи ладно со сирење и леб."
            ),
            ingredients = listOf(
                ing("пиперки", 6.0, MeasurementUnit.PIECE),
                ing("модри патлиџани", 1.0, MeasurementUnit.PIECE),
                ing("домати", 3.0, MeasurementUnit.PIECE),
                ing("лук", 3.0, MeasurementUnit.PIECE),
                ing("масло", 60.0, MeasurementUnit.MILLILITER),
                ing("сирење", 200.0, MeasurementUnit.GRAM),
                ing("леб", 0.5, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Ќебапи со кромид",
            description = "Домашни ќебапи на скара или тава, со сецкан кромид и леб.",
            servings = 4,
            prepTimeMinutes = 20,
            cookTimeMinutes = 15,
            category = RecipeCategory.MELENO_MESO,
            tags = listOf("вечера", "скара"),
            steps = listOf(
                "Мелено месо измешај со сол, бибер, ситно сецкан лук и малку сода бикарбона или вода.",
                "Измеси добро и остави смесата во фрижидер 1 час (по можност).",
                "Обликувај ќебапи долги околу 8 cm.",
                "Печи на загреана скара или тава по 6–7 минути, вртејќи ги.",
                "Служи со ситно сецкан кромид, леб и ајвар."
            ),
            ingredients = listOf(
                ing("мелено месо", 600.0, MeasurementUnit.GRAM),
                ing("лук", 2.0, MeasurementUnit.PIECE),
                ing("кромид", 2.0, MeasurementUnit.PIECE),
                ing("леб", 1.0, MeasurementUnit.PIECE),
                ing("ајвар", 1.0, MeasurementUnit.PIECE),
                ing("сол", 8.0, MeasurementUnit.GRAM),
                ing("црн пипер", 1.0, MeasurementUnit.PACKAGE)
            )
        ),
        SeedRecipe(
            name = "Пржени тиквички со кисело млеко",
            description = "Тиквички во тесто, пржени, со сос од кисело млеко и лук.",
            servings = 3,
            prepTimeMinutes = 15,
            cookTimeMinutes = 20,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("вечера", "вегетаријанско"),
            steps = listOf(
                "Тиквичките исечи на кругчиња, посоли ги и остави 10 минути, па исуши ги.",
                "Истепај ги јајцата.",
                "Секое кругче вивни го во брашно, па во јајце.",
                "Пржи во загреано масло додека порумени од двете страни.",
                "Киселото млеко измешај со ситно сецкан лук, копар и сол и служи го како сос."
            ),
            ingredients = listOf(
                ing("тиквички", 3.0, MeasurementUnit.PIECE),
                ing("брашно", 100.0, MeasurementUnit.GRAM),
                ing("јајца", 2.0, MeasurementUnit.PIECE),
                ing("масло", 200.0, MeasurementUnit.MILLILITER),
                ing("кисело млеко", 400.0, MeasurementUnit.MILLILITER),
                ing("лук", 2.0, MeasurementUnit.PIECE),
                ing("копар", 1.0, MeasurementUnit.PIECE)
            )
        ),
        SeedRecipe(
            name = "Бурек пиперки",
            description = "Пиперки полнети со сирење и јајца, пржени во тесто.",
            servings = 4,
            prepTimeMinutes = 30,
            cookTimeMinutes = 20,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("вечера", "традиционално", "вегетаријанско"),
            steps = listOf(
                "Пиперките испечи ги, излупи ги и внимателно извади ги семките.",
                "Сирењето издроби го и измешај го со едно јајце.",
                "Наполни ги пиперките со филот.",
                "Вивни ги во брашно, па во истепаното јајце.",
                "Пржи во загреано масло додека добијат златна кора."
            ),
            ingredients = listOf(
                ing("пиперки", 8.0, MeasurementUnit.PIECE),
                ing("сирење", 300.0, MeasurementUnit.GRAM),
                ing("јајца", 3.0, MeasurementUnit.PIECE),
                ing("брашно", 100.0, MeasurementUnit.GRAM),
                ing("масло", 200.0, MeasurementUnit.MILLILITER)
            )
        ),
        SeedRecipe(
            name = "Пилешки ражничиња",
            description = "Маринирани пилешки гради на стапчиња со пиперки и кромид.",
            servings = 4,
            prepTimeMinutes = 40,
            cookTimeMinutes = 15,
            category = RecipeCategory.PILESKO,
            tags = listOf("вечера", "скара"),
            steps = listOf(
                "Пилешките гради исечи на коцки.",
                "Маринирај со масло, лимонов сок, лук, сол и црвен пипер барем 30 минути.",
                "Наредувај на стапчиња наизменично месо, пиперка и кромид.",
                "Печи на скара или тава по 12–15 минути, вртејќи ги.",
                "Служи со шопска салата или помфрит."
            ),
            ingredients = listOf(
                ing("пилешки гради", 600.0, MeasurementUnit.GRAM),
                ing("пиперки", 2.0, MeasurementUnit.PIECE),
                ing("кромид", 1.0, MeasurementUnit.PIECE),
                ing("лимон", 1.0, MeasurementUnit.PIECE),
                ing("лук", 2.0, MeasurementUnit.PIECE),
                ing("масло", 40.0, MeasurementUnit.MILLILITER),
                ing("црвен пипер (зачин)", 1.0, MeasurementUnit.PACKAGE)
            )
        ),
        SeedRecipe(
            name = "Таратор",
            description = "Ладна супа од кисело млеко, краставици, лук и ореви.",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 0,
            category = RecipeCategory.BEZ_MESO,
            tags = listOf("вечера", "летно", "брзо"),
            steps = listOf(
                "Краставиците излупи ги и исечи ги на ситни коцки или изрендај ги.",
                "Киселото млеко разреди го со малку ладна вода.",
                "Додади ги краставиците, ситно сецкан лук, копар, сол и масло.",
                "Посипи со скршени ореви и служи добро изладено."
            ),
            ingredients = listOf(
                ing("кисело млеко", 1000.0, MeasurementUnit.MILLILITER),
                ing("краставици", 2.0, MeasurementUnit.PIECE),
                ing("лук", 3.0, MeasurementUnit.PIECE),
                ing("копар", 1.0, MeasurementUnit.PIECE),
                ing("ореви", 50.0, MeasurementUnit.GRAM),
                ing("масло", 20.0, MeasurementUnit.MILLILITER)
            )
        )
    )
}
