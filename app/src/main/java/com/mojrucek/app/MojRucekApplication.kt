package com.mojrucek.app

import android.app.Application
import com.mojrucek.app.data.seed.SampleRecipes
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.notification.ExpiryReminderScheduler
import com.mojrucek.app.notification.NotificationHelper
import com.mojrucek.app.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MojRucekApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        ExpiryReminderScheduler.scheduleDailyCheck(this)

        applicationScope.launch {
            container.settingsRepository.ensureInitialized()
            container.recipeRepository.seedIfEmpty()
            addNewBuiltInRecipesOnce()
            val settings = container.settingsRepository.getOnce()
            if (settings.reminderEnabled) {
                ReminderScheduler.scheduleReminder(this@MojRucekApplication, settings)
            }
        }
    }

    /** Existing installs get recipes added in later versions exactly once, so deleted ones stay deleted. */
    private suspend fun addNewBuiltInRecipesOnce() {
        val prefs = getSharedPreferences("seed", MODE_PRIVATE)
        if (prefs.getInt(KEY_RECIPE_SEED_VERSION, 1) >= RECIPE_SEED_VERSION) return
        container.recipeRepository.seedMissing(SampleRecipes.mealTimeRecipes)
        prefs.edit().putInt(KEY_RECIPE_SEED_VERSION, RECIPE_SEED_VERSION).apply()
    }

    private companion object {
        const val KEY_RECIPE_SEED_VERSION = "recipe_seed_version"
        const val RECIPE_SEED_VERSION = 2
    }
}
