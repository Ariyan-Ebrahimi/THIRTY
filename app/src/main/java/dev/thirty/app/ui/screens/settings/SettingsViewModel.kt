package dev.thirty.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.thirty.app.data.datastore.AppSettings
import dev.thirty.app.data.local.ChallengeEntity
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.notifications.NotificationHelper
import dev.thirty.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: ThirtyRepository) : ViewModel() {

    val plansFlow: Flow<List<ChallengeEntity>> = repo.activeListFlow

    val settingsFlow: Flow<AppSettings> = repo.settingsFlow

    fun setNudgeEnabled(appContext: Context, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repo.setNudgeEnabled(enabled)
                val s = repo.currentSettings()
                repo.activeListWithProgress().forEach { p ->
                    if (enabled && p.challenge.reminderEnabled) {
                        ReminderScheduler.scheduleNudge(
                            appContext, p.challenge.id, s.nudgeHour, s.nudgeMinute
                        )
                    } else {
                        ReminderScheduler.cancelNudge(appContext, p.challenge.id)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun setNudgeTime(appContext: Context, h: Int, m: Int) {
        viewModelScope.launch {
            try {
                repo.setNudgeTime(h, m)
                repo.activeListWithProgress().forEach { p ->
                    if (p.challenge.reminderEnabled) {
                        ReminderScheduler.scheduleNudge(appContext, p.challenge.id, h, m)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun setPlanEnabled(appContext: Context, id: Long, enabled: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.setChallengeReminderEnabled(id, enabled)
                val c = repo.challengeWithProgress(id)?.challenge
                if (enabled && c != null) {
                    ReminderScheduler.scheduleDaily(appContext, id, c.reminderHour, c.reminderMinute)
                } else {
                    ReminderScheduler.cancel(appContext, id)
                }
            } catch (_: Exception) { }
            onDone()
        }
    }

    fun setPlanTime(appContext: Context, id: Long, h: Int, m: Int) {
        viewModelScope.launch {
            try {
                repo.setChallengeReminderTime(id, h, m)
                ReminderScheduler.scheduleDaily(appContext, id, h, m)
            } catch (_: Exception) { }
        }
    }

    fun sendTestNotification(appContext: Context, onSent: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val plans = repo.activeListWithProgress()
                val first = plans.firstOrNull()
                if (first == null) {
                    NotificationHelper.showReminder(
                        appContext, 9999, 1, "Start your first plan"
                    )
                } else {
                    NotificationHelper.showReminder(
                        appContext,
                        ReminderScheduler.notifIdFor(first.challenge.id),
                        first.todayDayNumber,
                        first.challenge.title
                    )
                }
                onSent("Test notification sent — check your status bar.")
            } catch (e: Exception) {
                onSent("Couldn\u2019t send test notification.")
            }
        }
    }

    fun setTheme(mode: dev.thirty.app.data.datastore.ThemeMode) {
        viewModelScope.launch {
            try { repo.setTheme(mode) } catch (_: Exception) { }
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try { repo.setLockEnabled(enabled) } catch (_: Exception) { }
        }
    }

    fun setLockPassword(password: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try { repo.setLockPassword(password) } catch (_: Exception) { }
            onSaved()
        }
    }

    fun removeLock() {
        viewModelScope.launch {
            try { repo.clearLock() } catch (_: Exception) { }
        }
    }

    fun clearAll(appContext: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.activeListWithProgress().forEach {
                    runCatching { ReminderScheduler.cancel(appContext, it.challenge.id) }
                }
                repo.clearAll()
            } catch (_: Exception) { }
            onDone()
        }
    }
}

class SettingsViewModelFactory(private val repo: ThirtyRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repo) as T
}
