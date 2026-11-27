package com.mojrucek.app.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.repository.CatalogSyncRepository
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.decodesAsPicture
import com.mojrucek.app.session.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecipePicturesInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sync: CatalogSyncRepository
    private val tempFiles = mutableListOf<File>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sync = CatalogSyncRepository(context, db)
    }

    @After fun tearDown() { db.close(); tempFiles.forEach { it.delete() } }

    private fun recipeJson(id: Int, imageUrl: String?) = """{"serverTime":1,"ingredients":[],"recipes":[{"id":$id,"name":"Од сервер","servings":3,
        "prepTimeMinutes":5,"cookTimeMinutes":20,"category":"PASTA","ingredients":[{"name":"Паста","quantity":300.0,"unit":"GRAM"}],
        "imageUrl":${imageUrl?.let { "\"$it\"" } ?: "null"},"deleted":false}]}"""

    // ---------- the catalog brings the picture address ----------

    @Test fun aServerRecipe_getsItsPictureAddress_fromTheCatalog() = runBlocking {
        sync.applyForTest(recipeJson(9, "/img/9-0123456789abcdef.jpg"))
        assertEquals(ServerConfig.BASE_URL + "/img/9-0123456789abcdef.jpg", db.recipeDao().findByServerId(9)!!.imageUri)
    }

    @Test fun aChangedPicture_replacesTheAddress_andARemovedOneEmptiesIt() = runBlocking {
        sync.applyForTest(recipeJson(9, "/img/9-0123456789abcdef.jpg"))
        sync.applyForTest(recipeJson(9, "/img/9-fedcba9876543210.png"))
        assertEquals(ServerConfig.BASE_URL + "/img/9-fedcba9876543210.png", db.recipeDao().findByServerId(9)!!.imageUri)
        sync.applyForTest(recipeJson(9, null))
        assertNull(db.recipeDao().findByServerId(9)!!.imageUri)
        assertEquals("the recipe itself is the same row", 1, db.recipeDao().getAllWithIngredientsOnce("").count { it.recipe.serverId == 9L })
    }

    @Test fun aServerRecipeWithoutAPicture_hasNone() = runBlocking {
        sync.applyForTest(recipeJson(9, null))
        assertNull(db.recipeDao().findByServerId(9)!!.imageUri)
    }

    @Test fun theUsersOwnRecipePicture_isNeverTouchedByTheCatalog() = runBlocking {
        val owner = CurrentOwner.following(MutableStateFlow("uid-A"))
        val repo = RecipeRepository(db.recipeDao(), db.favoriteDao(), owner)
        val own = repo.upsert(Recipe(name = "Мој", imageUri = "content://photos/1"), emptyList())
        sync.applyForTest(recipeJson(9, "/img/9-0123456789abcdef.jpg"))
        assertEquals("content://photos/1", db.recipeDao().getById(own, "uid-A")!!.imageUri)
    }

    // ---------- is it really a picture? ----------

    private fun file(bytes: ByteArray): File = File.createTempFile("pic", ".bin", context.cacheDir).also { it.writeBytes(bytes); tempFiles += it }

    private fun encoded(format: Bitmap.CompressFormat): ByteArray {
        val bmp = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        return java.io.ByteArrayOutputStream().also { bmp.compress(format, 90, it) }.toByteArray()
    }

    @Test fun realPictures_areRecognised() {
        assertTrue(decodesAsPicture(file(encoded(Bitmap.CompressFormat.JPEG))))
        assertTrue(decodesAsPicture(file(encoded(Bitmap.CompressFormat.PNG))))
    }

    @Test fun notPictures_areRefused() {
        assertFalse(decodesAsPicture(file(ByteArray(0))))
        assertFalse(decodesAsPicture(file("just some text".toByteArray())))
        assertFalse(decodesAsPicture(file("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray())))
        assertFalse(decodesAsPicture(file(ByteArray(4000) { 7 })))
        assertFalse(decodesAsPicture(File(context.cacheDir, "does-not-exist-${System.nanoTime()}")))
    }
}
