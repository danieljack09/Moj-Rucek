package com.mojrucek.app.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.WeeklyPlan

data class WeeklyPlanWithMeals(
    @Embedded
    val weeklyPlan: WeeklyPlan,
    @Relation(
        parentColumn = "id",
        entityColumn = "weeklyPlanId"
    )
    val meals: List<PlannedMeal>
)
