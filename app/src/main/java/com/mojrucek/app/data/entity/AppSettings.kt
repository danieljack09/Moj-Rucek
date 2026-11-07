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
    val reminderEnabled: Boolean = true,
    /** [ReminderMode.WEEKLY] or [ReminderMode.DAILY]. */
    val reminderMode: String = ReminderMode.WEEKLY,
    /** java.time.DayOfWeek value (1 = MONDAY .. 7 = SUNDAY), used only in WEEKLY mode. */
    val reminderWeeklyDay: Int = 7,
    /** java.time.DayOfWeek values, used only in DAILY mode. */
    val reminderDays: Set<Int> = emptySet(),
    val reminderHour: Int = 10,
    val reminderMinute: Int = 0,
    val lastShoppingListWeekStartEpochDay: Long? = null
) {
    object ReminderMode {
        const val WEEKLY = "WEEKLY"
        const val DAILY = "DAILY"
    }

    companion object {
        const val SINGLETON_ID = 0
    }
}
