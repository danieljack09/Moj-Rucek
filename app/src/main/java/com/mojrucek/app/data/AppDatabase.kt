package com.mojrucek.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mojrucek.app.data.dao.AppSettingsDao
import com.mojrucek.app.data.dao.IngredientDao
import com.mojrucek.app.data.dao.PlannerDao
import com.mojrucek.app.data.dao.RecipeDao
import com.mojrucek.app.data.dao.ShoppingListDao
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.data.entity.WeeklyPlan

@Database(
    entities = [
        Ingredient::class,
        Recipe::class,
        RecipeIngredient::class,
        ShoppingListItem::class,
        PlannedMeal::class,
        WeeklyPlan::class,
        AppSettings::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun plannerDao(): PlannerDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mojrucek.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
    }
}
