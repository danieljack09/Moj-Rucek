package com.mojrucek.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a realistic version-4 database to version 5: the new table must exist and be empty, the details of SERVER
 * recipes must be gone, and everything else (own recipes, their details, all other tables) must be exactly as before.
 * Uses a throw-away database file, never the app's real data.
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5InstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val dbName = "migration-4-5-test"

    @After fun deleteTestDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    private fun recipe(db: SupportSQLiteDatabase, id: Int, name: String, serverId: Int?, own: Boolean) {
        db.execSQL(
            "INSERT INTO recipes (id, name, description, servings, prepTimeMinutes, cookTimeMinutes, instructionSteps, category, tags, notes, " +
                "imageUri, isFavorite, isUserCreated, createdAt, serverId, updatedAt, ownerUid, uuid, deletedAt, dirty) VALUES " +
                "($id, '$name', 'опис', 4, 20, 90, '[\"чекор 1\",\"чекор 2\"]', 'GRAV_I_LEKJA', '[\"брзо\"]', 'белешка', NULL, 0, " +
                "${if (own) 1 else 0}, 1000, ${serverId ?: "NULL"}, 1000, '${if (own) "uid-A" else ""}', 'uuid-$id', NULL, 1)"
        )
    }

    private fun createV4(): SupportSQLiteDatabase {
        val db = helper.createDatabase(dbName, 4)
        recipe(db, 1, "Од сервер 1", 77, own = false)
        recipe(db, 2, "Од сервер 2", 78, own = false)
        recipe(db, 3, "Мојата мусака", null, own = true)
        db.execSQL("INSERT INTO recipe_ingredients VALUES (1,1,'грав','грав',500.0,'GRAM',NULL,0)")
        db.execSQL("INSERT INTO recipe_ingredients VALUES (2,3,'компир','компир',1.0,'KILOGRAM',NULL,0)")
        db.execSQL("INSERT INTO recipe_favorites VALUES ('uid-A','s:77',5000,NULL,1)")
        db.execSQL("INSERT INTO catalog_ingredients VALUES (5,'Сол','сол','ZACINI','GRAM')")
        return db
    }

    private fun migrate(): SupportSQLiteDatabase {
        createV4().close()
        return helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5)
    }

    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.text(sql: String): String? = query(sql).use { it.moveToFirst(); if (it.isNull(0)) null else it.getString(0) }

    @Test fun theNewTableExists_isEmpty_andMatchesTheSchema() {
        val db = migrate()
        assertEquals(0, db.scalar("SELECT COUNT(*) FROM unlocked_recipe_details"))
        db.close()
    }

    @Test fun theDetailsOfServerRecipes_areRemoved_butTheirNamesTimesAndIngredientsStay() {
        val db = migrate()
        for (id in listOf(1, 2)) {
            assertNull(db.text("SELECT description FROM recipes WHERE id = $id"))
            assertEquals("[]", db.text("SELECT instructionSteps FROM recipes WHERE id = $id"))
            assertNull(db.text("SELECT notes FROM recipes WHERE id = $id"))
            assertEquals("[]", db.text("SELECT tags FROM recipes WHERE id = $id"))
        }
        assertEquals("Од сервер 1", db.text("SELECT name FROM recipes WHERE id = 1"))
        assertEquals(20, db.scalar("SELECT prepTimeMinutes FROM recipes WHERE id = 1"))
        assertEquals(90, db.scalar("SELECT cookTimeMinutes FROM recipes WHERE id = 1"))
        assertEquals("GRAV_I_LEKJA", db.text("SELECT category FROM recipes WHERE id = 1"))
        assertEquals(77, db.scalar("SELECT serverId FROM recipes WHERE id = 1"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM recipe_ingredients WHERE recipeId = 1"))
        db.close()
    }

    @Test fun theUsersOwnRecipe_isCompletelyUntouched() {
        val db = migrate()
        assertEquals("опис", db.text("SELECT description FROM recipes WHERE id = 3"))
        assertEquals("[\"чекор 1\",\"чекор 2\"]", db.text("SELECT instructionSteps FROM recipes WHERE id = 3"))
        assertEquals("белешка", db.text("SELECT notes FROM recipes WHERE id = 3"))
        assertEquals("[\"брзо\"]", db.text("SELECT tags FROM recipes WHERE id = 3"))
        assertEquals("uid-A", db.text("SELECT ownerUid FROM recipes WHERE id = 3"))
        db.close()
    }

    @Test fun everythingElse_isUnchanged() {
        val db = migrate()
        assertEquals(3, db.scalar("SELECT COUNT(*) FROM recipes"))
        assertEquals(2, db.scalar("SELECT COUNT(*) FROM recipe_ingredients"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM recipe_favorites"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM catalog_ingredients"))
        db.close()
    }

    @Test fun anEmptyV4Database_upgradesWithoutTrouble() {
        helper.createDatabase(dbName, 4).close()
        val db = helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5)
        assertEquals(0, db.scalar("SELECT COUNT(*) FROM recipes"))
        db.close()
    }
}
