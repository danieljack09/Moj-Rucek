package com.mojrucek.app.repository

import com.mojrucek.app.data.BackupData
import com.mojrucek.app.data.entity.PlannedMeal
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.WeeklyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private fun encode(data: BackupData) = BackupFormat.json.encodeToString(BackupData.serializer(), data)
    private fun backup(
        plans: List<WeeklyPlan> = emptyList(),
        meals: List<PlannedMeal> = emptyList(),
        recipes: List<Recipe> = emptyList(),
        version: Int = BackupData.CURRENT_FORMAT_VERSION
    ) = BackupData(formatVersion = version, exportedAtEpochMillis = 1L, recipes = recipes, weeklyPlans = plans, plannedMeals = meals)

    private fun invalidReason(text: String): String =
        (BackupFormat.parse(text) as? BackupFormat.Parsed.Invalid)?.reason ?: error("expected the file to be rejected")

    // ---------- reading a file ----------

    @Test fun aValidBackup_isAccepted_andReadBackIntact() {
        val original = backup(
            recipes = listOf(Recipe(id = 7, name = "Тавче гравче")),
            plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 20000)),
            meals = listOf(PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 1, recipeId = 7))
        )
        val parsed = BackupFormat.parse(encode(original)) as BackupFormat.Parsed.Ok
        assertEquals(original, parsed.data)
    }

    @Test fun anEmptyPlannerBackup_isFine() {
        assertTrue(BackupFormat.parse(encode(backup())) is BackupFormat.Parsed.Ok)
    }

    @Test fun notJsonOrNotABackup_isRejectedWithTheStandardMessage() {
        val garbage = listOf(
            "", "   ", "hello", "{", "[]", "null", "42", "{}", "[1,2,3]",
            "{\"exportedAtEpochMillis\": \"yesterday\"}",             // wrong type
            "{\"exportedAtEpochMillis\": 1, \"recipes\": \"none\"}",   // wrong type
            "{\"exportedAtEpochMillis\": 1, \"ingredients\": [{\"x\":1}]}"  // incomplete entries
        )
        for (text in garbage) assertEquals("input: $text", BackupFormat.NOT_A_BACKUP, invalidReason(text))
    }

    @Test fun aTruncatedFile_isRejected_atEveryCutPoint() {
        val full = encode(backup(plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 1))))
        for (cut in listOf(1, full.length / 4, full.length / 2, full.length - 2)) {
            assertEquals("cut at $cut", BackupFormat.NOT_A_BACKUP, invalidReason(full.substring(0, cut)))
        }
    }

    @Test fun aFileFromANewerAppVersion_isRejectedWithItsOwnMessage() {
        val newer = encode(backup(version = BackupData.CURRENT_FORMAT_VERSION + 1))
        assertEquals(BackupFormat.NEWER_VERSION, invalidReason(newer))
    }

    @Test fun anImpossibleVersion_isRejected() {
        assertEquals(BackupFormat.NOT_A_BACKUP, invalidReason(encode(backup(version = 0))))
        assertEquals(BackupFormat.NOT_A_BACKUP, invalidReason(encode(backup(version = -3))))
    }

    @Test fun unknownExtraFields_areIgnored_soSmallAdditionsDoNotBreakOldFiles() {
        val text = encode(backup()).replaceFirst("{", "{\"someFutureField\": {\"a\": 1},")
        assertTrue(BackupFormat.parse(text) is BackupFormat.Parsed.Ok)
    }

    // ---------- would a restore fail halfway? ----------

    private fun problem(
        plans: List<WeeklyPlan> = emptyList(),
        meals: List<PlannedMeal> = emptyList()
    ) = BackupFormat.consistencyProblem(backup(plans = plans, meals = meals))

    @Test fun consistentPlanner_hasNoProblem() {
        assertNull(problem(
            plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 100), WeeklyPlan(id = 2, weekStartEpochDay = 107)),
            meals = listOf(
                PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 1, recipeId = 5),
                PlannedMeal(id = 2, weeklyPlanId = 1, dayOfWeek = 2, recipeId = null),
                PlannedMeal(id = 3, weeklyPlanId = 2, dayOfWeek = 1, recipeId = 5)   // same day number, other week: fine
            )
        ))
    }

    @Test fun twoPlansForTheSameWeek_isCorrupt() {
        val plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 100), WeeklyPlan(id = 2, weekStartEpochDay = 100))
        assertTrue(problem(plans = plans) != null)
        assertEquals(BackupFormat.CORRUPT, invalidReason(encode(backup(plans = plans))))
    }

    @Test fun duplicatePlanIds_isCorrupt() {
        assertTrue(problem(plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 1), WeeklyPlan(id = 1, weekStartEpochDay = 2))) != null)
    }

    @Test fun aMealOfAPlanThatIsNotInTheFile_isCorrupt() {
        val plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 100))
        val meals = listOf(PlannedMeal(id = 1, weeklyPlanId = 99, dayOfWeek = 1))
        assertTrue(problem(plans = plans, meals = meals) != null)
        assertTrue(problem(meals = meals) != null)   // meals with no plans at all
    }

    @Test fun twoMealsOnTheSameDayOfOnePlan_isCorrupt() {
        val plans = listOf(WeeklyPlan(id = 1, weekStartEpochDay = 100))
        val meals = listOf(PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 3), PlannedMeal(id = 2, weeklyPlanId = 1, dayOfWeek = 3))
        assertTrue(problem(plans = plans, meals = meals) != null)
    }

    // ---------- keeping planned meals attached to the right recipes ----------

    @Test fun restoredRecipesGetNewIds_andPlannedMealsFollowThem() {
        val meals = listOf(
            PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 1, recipeId = 5, servingsOverride = 4),
            PlannedMeal(id = 2, weeklyPlanId = 1, dayOfWeek = 2, recipeId = 9)
        )
        val remapped = BackupFormat.remapMealRecipes(meals, mapOf(5L to 105L, 9L to 109L))
        assertEquals(listOf(105L, 109L), remapped.map { it.recipeId })
        assertEquals(4, remapped[0].servingsOverride)             // everything else is kept
        assertEquals(listOf(1L, 2L), remapped.map { it.id })
        assertEquals(listOf(1, 2), remapped.map { it.dayOfWeek })
    }

    @Test fun aMealWhoseRecipeIsNotInTheBackup_keepsItsDayButLosesTheRecipe() {
        val meals = listOf(PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 1, recipeId = 42))
        val remapped = BackupFormat.remapMealRecipes(meals, mapOf(5L to 105L))
        assertNull(remapped.single().recipeId)
        assertEquals(1, remapped.single().dayOfWeek)
    }

    @Test fun aMealWithNoRecipe_staysWithoutOne() {
        val meals = listOf(PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 1, recipeId = null))
        assertNull(BackupFormat.remapMealRecipes(meals, mapOf(1L to 2L)).single().recipeId)
    }

    @Test fun restoredPlansAndRecipesGetNewIds_andEveryMealFollowsBoth_withAFreshIdOfItsOwn() {
        val meals = listOf(
            PlannedMeal(id = 7, weeklyPlanId = 1, dayOfWeek = 1, recipeId = 5, servingsOverride = 3),
            PlannedMeal(id = 8, weeklyPlanId = 2, dayOfWeek = 1, recipeId = 9)
        )
        val remapped = BackupFormat.remapMeals(meals, newPlanIdByOldId = mapOf(1L to 101L, 2L to 102L), newRecipeIdByOldId = mapOf(5L to 205L, 9L to 209L))
        assertEquals(listOf(101L, 102L), remapped.map { it.weeklyPlanId })
        assertEquals(listOf(205L, 209L), remapped.map { it.recipeId })
        assertEquals("fresh ids so they cannot collide with another account's meals", listOf(0L, 0L), remapped.map { it.id })
        assertEquals(3, remapped[0].servingsOverride)
    }

    @Test fun aMealWhosePlanWasNotRestored_isDropped_andOneWhoseRecipeWasNotRestored_keepsItsDay() {
        val meals = listOf(
            PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 2, recipeId = 5),
            PlannedMeal(id = 2, weeklyPlanId = 99, dayOfWeek = 3, recipeId = 5)
        )
        val remapped = BackupFormat.remapMeals(meals, mapOf(1L to 101L), mapOf(5L to 205L)).single()
        assertEquals(101L, remapped.weeklyPlanId)
        val noRecipe = BackupFormat.remapMeals(meals.take(1), mapOf(1L to 101L), emptyMap()).single()
        assertNull(noRecipe.recipeId)
        assertEquals(2, noRecipe.dayOfWeek)
    }

    @Test fun anOldIdThatCollidesWithANewId_isNotConfusedWithIt() {
        // old 1 -> new 2, old 2 -> new 1: a naive in-place rewrite would swap them twice.
        val meals = listOf(
            PlannedMeal(id = 1, weeklyPlanId = 1, dayOfWeek = 1, recipeId = 1),
            PlannedMeal(id = 2, weeklyPlanId = 1, dayOfWeek = 2, recipeId = 2)
        )
        val remapped = BackupFormat.remapMealRecipes(meals, mapOf(1L to 2L, 2L to 1L))
        assertEquals(listOf(2L, 1L), remapped.map { it.recipeId })
    }
}
