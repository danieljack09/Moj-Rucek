package com.mojrucek.app.domain.model

import com.mojrucek.app.domain.unit.MeasurementUnit

/** A framework-free "I need/have this much of this ingredient" line, used across use cases. */
data class IngredientLine(
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val unit: MeasurementUnit
)
