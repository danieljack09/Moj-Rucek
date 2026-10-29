package com.mojrucek.app.data

import androidx.room.TypeConverter
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Converters {

    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromMeasurementUnit(unit: MeasurementUnit): String = unit.name

    @TypeConverter
    fun toMeasurementUnit(value: String): MeasurementUnit = MeasurementUnit.fromStorageName(value)

    @TypeConverter
    fun fromIngredientCategory(category: IngredientCategory): String = category.name

    @TypeConverter
    fun toIngredientCategory(value: String): IngredientCategory = IngredientCategory.fromStorageName(value)

    @TypeConverter
    fun fromRecipeCategory(category: RecipeCategory?): String? = category?.name

    @TypeConverter
    fun toRecipeCategory(value: String?): RecipeCategory? = RecipeCategory.fromStorageName(value)

    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(stringListSerializer, list)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString(stringListSerializer, value)
}
