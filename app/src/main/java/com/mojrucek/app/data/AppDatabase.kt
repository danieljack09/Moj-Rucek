package com.mojrucek.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mojrucek.app.data.dao.AppSettingsDao
import com.mojrucek.app.data.dao.CatalogDao
import com.mojrucek.app.data.entity.CatalogIngredient
import com.mojrucek.app.data.dao.FavoriteDao
import com.mojrucek.app.data.dao.IngredientDao
import com.mojrucek.app.data.dao.OwnershipDao
import com.mojrucek.app.data.dao.PlannerDao
import com.mojrucek.app.data.dao.RecipeDao
import com.mojrucek.app.data.dao.ShoppingListDao
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeFavorite
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.data.entity.WeeklyPlan
import java.io.File

@Database(
    entities = [
        Ingredient::class,
        Recipe::class,
        RecipeIngredient::class,
        ShoppingListItem::class,
        PlannedMeal::class,
        WeeklyPlan::class,
        AppSettings::class,
        CatalogIngredient::class,
        RecipeFavorite::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun plannerDao(): PlannerDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun catalogDao(): CatalogDao
    abstract fun ownershipDao(): OwnershipDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private const val DB_NAME = "mojrucek.db"

        /** Must match `version` in the @Database annotation above. */
        const val SCHEMA_VERSION = 4

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    // Before an upgrade, keep a copy of the old database. A failure here must not stop the app.
                    try {
                        DatabaseUpgradeBackup.copyAsideIfNeeded(
                            dbFile = app.getDatabasePath(DB_NAME),
                            backupDir = File(app.filesDir, "db-backups"),
                            targetVersion = SCHEMA_VERSION
                        )
                    } catch (e: Exception) {
                        // Continue without the extra copy.
                    }
                    Room.databaseBuilder(app, AppDatabase::class.java, DB_NAME)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .build()
                        .also { instance = it }
                }
            }

        /**
         * Replaces the old fixed Sunday-only reminder columns with the new
         * weekly/daily reminder settings, carrying over the user's existing
         * enabled flag and time so their preference survives the upgrade.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite can't add a NOT NULL column without a SQL-level DEFAULT, but Room's
                // schema validator requires the final table to have none (defaults live only
                // in the Kotlin constructor) — so the table is rebuilt instead of ALTER'd.
                db.execSQL(
                    "CREATE TABLE app_settings_new (" +
                        "id INTEGER NOT NULL, " +
                        "defaultHouseholdServings INTEGER NOT NULL, " +
                        "reminderEnabled INTEGER NOT NULL, " +
                        "reminderMode TEXT NOT NULL, " +
                        "reminderWeeklyDay INTEGER NOT NULL, " +
                        "reminderDays TEXT NOT NULL, " +
                        "reminderHour INTEGER NOT NULL, " +
                        "reminderMinute INTEGER NOT NULL, " +
                        "lastShoppingListWeekStartEpochDay INTEGER, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "INSERT INTO app_settings_new (id, defaultHouseholdServings, reminderEnabled, " +
                        "reminderMode, reminderWeeklyDay, reminderDays, reminderHour, reminderMinute, " +
                        "lastShoppingListWeekStartEpochDay) " +
                        "SELECT id, defaultHouseholdServings, sundayReminderEnabled, 'WEEKLY', 7, '', " +
                        "sundayReminderHour, sundayReminderMinute, lastShoppingListWeekStartEpochDay " +
                        "FROM app_settings"
                )
                db.execSQL("DROP TABLE app_settings")
                db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            }
        }

        /** Adds the synced ingredient catalog and links recipes to their server id. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catalog_ingredients (" +
                        "serverId INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "normalizedName TEXT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "defaultUnit TEXT NOT NULL, " +
                        "PRIMARY KEY(serverId))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catalog_ingredients_normalizedName " +
                        "ON catalog_ingredients (normalizedName)"
                )
                db.execSQL("ALTER TABLE recipes ADD COLUMN serverId INTEGER")
            }
        }

        /**
         * Account ownership and sync groundwork (docs/room-v4-ownership-and-sync-design.md): adds the
         * ownerUid / uuid / updatedAt / deletedAt / dirty columns, gives every existing row its own
         * uuid, makes the weekly-plan uniqueness per owner, and creates + fills recipe_favorites.
         * Nothing is deleted or rewritten: every existing row keeps all of its old values. Existing
         * data is left unclaimed (ownerUid = ""); a later step lets the signed-in account claim it.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            // A random version-4 UUID in the canonical 8-4-4-4-12 form, like java.util.UUID.randomUUID().
            private val uuidSql = "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || " +
                "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"
            private val nowMillisSql = "CAST(strftime('%s', 'now') AS INTEGER) * 1000"

            override fun migrate(db: SupportSQLiteDatabase) {
                // table -> (has its own owner column, has no updatedAt yet and with what value to start)
                addSyncColumns(db, "ingredients", owned = true, updatedAtFrom = null)           // already has updatedAt
                addSyncColumns(db, "shopping_list_items", owned = true, updatedAtFrom = "createdAt")
                addSyncColumns(db, "weekly_plans", owned = true, updatedAtFrom = "createdAt")
                addSyncColumns(db, "planned_meals", owned = false, updatedAtFrom = nowMillisSql)
                addSyncColumns(db, "recipes", owned = true, updatedAtFrom = "createdAt")

                // One plan per week PER OWNER (was: one per week on the whole phone).
                db.execSQL("DROP INDEX IF EXISTS index_weekly_plans_weekStartEpochDay")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_weekly_plans_ownerUid_weekStartEpochDay " +
                        "ON weekly_plans (ownerUid, weekStartEpochDay)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipe_favorites (" +
                        "ownerUid TEXT NOT NULL, " +
                        "recipeRef TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "deletedAt INTEGER, " +
                        "dirty INTEGER NOT NULL DEFAULT 1, " +
                        "PRIMARY KEY(ownerUid, recipeRef))"
                )
                // The old per-recipe favourite flags become favourites of the (not yet known) owner.
                db.execSQL(
                    "INSERT INTO recipe_favorites (ownerUid, recipeRef, updatedAt, deletedAt, dirty) " +
                        "SELECT '', CASE WHEN serverId IS NOT NULL THEN 's:' || serverId ELSE 'u:' || uuid END, " +
                        "updatedAt, NULL, 1 FROM recipes WHERE isFavorite = 1"
                )
            }

            private fun addSyncColumns(db: SupportSQLiteDatabase, table: String, owned: Boolean, updatedAtFrom: String?) {
                if (owned) db.execSQL("ALTER TABLE $table ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $table ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE $table ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                if (updatedAtFrom != null) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE $table SET updatedAt = $updatedAtFrom")
                }
                db.execSQL("UPDATE $table SET uuid = $uuidSql")   // evaluated separately for every row
            }
        }
    }
}
