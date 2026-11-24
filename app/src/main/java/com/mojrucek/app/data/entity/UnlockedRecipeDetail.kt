package com.mojrucek.app.data.entity

import androidx.room.Entity

/**
 * The full details of ONE server recipe that the server has unlocked for ONE account (Room v5).
 * Everything the public catalog keeps back (description, steps, notes, tags) lives only here, never in the
 * recipes table, so it can be removed the moment the unlock runs out.
 *
 * [unlockedUntil] is a SERVER time (epoch ms); null means "no deadline" (Paid). [serverId] says which recipe.
 */
@Entity(tableName = "unlocked_recipe_details", primaryKeys = ["ownerUid", "serverId"])
data class UnlockedRecipeDetail(
    val ownerUid: String,
    val serverId: Long,
    val description: String?,
    val instructionSteps: List<String>,
    val notes: String?,
    val tags: List<String>,
    val unlockedUntil: Long?,
    val savedAt: Long
)
