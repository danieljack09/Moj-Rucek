package com.mojrucek.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a realistic version-3 database to version 4 and checks that nothing was lost or changed,
 * and that the new ownership/sync columns, the per-owner plan index and the favourites table are right.
 * Uses a throw-away database file, never the app's real data.
 */
@RunWith(AndroidJUnit4::class)
class Migration3To4InstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val dbName = "migration-3-4-test"

    @After
    fun deleteTestDatabase() {
        // The helper does not remove its database file; do not leave test data in the app's folder.
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    private val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    /** A small but complete version-3 database: every table filled, links between them, favourites of both kinds. */
    private fun createV3(): SupportSQLiteDatabase {
        val db = helper.createDatabase(dbName, 3)
        // recipes: 1 = from the server and a favourite, 2 = own recipe and a favourite, 3 = own, not a favourite
        db.execSQL("INSERT INTO recipes VALUES (1,'Тавче гравче','опис',4,20,90,'[\"чекор 1\",\"чекор 2\"]','GRAV_I_LEKJA','[\"традиционално\"]',NULL,NULL,1,0,1000,77)")
        db.execSQL("INSERT INTO recipes VALUES (2,'Мојата мусака',NULL,6,30,50,'[]','MELENO_MESO','[]','белешка',NULL,1,1,2000,NULL)")
        db.execSQL("INSERT INTO recipes VALUES (3,'Салата',NULL,2,10,0,'[]',NULL,'[]',NULL,NULL,0,1,3000,NULL)")
        db.execSQL("INSERT INTO recipe_ingredients VALUES (1,1,'грав','грав',500.0,'GRAM',NULL,0)")
        db.execSQL("INSERT INTO recipe_ingredients VALUES (2,1,'кромид','кромид',1.0,'PIECE','ситно',1)")
        db.execSQL("INSERT INTO recipe_ingredients VALUES (3,2,'компир','компир',1.0,'KILOGRAM',NULL,0)")
        db.execSQL("INSERT INTO ingredients VALUES (1,'Јајца','јајца','JAJCA',6.0,'PIECE',20400,2.0,'домашни',5000)")
        db.execSQL("INSERT INTO ingredients VALUES (2,'Ориз','ориз','ZITARICI_I_TESTENINI',750.5,'GRAM',NULL,NULL,NULL,6000)")
        db.execSQL("INSERT INTO shopping_list_items VALUES (1,'Млеко','млеко',1.0,'LITER','MLECNI_PROIZVODI',0,0,NULL,NULL,7000)")
        db.execSQL("INSERT INTO shopping_list_items VALUES (2,'Леб','леб',2.0,'PIECE','LEB_I_PEKARSKI_PROIZVODI',1,1,1,10,8000)")
        db.execSQL("INSERT INTO weekly_plans VALUES (10,20400,3,1,9000)")
        db.execSQL("INSERT INTO weekly_plans VALUES (11,20407,2,0,9100)")
        db.execSQL("INSERT INTO planned_meals VALUES (1,10,1,1,NULL)")
        db.execSQL("INSERT INTO planned_meals VALUES (2,10,2,2,4)")
        db.execSQL("INSERT INTO planned_meals VALUES (3,11,1,NULL,NULL)")
        db.execSQL("INSERT INTO app_settings VALUES (1,3,1,'WEEKLY',7,'',10,0,20393)")
        db.execSQL("INSERT INTO catalog_ingredients VALUES (5,'Сол','сол','ZACINI','GRAM')")
        return db
    }

    private fun migrate(): SupportSQLiteDatabase {
        createV3().close()
        // Validates the migrated database against the exported version-4 schema (tables, columns, indexes, keys).
        return helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4)
    }

    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.strings(sql: String): List<String> =
        query(sql).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }

    private val userTables = listOf("ingredients", "shopping_list_items", "weekly_plans", "planned_meals", "recipes")

    @Test
    fun noRowIsLost_andNoOldValueChanges() {
        val db = migrate()
        val counts = mapOf("recipes" to 3L, "recipe_ingredients" to 3L, "ingredients" to 2L, "shopping_list_items" to 2L,
            "weekly_plans" to 2L, "planned_meals" to 3L, "app_settings" to 1L, "catalog_ingredients" to 1L)
        for ((table, n) in counts) assertEquals("rows in $table", n, db.scalar("SELECT COUNT(*) FROM $table"))

        assertEquals("Јајца|6.0|20400|2.0|домашни|5000",
            db.strings("SELECT name || '|' || quantity || '|' || expirationDateEpochDay || '|' || lowStockThreshold || '|' || notes || '|' || updatedAt FROM ingredients WHERE id = 1").single())
        assertEquals("Ориз|750.5", db.strings("SELECT name || '|' || quantity FROM ingredients WHERE id = 2").single())
        assertEquals("Тавче гравче|4|[\"чекор 1\",\"чекор 2\"]|77|1|0|1000",
            db.strings("SELECT name || '|' || servings || '|' || instructionSteps || '|' || serverId || '|' || isFavorite || '|' || isUserCreated || '|' || createdAt FROM recipes WHERE id = 1").single())
        assertEquals("Леб|1|1|1|10|8000",
            db.strings("SELECT name || '|' || isPurchased || '|' || isHandled || '|' || sourceRecipeId || '|' || sourceWeeklyPlanId || '|' || createdAt FROM shopping_list_items WHERE id = 2").single())
        assertEquals("20400|3|1|9000", db.strings("SELECT weekStartEpochDay || '|' || householdServings || '|' || shoppingListGenerated || '|' || createdAt FROM weekly_plans WHERE id = 10").single())
        // planned meals keep their plan and recipe links
        assertEquals(listOf("10|1|1", "10|2|2", "11|1|"), db.strings("SELECT weeklyPlanId || '|' || dayOfWeek || '|' || IFNULL(recipeId,'') FROM planned_meals ORDER BY id"))
        assertEquals("4", db.strings("SELECT servingsOverride FROM planned_meals WHERE id = 2").single())
        assertEquals("no broken links between tables", 0, db.query("PRAGMA foreign_key_check").use { it.count })
    }

    @Test
    fun everyRowGetsItsOwnValidUuid_andTheOtherNewColumnsStartAsDesigned() {
        val db = migrate()
        val all = mutableListOf<String>()
        for (table in userTables) {
            val uuids = db.strings("SELECT uuid FROM $table")
            assertTrue("every $table row has a canonical uuid: $uuids", uuids.isNotEmpty() && uuids.all { uuidRegex.matches(it) })
            assertEquals("uuids are unique inside $table", uuids.size, uuids.toSet().size)
            all += uuids
            assertEquals("no row of $table is deleted", 0L, db.scalar("SELECT COUNT(*) FROM $table WHERE deletedAt IS NOT NULL"))
            assertEquals("every row of $table is marked as not yet synced", 0L, db.scalar("SELECT COUNT(*) FROM $table WHERE dirty != 1"))
            assertTrue("$table.updatedAt is filled in", db.scalar("SELECT COUNT(*) FROM $table WHERE updatedAt <= 0") == 0L)
        }
        assertEquals("uuids are unique across all tables", all.size, all.toSet().size)
        for (table in listOf("ingredients", "shopping_list_items", "weekly_plans", "recipes")) {
            assertEquals("existing $table rows start unclaimed", 0L, db.scalar("SELECT COUNT(*) FROM $table WHERE ownerUid != ''"))
        }
    }

    @Test
    fun updatedAt_startsFromTheOldCreationTime() {
        val db = migrate()
        assertEquals(1000L, db.scalar("SELECT updatedAt FROM recipes WHERE id = 1"))
        assertEquals(8000L, db.scalar("SELECT updatedAt FROM shopping_list_items WHERE id = 2"))
        assertEquals(9100L, db.scalar("SELECT updatedAt FROM weekly_plans WHERE id = 11"))
        assertEquals("ingredients keep their own updatedAt", 5000L, db.scalar("SELECT updatedAt FROM ingredients WHERE id = 1"))
    }

    @Test
    fun oldFavouriteFlagsBecomeFavouritesOfTheUnclaimedOwner() {
        val db = migrate()
        val ownUuid = db.strings("SELECT uuid FROM recipes WHERE id = 2").single()
        assertEquals(listOf("|s:77", "|u:$ownUuid"), db.strings("SELECT ownerUid || '|' || recipeRef FROM recipe_favorites ORDER BY recipeRef"))
        assertEquals("the old flags themselves are kept", 2L, db.scalar("SELECT COUNT(*) FROM recipes WHERE isFavorite = 1"))
    }

    @Test
    fun weeklyPlansAreNowUniquePerOwner() {
        val db = migrate()
        assertFalse("the old one-plan-per-week-on-the-phone index is gone",
            db.strings("SELECT name FROM pragma_index_list('weekly_plans')").contains("index_weekly_plans_weekStartEpochDay"))
        // another account may have a plan for the same week...
        db.execSQL("INSERT INTO weekly_plans (weekStartEpochDay, householdServings, shoppingListGenerated, createdAt, ownerUid, uuid, updatedAt, dirty) VALUES (20400, 2, 0, 1, 'someone-else', 'u-1', 1, 1)")
        // ...but the same owner may not have two
        try {
            db.execSQL("INSERT INTO weekly_plans (weekStartEpochDay, householdServings, shoppingListGenerated, createdAt, ownerUid, uuid, updatedAt, dirty) VALUES (20400, 2, 0, 1, '', 'u-2', 1, 1)")
            fail("two plans for the same week and the same owner must be refused")
        } catch (expected: SQLiteConstraintException) {
        }
    }

    @Test
    fun migratedDatabase_opensAndReadsThroughRoom() = runBlocking {
        createV3().close()
        helper.closeWhenFinished(
            Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build().also { room ->
                    val ingredients = room.ingredientDao().getAllOnce("")
                    assertEquals(setOf("Јајца", "Ориз"), ingredients.map { it.name }.toSet())
                    assertTrue(ingredients.all { uuidRegex.matches(it.uuid) && it.ownerUid == "" && it.dirty && it.deletedAt == null })
                    val plans = room.plannerDao().getAllWithMealsOnce("")
                    assertEquals(2, plans.size)
                    assertEquals(3, plans.sumOf { it.meals.size })
                    assertTrue(plans.all { p -> uuidRegex.matches(p.weeklyPlan.uuid) && p.meals.all { uuidRegex.matches(it.uuid) } })
                    assertEquals(3, room.recipeDao().getAllWithIngredientsOnce("").size)
                    assertEquals(2, room.shoppingListDao().getAllOnce("").size)
                }
        )
    }
}
