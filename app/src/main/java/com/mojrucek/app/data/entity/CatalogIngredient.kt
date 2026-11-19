package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mojrucek.app.domain.unit.MeasurementUnit

/**
 * One entry of the ingredient catalog synced from the admin server. This is the
 * list the add-ingredient autocomplete suggests from (name + category + default
 * unit); it is separate from [Ingredient], which is what the user has at home.
 */
@Entity(tableName = "catalog_ingredients", indices = [Index("normalizedName")])
data class CatalogIngredient(
    @PrimaryKey val serverId: Long,
    val name: String,
    val normalizedName: String,
    val category: IngredientCategory,
    val defaultUnit: MeasurementUnit
)
