package com.mojrucek.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
