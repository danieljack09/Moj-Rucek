package com.mojrucek.app.repository

import com.mojrucek.app.data.dao.AppSettingsDao
import com.mojrucek.app.data.entity.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {

    /** Always emits a settings row — defaults are used until the user changes anything. */
    fun observe(): Flow<AppSettings> = dao.observe().map { it ?: AppSettings() }

    suspend fun getOnce(): AppSettings = dao.getOnce() ?: AppSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = getOnce()
        dao.upsert(transform(current))
    }

    suspend fun ensureInitialized() {
        if (dao.getOnce() == null) dao.upsert(AppSettings())
    }
}
