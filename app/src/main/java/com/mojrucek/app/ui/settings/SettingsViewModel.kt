package com.mojrucek.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.notification.ReminderScheduler
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.CatalogSyncRepository
import com.mojrucek.app.repository.SyncResult
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.repository.SaveOutcome
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.ui.components.backupSaverFor
import com.mojrucek.app.ui.components.savedBackupMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mojrucek.app.domain.text.MkPlural

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val exportedJson: String? = null,
    val importMessage: String? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String? = null
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
    private val isSaving = MutableStateFlow(false)
    private val saveMessage = MutableStateFlow<String?>(null)

    private val baseState = combine(
        settingsRepository.observe(),
        exportedJson,
        importMessage,
        isSyncing,
        syncMessage
    ) { settings, exported, message, syncing, sync ->
        SettingsUiState(settings, exported, message, syncing, sync)
    }

    val uiState: StateFlow<SettingsUiState> = combine(baseState, isSaving, saveMessage) { base, saving, saved ->
        base.copy(isSaving = saving, saveMessage = saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** "Save to device": the same backup JSON as sharing, written to a file the user picked. */
    private val backupSaver = backupSaverFor(application) { backupRepository.exportToJson() }

    /** [uri] is what the system file picker returned; null means the user cancelled (no message). */
    fun saveBackupTo(uri: Uri?) {
        if (uri == null || isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            try {
                saveMessage.value = when (val outcome = backupSaver.save(uri)) {
                    SaveOutcome.Cancelled -> null
                    is SaveOutcome.Saved -> savedBackupMessage(getApplication(), uri)
                    is SaveOutcome.Failed -> "Резервната копија НЕ е зачувана.\n\n${outcome.reason}"
                }
            } finally {
                isSaving.value = false
            }
        }
    }

    fun clearSaveMessage() { saveMessage.value = null }

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
                    "Вчитани се ${MkPlural.recipes(result.summary.recipeCount)}, ${MkPlural.ingredients(result.summary.ingredientCount)}."
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
                is SyncResult.Success -> "Синхронизирано: ${MkPlural.ingredients(result.ingredients)}, ${MkPlural.recipes(result.recipes)} ажурирани."
                is SyncResult.Failure -> "Синхронизацијата не успеа (${result.reason}). Провери интернет врска."
            }
            isSyncing.value = false
        }
    }

    fun clearSyncMessage() { syncMessage.value = null }
}
