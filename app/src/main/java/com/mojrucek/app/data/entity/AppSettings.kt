package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Single-row settings table; always stored and read at [id] = 0. */
@Entity(tableName = "app_settings")
@Serializable
data class AppSettings(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val defaultHouseholdServings: Int = 3,
    val sundayReminderEnabled: Boolean = true,
    val sundayReminderHour: Int = 10,
    val sundayReminderMinute: Int = 0,
    val lastShoppingListWeekStartEpochDay: Long? = null
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
