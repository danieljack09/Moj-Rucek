package com.mojrucek.app

import android.app.Application
import com.mojrucek.app.di.AppContainer
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

        applicationScope.launch {
            container.settingsRepository.ensureInitialized()
            container.recipeRepository.seedIfEmpty()
            val settings = container.settingsRepository.getOnce()
            if (settings.sundayReminderEnabled) {
                ReminderScheduler.scheduleWeeklyReminder(this@MojRucekApplication, settings)
            }
        }
    }
}
