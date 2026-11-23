package com.mojrucek.app.repository

import android.content.Context
import androidx.room.withTransaction
import com.mojrucek.app.data.AppDatabase
import com.mojrucek.app.data.entity.CatalogIngredient
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeCategory
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.data.entity.newUuid
import com.mojrucek.app.domain.unit.MeasurementUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

sealed interface SyncResult {
    data class Success(val ingredients: Int, val recipes: Int) : SyncResult
    data class Failure(val reason: String) : SyncResult
}

/**
 * Pulls the catalog (ingredient list + recipes) from the admin server into Room.
 * The server is the source of truth: whatever it holds is what the app shows.
 * Incremental via `since`; deletions on the server delete here too.
 */
class CatalogSyncRepository(context: Context, private val database: AppDatabase) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val catalogDao = database.catalogDao()
    private val recipeDao = database.recipeDao()

    /** The synced ingredient list; empty until the first successful sync. */
    fun observeIngredients(): kotlinx.coroutines.flow.Flow<List<CatalogIngredient>> = catalogDao.observeIngredients()

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val since = prefs.getLong(KEY_SINCE, 0L)
            val body = download("$CATALOG_URL?since=$since")
            val catalog = json.decodeFromString<CatalogResponse>(body)
            apply(catalog)
            prefs.edit().putLong(KEY_SINCE, catalog.serverTime).apply()
            SyncResult.Success(catalog.ingredients.size, catalog.recipes.size)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun apply(catalog: CatalogResponse) = database.withTransaction {
        for (i in catalog.ingredients) {
            if (i.deleted) {
                catalogDao.deleteIngredient(i.id)
            } else {
                catalogDao.upsertIngredient(
                    CatalogIngredient(
                        serverId = i.id,
                        name = i.name,
                        normalizedName = i.name.trim().lowercase(),
                        category = IngredientCategory.fromStorageName(i.category),
                        defaultUnit = MeasurementUnit.fromStorageName(i.defaultUnit)
                    )
                )
            }
        }

        for (r in catalog.recipes) {
            val existing = recipeDao.findByServerId(r.id)
            if (r.deleted) {
                existing?.let { recipeDao.deleteRecipe(it) }
                continue
            }
            val recipe = Recipe(
                id = existing?.id ?: 0,
                name = r.name,
                description = r.description,
                servings = r.servings,
                prepTimeMinutes = r.prepTimeMinutes,
                cookTimeMinutes = r.cookTimeMinutes,
                instructionSteps = r.instructionSteps,
                category = RecipeCategory.fromStorageName(r.category),
                tags = r.tags,
                notes = r.notes,
                imageUri = existing?.imageUri,
                isFavorite = existing?.isFavorite ?: false,
                isUserCreated = false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                serverId = r.id,
                uuid = existing?.uuid ?: newUuid()
            )
            val recipeId = if (existing == null) recipeDao.insertRecipe(recipe) else {
                recipeDao.updateRecipe(recipe)
                existing.id
            }
            recipeDao.replaceIngredients(
                recipeId,
                r.ingredients.mapIndexed { index, line ->
                    RecipeIngredient(
                        recipeId = recipeId,
                        name = line.name,
                        normalizedName = RecipeIngredient.normalize(line.name),
                        quantity = line.quantity,
                        unit = MeasurementUnit.fromStorageName(line.unit),
                        notes = line.notes,
                        sortOrder = index
                    )
                }
            )
        }
    }

    @Serializable
    private data class CatalogResponse(
        val serverTime: Long,
        val ingredients: List<ServerIngredient> = emptyList(),
        val recipes: List<ServerRecipe> = emptyList()
    )

    @Serializable
    private data class ServerIngredient(
        val id: Long,
        val name: String = "",
        val category: String = "",
        val defaultUnit: String = "",
        val deleted: Boolean = false
    )

    @Serializable
    private data class ServerRecipe(
        val id: Long,
        val name: String = "",
        val description: String? = null,
        val servings: Int = 3,
        val prepTimeMinutes: Int = 0,
        val cookTimeMinutes: Int = 0,
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val instructionSteps: List<String> = emptyList(),
        val notes: String? = null,
        val ingredients: List<ServerLine> = emptyList(),
        val deleted: Boolean = false
    )

    @Serializable
    private data class ServerLine(
        val name: String,
        val quantity: Double,
        val unit: String,
        val notes: String? = null
    )

    private companion object {
        const val CATALOG_URL = "https://rucek.display.mk/api/catalog"
        const val PREFS = "catalog_sync"
        const val KEY_SINCE = "since"    }
}
