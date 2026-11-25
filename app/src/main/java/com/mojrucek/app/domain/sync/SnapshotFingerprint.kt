package com.mojrucek.app.domain.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * A short fingerprint of a snapshot's CONTENT: it ignores when the snapshot was made and the ORDER OF THE TOP-LEVEL LISTS
 * (pantry, recipes, plans, ... may come out of the database in any order), so two exports of unchanged data always give
 * the same fingerprint and any real change gives a different one. Lists INSIDE an item keep their order (the steps of a
 * recipe are a sequence: moving a step is a real change). This is how the phone knows whether it changed since the last
 * sync, without every data function having to report changes.
 */
object SnapshotFingerprint {
    private val json = Json { ignoreUnknownKeys = true }
    private const val IGNORED_KEY = "exportedAtEpochMillis"

    fun of(snapshotJson: String): String {
        val root = json.parseToJsonElement(snapshotJson)
        val normalized = if (root is JsonObject) {
            JsonObject(
                root.entries
                    .filter { it.key != IGNORED_KEY }
                    .sortedBy { it.key }
                    .associate { (key, value) -> key to if (value is JsonArray) sortedList(value) else keepOrder(value) }
            )
        } else keepOrder(root)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** A top-level list: its items in a fixed order, each item normalised. */
    private fun sortedList(list: JsonArray): JsonArray = JsonArray(list.map { keepOrder(it) }.sortedBy { it.toString() })

    /** Everything below the top level: object keys sorted (so field order cannot matter), list order kept. */
    private fun keepOrder(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associate { it.key to keepOrder(it.value) })
        is JsonArray -> JsonArray(e.map { keepOrder(it) })
        is JsonPrimitive -> e
    }
}
