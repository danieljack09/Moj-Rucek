package com.mojrucek.app.repository

import com.mojrucek.app.data.BackupData
import com.mojrucek.app.data.entity.PlannedMeal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Android-free half of backup import: reading a backup file, deciding whether it is safe to restore,
 * and the id fix-up the restore needs. Kept apart from the database code so it can be unit tested.
 */
object BackupFormat {

    /** One configuration for writing and reading backups, so the two can never drift apart. */
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    const val NOT_A_BACKUP = "Датотеката не е валидна резервна копија на Мој Ручек."
    const val NEWER_VERSION = "Датотеката е зачувана со понова верзија на апликацијата."
    const val CORRUPT = "Датотеката е оштетена или е изменета и не може безбедно да се вчита. Ништо не е променето."

    sealed interface Parsed {
        data class Ok(val data: BackupData) : Parsed
        /** [reason] is a user-facing Macedonian sentence. */
        data class Invalid(val reason: String) : Parsed
    }

    /** Parses [text] and checks that restoring it cannot fail halfway. Changes nothing. */
    fun parse(text: String): Parsed {
        val data = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: SerializationException) {
            return Parsed.Invalid(NOT_A_BACKUP)
        } catch (e: IllegalArgumentException) {
            return Parsed.Invalid(NOT_A_BACKUP)
        }
        if (data.formatVersion > BackupData.CURRENT_FORMAT_VERSION) return Parsed.Invalid(NEWER_VERSION)
        if (data.formatVersion < 1) return Parsed.Invalid(NOT_A_BACKUP)
        if (consistencyProblem(data) != null) return Parsed.Invalid(CORRUPT)
        return Parsed.Ok(data)
    }

    /**
     * What would make a restore fail midway (unique indexes and foreign keys in the planner tables).
     * Returns a short English description for logs/tests, or null when the data is consistent.
     */
    fun consistencyProblem(data: BackupData): String? {
        val planIds = data.weeklyPlans.map { it.id }
        if (planIds.size != planIds.toSet().size) return "duplicate weekly plan ids"
        val weeks = data.weeklyPlans.map { it.weekStartEpochDay }
        if (weeks.size != weeks.toSet().size) return "two weekly plans for the same week"
        val knownPlans = planIds.toSet()
        if (data.plannedMeals.any { it.weeklyPlanId !in knownPlans }) return "a planned meal belongs to a plan that is not in the file"
        val days = data.plannedMeals.map { it.weeklyPlanId to it.dayOfWeek }
        if (days.size != days.toSet().size) return "two planned meals on the same day of the same plan"
        return null
    }

    /**
     * Recipes are restored with NEW ids, so a planned meal's recipe link has to follow. A meal whose
     * recipe is not in the backup keeps its day but loses the recipe (instead of pointing at a
     * missing, or worse a different, recipe).
     */
    fun remapMealRecipes(meals: List<PlannedMeal>, newIdByOldId: Map<Long, Long>): List<PlannedMeal> =
        meals.map { meal -> meal.copy(recipeId = meal.recipeId?.let { newIdByOldId[it] }) }
}
