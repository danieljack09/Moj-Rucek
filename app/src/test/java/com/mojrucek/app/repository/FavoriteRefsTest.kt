package com.mojrucek.app.repository

import com.mojrucek.app.data.entity.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FavoriteRefsTest {

    @Test fun aServerRecipe_isNamedByItsServerId_soItIsTheSameOnEveryPhoneAndAccount() {
        assertEquals("s:77", FavoriteRefs.of(serverId = 77, uuid = "ignored"))
        assertEquals("s:77", FavoriteRefs.of(Recipe(name = "x", serverId = 77, uuid = "a")))
        assertEquals("s:77", FavoriteRefs.of(Recipe(name = "y", serverId = 77, uuid = "b")))
    }

    @Test fun anOwnRecipe_isNamedByItsPermanentUuid_notByAnIdThatChangesWithABackupRestore() {
        assertEquals("u:abc-123", FavoriteRefs.of(serverId = null, uuid = "abc-123"))
        assertEquals("u:abc-123", FavoriteRefs.of(Recipe(id = 5, name = "x", uuid = "abc-123")))
        assertEquals("u:abc-123", FavoriteRefs.of(Recipe(id = 99, name = "x", uuid = "abc-123")))
    }

    @Test fun twoDifferentRecipes_neverShareAName() {
        assertNotEquals(FavoriteRefs.of(null, "77"), FavoriteRefs.of(77L, "77"))
        assertNotEquals(FavoriteRefs.of(null, "a"), FavoriteRefs.of(null, "b"))
    }
}
