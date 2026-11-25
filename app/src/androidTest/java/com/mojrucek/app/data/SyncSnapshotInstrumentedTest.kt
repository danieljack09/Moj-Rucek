package com.mojrucek.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.sync.SnapshotFingerprint
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository
import com.mojrucek.app.sync.FileSafetyCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Two in-memory databases play two phones: what one exports for sync, the other must import correctly. */
@RunWith(AndroidJUnit4::class)
class SyncSnapshotInstrumentedTest {

    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbs = mutableListOf<AppDatabase>()
    private val tempDirs = mutableListOf<File>()

    @After fun tearDown() { dbs.forEach { it.close() }; tempDirs.forEach { it.deleteRecursively() } }

    private class Phone(val db: AppDatabase, val owner: MutableStateFlow<String>) {
        val currentOwner = CurrentOwner.following(owner)
        val recipes = RecipeRepository(db.recipeDao(), db.favoriteDao(), currentOwner)
        val ingredients = IngredientRepository(db.ingredientDao(), currentOwner)
        val shopping = ShoppingListRepository(db.shoppingListDao(), ingredients, currentOwner)
        val planner = PlannerRepository(db.plannerDao(), recipes, ingredients, shopping, currentOwner)
        val settings = SettingsRepository(db.appSettingsDao())
        val backup = BackupRepository(ingredients, recipes, shopping, db.plannerDao(), settings, db, currentOwner)
    }

    private fun phone(uid: String = "uid-A"): Phone {
        val db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        dbs += db
        return Phone(db, MutableStateFlow(uid))
    }

    private suspend fun Phone.addServerRecipe(serverId: Long, name: String): Long = recipes.upsert(
        Recipe(name = name, isUserCreated = false, serverId = serverId, prepTimeMinutes = 5, cookTimeMinutes = 20),
        listOf(RecipeIngredient(recipeId = 0, name = "Ориз", normalizedName = "ориз", quantity = 100.0, unit = MeasurementUnit.GRAM))
    )

    /** The account's data: pantry, a shopping item, an own recipe with steps and lines, two favourites, a plan with two meals. */
    private suspend fun Phone.seed(): Pair<Long, Long> {
        val own = recipes.upsert(
            Recipe(name = "Мојата мусака", instructionSteps = listOf("прв", "втор", "трет"), tags = listOf("т1", "т2"), notes = "белешка"),
            listOf(RecipeIngredient(recipeId = 0, name = "Компир", normalizedName = "компир", quantity = 1.0, unit = MeasurementUnit.KILOGRAM))
        )
        val server = addServerRecipe(77, "Од сервер")
        recipes.setFavorite(own, true)
        recipes.setFavorite(server, true)
        db.ingredientDao().insert(Ingredient(name = "Јајца", normalizedName = "јајца", category = IngredientCategory.JAJCA, quantity = 6.0, unit = MeasurementUnit.PIECE, ownerUid = owner.value))
        db.shoppingListDao().insert(ShoppingListItem(name = "Млеко", normalizedName = "млеко", quantity = 1.0, unit = MeasurementUnit.LITER, ownerUid = owner.value))
        val plan = planner.getOrCreatePlan(LocalDate.of(2026, 11, 23), defaultServings = 2)
        planner.setMeal(plan.weeklyPlan.id, dayOfWeek = 1, recipeId = own)
        planner.setMeal(plan.weeklyPlan.id, dayOfWeek = 2, recipeId = server)
        return own to server
    }

    private fun list(snapshot: String, key: String): JsonArray = Json.parseToJsonElement(snapshot).jsonObject[key]!!.jsonArray

    // ---------- what goes into the snapshot ----------

