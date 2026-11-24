package com.mojrucek.app.domain.limits

/** The details of a recipe that the server keeps back until a list is made. */
data class RecipeDetails(
    val description: String?,
    val instructionSteps: List<String>,
    val notes: String?,
    val tags: List<String>
)

/** What the recipe screen may show for the locked fields of one recipe. */
sealed interface RecipeAccess {
    /** The user's own recipe: always fully visible, from the recipe itself. */
    data object Own : RecipeAccess

    /** A server recipe with unlocked details. */
    data class Unlocked(val details: RecipeDetails, val unlockedUntil: Long?) : RecipeAccess

    /** A server recipe whose details are not unlocked (never, or the 72 hours ran out): show name, time, ingredients only. */
    data object Locked : RecipeAccess
}

/** Pure rule: may the details of a recipe be shown at server time [serverNowMs]? */
object RecipeAccessRules {
    /**
     * @param isOwn true for a recipe the user created
     * @param stored the unlocked details saved for this account and recipe, if any
     * An entry without a deadline (Paid) stays unlocked; one whose deadline has been reached is locked, even if it
     * has not been cleaned out yet.
     */
    fun access(isOwn: Boolean, stored: StoredDetails?, serverNowMs: Long): RecipeAccess {
        if (isOwn) return RecipeAccess.Own
        if (stored == null) return RecipeAccess.Locked
        val until = stored.unlockedUntil
        if (until != null && serverNowMs >= until) return RecipeAccess.Locked
        return RecipeAccess.Unlocked(stored.details, until)
    }

    data class StoredDetails(val details: RecipeDetails, val unlockedUntil: Long?)
}
