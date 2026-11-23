package com.mojrucek.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A Monday-to-Sunday lunch plan. [weekStartEpochDay] is the epoch day
 * (java.time.LocalDate.toEpochDay()) of that week's Monday, in the
 * Europe/Skopje calendar. It is unique PER OWNER (Room v4: two accounts on one
 * phone can each have a plan for the same week) — generating or opening "next
 * week's" plan always resolves to a single row instead of creating duplicates.
 */
@Entity(
    tableName = "weekly_plans",
    indices = [Index(value = ["ownerUid", "weekStartEpochDay"], unique = true)]
)
@Serializable
data class WeeklyPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekStartEpochDay: Long,
    val householdServings: Int = 3,
    val shoppingListGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // Room v4: ownership and sync groundwork, see SyncColumns.kt
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") @Transient val ownerUid: String = "",
    @ColumnInfo(defaultValue = "''") val uuid: String = newUuid(),
    @Transient val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") @Transient val dirty: Boolean = true
)
