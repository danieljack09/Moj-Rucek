package com.mojrucek.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.notification.ReminderScheduler
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.CatalogSyncRepository
import com.mojrucek.app.repository.SyncResult
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val exportedJson: String? = null,
    val importMessage: String? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val catalogSyncRepository: CatalogSyncRepository
) : AndroidViewModel(application) {

    private val exportedJson = MutableStateFlow<String?>(null)
    private val importMessage = MutableStateFlow<String?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observe(),
        exportedJson,
        importMessage,
        isSyncing,
        syncMessage
    ) { settings, exported, message, syncing, sync ->
        SettingsUiState(settings, exported, message, syncing, sync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setDefaultServings(servings: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(defaultHouseholdServings = servings.coerceAtLeast(1)) }
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(reminderEnabled = enabled) }
            rescheduleReminder()
        }
    }

    fun setReminderMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(reminderMode = mode) }
            rescheduleReminder()
        }
    }

    fun setReminderWeeklyDay(day: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(reminderWeeklyDay = day) }
            rescheduleReminder()
        }
    }

    fun setReminderDays(days: Set<Int>) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(reminderDays = days) }
            rescheduleReminder()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(reminderHour = hour, reminderMinute = minute) }
            rescheduleReminder()
        }
    }

    private suspend fun rescheduleReminder() {
        val updated = settingsRepository.getOnce()
        ReminderScheduler.scheduleReminder(getApplication<Application>(), updated)
    }

    fun validateImport(jsonText: String): ImportResult = backupRepository.validate(jsonText)

    fun exportData() {
        viewModelScope.launch { exportedJson.value = backupRepository.exportToJson() }
    }

    fun clearExport() { exportedJson.value = null }

    fun importData(jsonText: String) {
        viewModelScope.launch {
            when (val result = backupRepository.import(jsonText)) {
                is ImportResult.Success -> importMessage.value =
                    "Вчитани се ${result.summary.recipeCount} рецепти, ${result.summary.ingredientCount} намирници."
                is ImportResult.InvalidFile -> importMessage.value = result.reason
            }
        }
    }

    fun clearImportMessage() { importMessage.value = null }

    fun syncCatalog() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            syncMessage.value = when (val result = catalogSyncRepository.sync()) {
                is SyncResult.Success -> "Синхронизирано: ${result.ingredients} намирници, ${result.recipes} рецепти ажурирани."
                is SyncResult.Failure -> "Синхронизацијата не успеа (${result.reason}). Провери интернет врска."
            }
            isSyncing.value = false
        }
    }

    fun clearSyncMessage() { syncMessage.value = null }
}
