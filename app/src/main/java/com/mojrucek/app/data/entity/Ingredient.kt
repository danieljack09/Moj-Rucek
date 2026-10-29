package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mojrucek.app.domain.unit.MeasurementUnit
import kotlinx.serialization.Serializable

/**
 * One item in the home ingredient inventory (Намирници).
 *
 * [normalizedName] is [name] trimmed and lower-cased, kept in sync by the
 * repository on every write, and used to detect duplicates and to match
 * recipe ingredients by name.
 */
@Entity(tableName = "ingredients")
@Serializable
data class Ingredient(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val category: IngredientCategory,
    val quantity: Double,
    val unit: MeasurementUnit,
    val expirationDateEpochDay: Long? = null,
    val lowStockThreshold: Double? = null,
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = lowStockThreshold != null && quantity <= lowStockThreshold

    companion object {
        fun normalize(name: String): String = name.trim().lowercase()
    }
}
