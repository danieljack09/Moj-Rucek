package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A Monday-to-Sunday lunch plan. [weekStartEpochDay] is the epoch day
 * (java.time.LocalDate.toEpochDay()) of that week's Monday, in the
 * Europe/Skopje calendar, and is unique — generating or opening "next
 * week's" plan always resolves to a single row instead of creating
 * duplicates.
 */
@Entity(
    tableName = "weekly_plans",
    indices = [Index(value = ["weekStartEpochDay"], unique = true)]
)
@Serializable
data class WeeklyPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekStartEpochDay: Long,
    val householdServings: Int = 3,
    val shoppingListGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
