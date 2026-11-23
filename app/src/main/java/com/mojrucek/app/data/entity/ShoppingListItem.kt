package com.mojrucek.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mojrucek.app.domain.unit.MeasurementUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(tableName = "shopping_list_items")
@Serializable
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val unit: MeasurementUnit,
    val category: IngredientCategory = IngredientCategory.DRUGO,
    val isPurchased: Boolean = false,
    /** Set once the user resolves a purchased item (added to inventory, or deliberately kept out). */
    val isHandled: Boolean = false,
    val sourceRecipeId: Long? = null,
    val sourceWeeklyPlanId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Room v4: ownership and sync groundwork, see SyncColumns.kt
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") @Transient val ownerUid: String = "",
    @ColumnInfo(defaultValue = "''") val uuid: String = newUuid(),
    @Transient val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") @Transient val dirty: Boolean = true
) {
    companion object {
        fun normalize(name: String): String = name.trim().lowercase()
    }
}
