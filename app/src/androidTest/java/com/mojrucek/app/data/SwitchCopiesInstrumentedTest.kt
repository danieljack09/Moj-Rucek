package com.mojrucek.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.Ingredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.AccountFileTag
import com.mojrucek.app.repository.AccountFiles
import com.mojrucek.app.repository.AccountSwitchRepository
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.InMemorySwitchCopyIndex
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository
import com.mojrucek.app.repository.SnapshotResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The guest's copy made when switching into an existing account must be deleted together with the accounts it belongs to. */
@RunWith(AndroidJUnit4::class)
class SwitchCopiesInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var repo: AccountSwitchRepository
    private val index = InMemorySwitchCopyIndex()
    private val guest = "guest-uid-1"

    @Before fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        filesDir = File(target.cacheDir, "switch-copies-files-${System.nanoTime()}").apply { mkdirs() }
        cacheDir = File(target.cacheDir, "switch-copies-cache-${System.nanoTime()}").apply { mkdirs() }
        val context: Context = object : ContextWrapper(target) { override fun getFilesDir(): File = this@SwitchCopiesInstrumentedTest.filesDir }
        db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        val owner = CurrentOwner.following(MutableStateFlow(guest))
        val recipes = RecipeRepository(db.recipeDao(), db.favoriteDao(), owner)
        val ingredients = IngredientRepository(db.ingredientDao(), owner)
        val shopping = ShoppingListRepository(db.shoppingListDao(), ingredients, owner)
        val backup = BackupRepository(ingredients, recipes, shopping, db.plannerDao(), SettingsRepository(db.appSettingsDao()), db, owner)
        repo = AccountSwitchRepository(context, db, backup, owner, index)
        runBlocking {
            db.ingredientDao().insert(Ingredient(name = "Јајца", normalizedName = "јајца", category = IngredientCategory.JAJCA, quantity = 6.0, unit = MeasurementUnit.PIECE, ownerUid = guest))
        }
    }

    @After fun tearDown() { db.close(); filesDir.deleteRecursively(); cacheDir.deleteRecursively() }

    private fun makeCopy(): File = (runBlocking { repo.createVerifiedSnapshot() } as SnapshotResult.Ok).snapshot.file
    private fun files() = AccountFiles(filesDir, cacheDir, index)

    @Test fun theCopyIsNamedAfterTheGuestThatMadeIt() {
        val copy = makeCopy()
        assertTrue(copy.name, copy.name.startsWith("mojrucek-pred-promena-${AccountFileTag.of(guest)}-"))
        assertFalse("the guest's id is not in the name", copy.name.contains(guest))
    }

    @Test fun deletingTheGuestAccount_deletesItsCopy() {
        val copy = makeCopy()
        files().deleteFor(guest)
        assertFalse(copy.exists())
    }

    @Test fun afterASuccessfulSwitch_deletingTheAccountTheyJoined_deletesTheCopy() {
        val snapshot = (runBlocking { repo.createVerifiedSnapshot() } as SnapshotResult.Ok).snapshot
        repo.recordDestination(snapshot, "google-uid-2")
        files().deleteFor("google-uid-2")
        assertFalse(snapshot.file.exists())
    }

    @Test fun aSwitchThatDidNotHappen_recordsNothing_andAnotherAccountsDeletionLeavesTheCopy() {
        val copy = makeCopy()
        repo.recordDestination((runBlocking { repo.createVerifiedSnapshot() } as SnapshotResult.Ok).snapshot, null)
        files().deleteFor("some-other-account")
        assertTrue(copy.exists())
    }
}
