package com.mojrucek.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.notification.ReminderScheduler
import com.mojrucek.app.repository.BackupRepository
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
    val importMessage: String? = null
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository
) : AndroidViewModel(application) {

    private val exportedJson = MutableStateFlow<String?>(null)
    private val importMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observe(),
        exportedJson,
        importMessage
    ) { settings, exported, message ->
        SettingsUiState(settings, exported, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setDefaultServings(servings: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(defaultHouseholdServings = servings.coerceAtLeast(1)) }
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(sundayReminderEnabled = enabled) }
            val updated = settingsRepository.getOnce()
            ReminderScheduler.scheduleWeeklyReminder(getApplication<Application>(), updated)
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(sundayReminderHour = hour, sundayReminderMinute = minute) }
            val updated = settingsRepository.getOnce()
            ReminderScheduler.scheduleWeeklyReminder(getApplication<Application>(), updated)
        }
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
}
