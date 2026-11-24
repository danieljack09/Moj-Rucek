package com.mojrucek.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.domain.limits.RecipeAccess
import com.mojrucek.app.repository.CatalogSyncRepository
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.LocalDataEraser
import com.mojrucek.app.repository.OwnerKeys
import com.mojrucek.app.repository.UnlockedDetailsRepository
import com.mojrucek.app.repository.UnlockedPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnlockedDetailsInstrumentedTest {

    private lateinit var db: AppDatabase
    private val ownerKey = MutableStateFlow("uid-A")
    private var serverNow = 1_000_000L
    private lateinit var repo: UnlockedDetailsRepository

    @Before fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        repo = UnlockedDetailsRepository(db.unlockedDetailsDao(), CurrentOwner.following(ownerKey), { serverNow }, { 42L })
    }

    @After fun tearDown() { db.close() }

    private fun payload(id: Long) = UnlockedPayload(id, "опис $id", listOf("чекор $id"), "белешка", listOf("т"))
    private fun count(owner: String) = db.query("SELECT COUNT(*) FROM unlocked_recipe_details WHERE ownerUid = '$owner'", null).use { it.moveToFirst(); it.getInt(0) }

    @Test fun savedDetails_areShownUntilTheDeadline_thenLocked() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(7)), unlockedUntil = serverNow + 1000)

        val open = repo.accessFor(isOwn = false, serverId = 7) as RecipeAccess.Unlocked
        assertEquals(listOf("чекор 7"), open.details.instructionSteps)
        assertEquals("опис 7", open.details.description)

        serverNow += 1000                      // exactly the deadline
        assertEquals(RecipeAccess.Locked, repo.accessFor(false, 7))
    }

    @Test fun aNewAnswer_replacesEverythingUnlockedBefore() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1), payload(2)), serverNow + 5000)
        repo.replaceUnlocked("uid-A", listOf(payload(3)), serverNow + 9000)

        assertEquals(RecipeAccess.Locked, repo.accessFor(false, 1))
        assertEquals(RecipeAccess.Locked, repo.accessFor(false, 2))
        assertTrue(repo.accessFor(false, 3) is RecipeAccess.Unlocked)
        assertEquals(1, count("uid-A"))
    }

    @Test fun anEmptyAnswer_locksEverything() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), serverNow + 5000)
        repo.replaceUnlocked("uid-A", emptyList(), null)
        assertEquals(0, count("uid-A"))
    }

    @Test fun noDeadline_meansPaid_stayingUnlocked() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), null)
        serverNow += 365L * 24 * 3600 * 1000
        assertTrue(repo.accessFor(false, 1) is RecipeAccess.Unlocked)
    }

    @Test fun accountsNeverSeeEachOthersDetails() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), serverNow + 5000)
        repo.replaceUnlocked("uid-B", listOf(payload(1), payload(2)), serverNow + 5000)

        ownerKey.value = "uid-A"
        assertTrue(repo.accessFor(false, 1) is RecipeAccess.Unlocked)
        assertEquals(RecipeAccess.Locked, repo.accessFor(false, 2))
        ownerKey.value = "uid-B"
        assertTrue(repo.accessFor(false, 2) is RecipeAccess.Unlocked)
        ownerKey.value = OwnerKeys.SIGNED_OUT
        assertEquals(RecipeAccess.Locked, repo.accessFor(false, 1))
        ownerKey.value = OwnerKeys.UNOWNED
        assertEquals(RecipeAccess.Locked, repo.accessFor(false, 1))
    }

    @Test fun replacingForOneAccount_leavesTheOtherAlone() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), serverNow + 5000)
        repo.replaceUnlocked("uid-B", listOf(payload(2)), serverNow + 5000)
        repo.replaceUnlocked("uid-A", emptyList(), null)
        assertEquals(0, count("uid-A"))
        assertEquals(1, count("uid-B"))
    }

    @Test fun unlocksAreNotSavedForTheUnownedOrSignedOutBuckets() = runBlocking {
        repo.replaceUnlocked(OwnerKeys.UNOWNED, listOf(payload(1)), null)
        repo.replaceUnlocked(OwnerKeys.SIGNED_OUT, listOf(payload(1)), null)
        assertEquals(0, count(OwnerKeys.UNOWNED) + count(OwnerKeys.SIGNED_OUT))
    }

    @Test fun anOwnRecipe_isNeverLocked_andNeedsNoServerId() = runBlocking {
        assertEquals(RecipeAccess.Own, repo.accessFor(true, null))
        assertEquals(RecipeAccess.Locked, repo.accessFor(false, null))
    }

    @Test fun pruning_removesOnlyWhatRanOut_ofTheCurrentAccount() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), serverNow + 100)
        repo.replaceUnlocked("uid-B", listOf(payload(2)), serverNow + 100)
        serverNow += 100
        ownerKey.value = "uid-A"
        repo.pruneExpired()
        assertEquals(0, count("uid-A"))
        assertEquals("another account's rows are left for its own pruning", 1, count("uid-B"))
    }

    @Test fun pruning_keepsEntriesWithoutADeadline() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), null)
        serverNow += Long.MAX_VALUE / 4
        repo.pruneExpired()
        assertEquals(1, count("uid-A"))
    }

    @Test fun observing_listsOnlyWhatIsStillUnlocked_forTheCurrentAccount() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1), payload(2)), serverNow + 100)
        repo.replaceUnlocked("uid-B", listOf(payload(3)), serverNow + 100)
        assertEquals(setOf(1L, 2L), repo.observeUnlocked().first().keys)
        serverNow += 100
        assertTrue(repo.observeUnlocked().first().isEmpty())
    }

    @Test fun erasingAnAccount_alsoRemovesItsUnlockedDetails_andOnlyThose() = runBlocking {
        repo.replaceUnlocked("uid-A", listOf(payload(1)), null)
        repo.replaceUnlocked("uid-B", listOf(payload(1)), null)
        LocalDataEraser(db).eraseAccount("uid-A")
        assertEquals(0, count("uid-A"))
        assertEquals(1, count("uid-B"))
    }

    @Test fun theCatalogSync_neverKeepsLockedDetails_evenIfTheServerSendsThem() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sync = CatalogSyncRepository(context, db)
        sync.applyForTest(
            """{"serverTime":1,"ingredients":[],"recipes":[{"id":9,"name":"Од сервер","description":"таен опис","servings":3,
               "prepTimeMinutes":5,"cookTimeMinutes":20,"category":"PASTA","tags":["брзо"],"instructionSteps":["сецкај","вари"],
               "notes":"таен","ingredients":[{"name":"Паста","quantity":300.0,"unit":"GRAM"}],"deleted":false}]}"""
        )
        val r = db.recipeDao().findByServerId(9)!!
        assertEquals("Од сервер", r.name)
        assertEquals(20, r.cookTimeMinutes)
        assertNull(r.description)
        assertTrue(r.instructionSteps.isEmpty())
        assertNull(r.notes)
        assertTrue(r.tags.isEmpty())
        val lines = db.query("SELECT COUNT(*) FROM recipe_ingredients WHERE recipeId = ${r.id}", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, lines)
    }
}
