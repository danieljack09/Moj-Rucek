package com.mojrucek.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotFingerprintTest {

    private fun snap(
        exportedAt: Long = 1,
        ingredients: String = """[{"id":1,"name":"Јајца","quantity":6.0},{"id":2,"name":"Ориз","quantity":500.0}]""",
        recipes: String = """[{"id":1,"name":"Мусака","instructionSteps":["а","б","в"],"tags":["x","y"]}]"""
    ) = """{"formatVersion":1,"exportedAtEpochMillis":$exportedAt,"ingredients":$ingredients,"recipes":$recipes,"shoppingListItems":[]}"""

    @Test fun sameContent_sameFingerprint() {
        assertEquals(SnapshotFingerprint.of(snap()), SnapshotFingerprint.of(snap()))
    }

    @Test fun theTimeOfTheExport_doesNotMatter() {
        assertEquals(SnapshotFingerprint.of(snap(exportedAt = 1)), SnapshotFingerprint.of(snap(exportedAt = 999_999)))
    }

    @Test fun theOrderOfTheTopLevelLists_doesNotMatter() {
        val a = snap(ingredients = """[{"id":1,"name":"Јајца","quantity":6.0},{"id":2,"name":"Ориз","quantity":500.0}]""")
        val b = snap(ingredients = """[{"id":2,"name":"Ориз","quantity":500.0},{"id":1,"name":"Јајца","quantity":6.0}]""")
        assertEquals(SnapshotFingerprint.of(a), SnapshotFingerprint.of(b))
    }

    @Test fun theOrderOfFieldsInsideAnItem_doesNotMatter() {
        val a = snap(ingredients = """[{"id":1,"name":"Јајца","quantity":6.0}]""")
        val b = snap(ingredients = """[{"quantity":6.0,"name":"Јајца","id":1}]""")
        assertEquals(SnapshotFingerprint.of(a), SnapshotFingerprint.of(b))
    }

    @Test fun anyRealChange_changesIt() {
        val base = SnapshotFingerprint.of(snap())
        assertNotEquals(base, SnapshotFingerprint.of(snap(ingredients = """[{"id":1,"name":"Јајца","quantity":5.0},{"id":2,"name":"Ориз","quantity":500.0}]""")))
        assertNotEquals(base, SnapshotFingerprint.of(snap(ingredients = """[{"id":1,"name":"Јајца","quantity":6.0}]""")))
        assertNotEquals(base, SnapshotFingerprint.of(snap(ingredients = """[]""")))
        assertNotEquals(base, SnapshotFingerprint.of(snap(recipes = """[{"id":1,"name":"Мусака 2","instructionSteps":["а","б","в"],"tags":["x","y"]}]""")))
    }

    @Test fun theOrderOfStepsInsideARecipe_isARealChange() {
        val a = SnapshotFingerprint.of(snap(recipes = """[{"id":1,"name":"Мусака","instructionSteps":["а","б","в"],"tags":["x","y"]}]"""))
        val b = SnapshotFingerprint.of(snap(recipes = """[{"id":1,"name":"Мусака","instructionSteps":["б","а","в"],"tags":["x","y"]}]"""))
        assertNotEquals("moving a step must be noticed", a, b)
        val c = SnapshotFingerprint.of(snap(recipes = """[{"id":1,"name":"Мусака","instructionSteps":["а","б","в"],"tags":["y","x"]}]"""))
        assertNotEquals(a, c)
    }

    @Test fun itLooksLikeAHash_andIsStableAcrossCalls() {
        val f = SnapshotFingerprint.of(snap())
        assertEquals(64, f.length)
        assertTrue(f.all { it in "0123456789abcdef" })
    }
}
