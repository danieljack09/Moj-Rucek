package com.mojrucek.app.domain.limits

import com.mojrucek.app.domain.limits.RecipeAccessRules.StoredDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeAccessRulesTest {

    private val details = RecipeDetails("опис", listOf("чекор 1", "чекор 2"), "белешка", listOf("брзо"))
    private val now = 1_800_000_000_000L

    @Test fun anOwnRecipe_isAlwaysFullyVisible_whateverIsStored() {
        assertEquals(RecipeAccess.Own, RecipeAccessRules.access(true, null, now))
        assertEquals(RecipeAccess.Own, RecipeAccessRules.access(true, StoredDetails(details, now - 1), now))
    }

    @Test fun aServerRecipeWithNothingStored_isLocked() {
        assertEquals(RecipeAccess.Locked, RecipeAccessRules.access(false, null, now))
    }

    @Test fun aServerRecipe_isUnlocked_untilTheDeadline() {
        val r = RecipeAccessRules.access(false, StoredDetails(details, now + 1), now)
        assertTrue(r is RecipeAccess.Unlocked)
        assertEquals(details, (r as RecipeAccess.Unlocked).details)
        assertEquals(now + 1, r.unlockedUntil)
    }

    @Test fun atTheDeadline_andAfter_itIsLocked_evenIfNotCleanedOutYet() {
        assertEquals(RecipeAccess.Locked, RecipeAccessRules.access(false, StoredDetails(details, now), now))
        assertEquals(RecipeAccess.Locked, RecipeAccessRules.access(false, StoredDetails(details, now - 1), now))
    }

    @Test fun noDeadline_meansPaid_andStaysUnlocked() {
        for (t in listOf(0L, now, Long.MAX_VALUE)) {
            assertTrue(RecipeAccessRules.access(false, StoredDetails(details, null), t) is RecipeAccess.Unlocked)
        }
    }
}