    @Test fun theSnapshot_leavesOutDeviceSettings_andNonFavouriteServerRecipes() = runBlocking {
        val p = phone()
        p.seed()
        p.addServerRecipe(88, "Не е омилен")
        p.settings.ensureInitialized()
        p.settings.update { it.copy(reminderHour = 7, reminderEnabled = true) }

        val snap = p.backup.exportForSync()

        val settings = Json.parseToJsonElement(snap).jsonObject["settings"]!!.jsonObject
        assertNotEquals("the phone's own reminder hour is not in the snapshot", 7, settings["reminderHour"]!!.jsonPrimitive.int)
        val names = list(snap, "recipes").map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("Мојата мусака", "Од сервер"), names)
        val server = list(snap, "recipes").first { it.jsonObject["serverId"]?.jsonPrimitive?.content == "77" }.jsonObject
        assertEquals("a server recipe is a stub: no dates", "0", server["createdAt"]!!.jsonPrimitive.content)
        assertEquals("0", server["updatedAt"]!!.jsonPrimitive.content)
        assertEquals("and its lines are not in it", 1, list(snap, "recipeIngredients").size)
    }

    @Test fun theFingerprint_isStableForUnchangedData_andIgnoresTheCatalogChangingByItself() = runBlocking {
        val p = phone()
        val (_, server) = p.seed()
        val before = SnapshotFingerprint.of(p.backup.exportForSync())
        assertEquals("two exports of the same data", before, SnapshotFingerprint.of(p.backup.exportForSync()))

        // The server catalog changes the recipe's lines and dates by itself: the account did not change.
        p.recipes.upsert(
            p.recipes.findByServerId(77)!!.copy(updatedAt = 12345, createdAt = 777),
            listOf(RecipeIngredient(recipeId = 0, name = "Нешто друго", normalizedName = "нешто друго", quantity = 9.0, unit = MeasurementUnit.GRAM))
        )
        assertEquals(before, SnapshotFingerprint.of(p.backup.exportForSync()))
        assertTrue(server > 0)
    }

    @Test fun theFingerprint_changesForEveryRealChange() = runBlocking {
        val p = phone()
        val (own, server) = p.seed()
        val base = SnapshotFingerprint.of(p.backup.exportForSync())
        fun changed() = runBlocking { SnapshotFingerprint.of(p.backup.exportForSync()) != base }

        p.recipes.setFavorite(server, false)
        assertTrue("an unticked favourite", changed())
        p.recipes.setFavorite(server, true)
        assertFalse("back to the start", changed())

        val ingredient = p.db.ingredientDao().getAllOnce(p.owner.value).single()
        p.db.ingredientDao().update(ingredient.copy(quantity = 5.0))
        assertTrue("a pantry quantity", changed())
        p.db.ingredientDao().update(ingredient)
        assertFalse(changed())

        val recipe = p.recipes.getWithIngredients(own)!!
        p.recipes.upsert(recipe.recipe.copy(instructionSteps = listOf("втор", "прв", "трет")), recipe.ingredients)
        assertTrue("two steps swapped", changed())
    }

    // ---------- a second phone ----------

    @Test fun aNewPhone_importsEverything_withNewIdsButTheSameMeaning() = runBlocking {
        val a = phone(); a.seed()
        val snap = a.backup.exportForSync()

        val b = phone()
        b.addServerRecipe(77, "Од сервер")           // the catalog arrives by its own sync
        assertTrue(b.backup.importForSync(snap) is ImportResult.Success)

        assertEquals(1, b.ingredients.getAllOnce().size)
        assertEquals("Јајца", b.ingredients.getAllOnce().single().name)
        assertEquals("Млеко", b.shopping.getAllOnce().single().name)
        val own = b.recipes.getAllWithIngredientsOnce().first { it.recipe.serverId == null }
        assertEquals("Мојата мусака", own.recipe.name)
        assertEquals(listOf("прв", "втор", "трет"), own.recipe.instructionSteps)
        assertEquals(listOf("Компир"), own.ingredients.map { it.name })
        assertTrue("own favourite", own.recipe.isFavorite)
        val serverRecipe = b.recipes.findByServerId(77)!!
        assertEquals("the catalog recipe was kept, not duplicated", 1, b.db.recipeDao().getAllWithIngredientsOnce("uid-A").count { it.recipe.serverId == 77L })
        assertTrue(b.recipes.getAllWithIngredientsOnce().first { it.recipe.serverId == 77L }.recipe.isFavorite)
        val plan = b.db.plannerDao().getAllWithMealsOnce("uid-A").single()
        assertEquals(2, plan.meals.size)
        assertEquals("the meal follows the recipe to its new id", own.recipe.id, plan.meals.first { it.dayOfWeek == 1 }.recipeId)
        assertEquals(serverRecipe.id, plan.meals.first { it.dayOfWeek == 2 }.recipeId)
    }

    @Test fun afterImporting_theNewPhoneExportsTheSameContent_soItIsNotSeenAsChanged() = runBlocking {
        val a = phone(); a.seed()
        val b = phone(); b.addServerRecipe(77, "Од сервер")
        b.backup.importForSync(a.backup.exportForSync())
        val after = SnapshotFingerprint.of(b.backup.exportForSync())
        assertEquals(after, SnapshotFingerprint.of(b.backup.exportForSync()))
        // and what B would now upload says the same things as A's snapshot (ids aside)
        fun facts(s: String) = listOf("ingredients", "shoppingListItems").map { k -> list(s, k).map { it.jsonObject["name"]!!.jsonPrimitive.content }.sorted() } +
            listOf(list(s, "recipes").map { it.jsonObject["name"]!!.jsonPrimitive.content }.sorted())
        assertEquals(facts(a.backup.exportForSync()), facts(b.backup.exportForSync()))
    }

    @Test fun importingForSync_keepsThePhonesOwnReminderSettings() = runBlocking {
        val a = phone(); a.seed()
        a.settings.ensureInitialized()
        a.settings.update { it.copy(reminderHour = 6, reminderEnabled = false) }
        val b = phone()
        b.settings.ensureInitialized()
        b.settings.update { it.copy(reminderHour = 19, reminderEnabled = true) }

        b.backup.importForSync(a.backup.exportForSync())

        val kept = b.settings.getOnce()
        assertEquals(19, kept.reminderHour)
        assertTrue(kept.reminderEnabled)
    }

    @Test fun importingForSync_replacesOnlyTheSignedInAccountsData() = runBlocking {
        val a = phone(); a.seed()
        val b = phone("uid-B")
        b.db.ingredientDao().insert(Ingredient(name = "Чуден", normalizedName = "чуден", category = IngredientCategory.DRUGO, quantity = 1.0, unit = MeasurementUnit.PIECE, ownerUid = "uid-B"))
        b.db.ingredientDao().insert(Ingredient(name = "Туѓо", normalizedName = "туѓо", category = IngredientCategory.DRUGO, quantity = 1.0, unit = MeasurementUnit.PIECE, ownerUid = "uid-OTHER"))

        b.backup.importForSync(a.backup.exportForSync())

        assertEquals(listOf("Јајца"), b.db.ingredientDao().getAllOnce("uid-B").map { it.name })
        assertEquals("another account's pantry is untouched", listOf("Туѓо"), b.db.ingredientDao().getAllOnce("uid-OTHER").map { it.name })
    }

    @Test fun aBrokenSnapshot_changesNothing() = runBlocking {
        val b = phone(); b.seed()
        val before = SnapshotFingerprint.of(b.backup.exportForSync())
        for (bad in listOf("", "not json", """{"formatVersion":99}""", """{"formatVersion":1,"plannedMeals":[{"id":1,"weeklyPlanId":42,"dayOfWeek":1,"updatedAt":0,"uuid":"x"}]}""")) {
            assertTrue(bad, b.backup.importForSync(bad) is ImportResult.InvalidFile)
        }
        assertEquals(before, SnapshotFingerprint.of(b.backup.exportForSync()))
    }

    // ---------- the safety copy ----------

    @Test fun theSafetyCopy_isWrittenAndReadBack_andOnlyTheNewestFiveAreKept() {
        val dir = File(target.cacheDir, "sync-safety-${System.nanoTime()}").apply { mkdirs() }.also { tempDirs += it }
        val context = object : android.content.ContextWrapper(target) { override fun getFilesDir(): File = dir }
        val safety = FileSafetyCopy(context)
        repeat(8) { i -> assertTrue(safety.save("""{"formatVersion":1,"copy":$i}""")); Thread.sleep(5) }

        val files = File(dir, FileSafetyCopy.DIR).listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertEquals(5, files.size)
        assertEquals("""{"formatVersion":1,"copy":3}""", files.first().readText())
        assertEquals("""{"formatVersion":1,"copy":7}""", files.last().readText())
        assertTrue("no half-written files are left behind", File(dir, FileSafetyCopy.DIR).listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
