package com.mojrucek.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One day's slot within a [WeeklyPlan]. [dayOfWeek] follows
 * java.time.DayOfWeek numbering (1 = Monday ... 7 = Sunday). A null
 * [recipeId] means the day is intentionally left empty.
 */
@Entity(
    tableName = "planned_meals",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlan::class,
            parentColumns = ["id"],
            childColumns = ["weeklyPlanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["weeklyPlanId", "dayOfWeek"], unique = true),
        Index("recipeId")
    ]
)
@Serializable
data class PlannedMeal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weeklyPlanId: Long,
    val dayOfWeek: Int,
    val recipeId: Long? = null,
    /** Overrides the plan's household servings for this one day, if set. */
    val servingsOverride: Int? = null
)
