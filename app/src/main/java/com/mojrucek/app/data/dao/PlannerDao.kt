package com.mojrucek.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.WeeklyPlan
import com.mojrucek.app.data.relation.WeeklyPlanWithMeals
import kotlinx.coroutines.flow.Flow

/**
 * Weekly plans belong to an owner; the planned meals belong to their plan, so the meal queries that
 * take a plan id need no owner of their own (the plan id comes from an owner-scoped read).
 */
@Dao
interface PlannerDao {

    @Query("SELECT * FROM weekly_plans WHERE weekStartEpochDay = :weekStartEpochDay AND ownerUid = :ownerUid LIMIT 1")
    suspend fun findByWeekStart(weekStartEpochDay: Long, ownerUid: String): WeeklyPlan?

    @Transaction
    @Query("SELECT * FROM weekly_plans WHERE weekStartEpochDay = :weekStartEpochDay AND ownerUid = :ownerUid LIMIT 1")
    fun observeWithMealsByWeekStart(weekStartEpochDay: Long, ownerUid: String): Flow<WeeklyPlanWithMeals?>

    @Transaction
    @Query("SELECT * FROM weekly_plans WHERE weekStartEpochDay = :weekStartEpochDay AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getWithMealsByWeekStart(weekStartEpochDay: Long, ownerUid: String): WeeklyPlanWithMeals?

    @Transaction
    @Query("SELECT * FROM weekly_plans WHERE id = :id AND ownerUid = :ownerUid")
    suspend fun getWithMealsById(id: Long, ownerUid: String): WeeklyPlanWithMeals?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: WeeklyPlan): Long

    @Update
    suspend fun updatePlan(plan: WeeklyPlan)

    @Query("UPDATE weekly_plans SET shoppingListGenerated = :generated WHERE id = :id")
    suspend fun setShoppingListGenerated(id: Long, generated: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeal(meal: PlannedMeal): Long

    @Query("DELETE FROM planned_meals WHERE weeklyPlanId = :weeklyPlanId AND dayOfWeek = :dayOfWeek")
    suspend fun clearDay(weeklyPlanId: Long, dayOfWeek: Int)

    @Query("SELECT * FROM planned_meals WHERE weeklyPlanId = :weeklyPlanId ORDER BY dayOfWeek ASC")
    suspend fun getMealsForPlan(weeklyPlanId: Long): List<PlannedMeal>

    @Query("DELETE FROM weekly_plans WHERE ownerUid = :ownerUid")
    suspend fun deleteAllPlans(ownerUid: String)

    @Transaction
    @Query("SELECT * FROM weekly_plans WHERE ownerUid = :ownerUid")
    suspend fun getAllWithMealsOnce(ownerUid: String): List<WeeklyPlanWithMeals>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlans(plans: List<WeeklyPlan>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeals(meals: List<PlannedMeal>)
}
