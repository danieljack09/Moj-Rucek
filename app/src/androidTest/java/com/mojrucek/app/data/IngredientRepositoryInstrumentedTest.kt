package com.mojrucek.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.IngredientRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against a real (in-memory) Room database on a device/emulator, since
 * these behaviors depend on the actual persistence layer, not just pure
 * calculation logic. Requires a connected device or emulator — see
 * ./gradlew connectedDebugAndroidTest in the README.
 */
@RunWith(AndroidJUnit4::class)
class IngredientRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: IngredientRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = IngredientRepository(db.ingredientDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun adjustQuantity_neverGoesBelowZero() = runBlocking {
        val id = repository.insert(
            name = "Ориз",
            category = IngredientCategory.ZITARICI_I_TESTENINI,
            quantity = 100.0,
            unit = MeasurementUnit.GRAM,
            expirationDateEpochDay = null,
            lowStockThreshold = null,
            notes = null
        )
        val ingredient = repository.getById(id)!!

        repository.adjustQuantity(ingredient, -500.0)

        val updated = repository.getById(id)!!
        assertEquals(0.0, updated.quantity, 0.0001)
    }

    @Test
    fun findExistingByName_isCaseAndWhitespaceInsensitive() = runBlocking {
        repository.insert(
            name = "Кромид",
            category = IngredientCategory.ZELENCUK,
            quantity = 1.0,
            unit = MeasurementUnit.PIECE,
            expirationDateEpochDay = null,
            lowStockThreshold = null,
            notes = null
        )

        val found = repository.findExistingByName("  кромид  ")
        assertEquals("Кромид", found?.name)
    }
}
