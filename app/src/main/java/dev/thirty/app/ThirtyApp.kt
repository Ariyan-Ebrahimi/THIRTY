package dev.thirty.app

import android.app.Application
import dev.thirty.app.data.datastore.SettingsStore
import dev.thirty.app.data.local.ThirtyDatabase
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.notifications.NotificationHelper
import dev.thirty.app.notifications.ReminderScheduler
import dev.thirty.app.notifications.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ThirtyApp : Application() {
    val database: ThirtyDatabase by lazy { ThirtyDatabase.get(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val repository: ThirtyRepository by lazy { ThirtyRepository(database, settingsStore) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        ReminderWorker.enqueue(this)
        scope.launch {
            try {
                repository.refreshDayStates()
            } catch (_: Exception) { }
            try {
                ReminderScheduler.rescheduleFromStore(this@ThirtyApp)
            } catch (_: Exception) { }
        }
    }
}
