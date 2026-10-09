package dev.thirty.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "thirty_prefs")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val nudgeEnabled: Boolean = true,
    val nudgeHour: Int = 21,
    val nudgeMinute: Int = 30,
    val onboardingSeen: Boolean = false,
    /** Optional app lock: off by default, so anyone can use THIRTY without one. */
    val lockEnabled: Boolean = false,
    /** SHA-256 of the lock password (never the password itself). */
    val lockHash: String = ""
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val NOTIFS = booleanPreferencesKey("notifications_enabled")
        val HOUR = intPreferencesKey("reminder_hour")
        val MINUTE = intPreferencesKey("reminder_minute")
        val NUDGE = booleanPreferencesKey("evening_nudge_enabled")
        val NUDGE_HOUR = intPreferencesKey("evening_nudge_hour")
        val NUDGE_MINUTE = intPreferencesKey("evening_nudge_minute")
        val ONBOARDING = booleanPreferencesKey("onboarding_seen")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val LOCK_HASH = stringPreferencesKey("lock_hash")
        val SELECTED_PLAN = longPreferencesKey("selected_plan_id")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            notificationsEnabled = p[Keys.NOTIFS] ?: true,
            reminderHour = p[Keys.HOUR] ?: 20,
            reminderMinute = p[Keys.MINUTE] ?: 0,
            nudgeEnabled = p[Keys.NUDGE] ?: true,
            nudgeHour = p[Keys.NUDGE_HOUR] ?: 21,
            nudgeMinute = p[Keys.NUDGE_MINUTE] ?: 30,
            onboardingSeen = p[Keys.ONBOARDING] ?: false,
            lockEnabled = p[Keys.LOCK_ENABLED] ?: false,
            lockHash = p[Keys.LOCK_HASH] ?: ""
        )
    }

    suspend fun current(): AppSettings = flow.first()

    /** Plan the user last picked — remembered across screens and restarts. */
    suspend fun selectedPlanId(): Long? = context.dataStore.data.first()[Keys.SELECTED_PLAN]

    suspend fun setSelectedPlanId(id: Long) {
        context.dataStore.edit { it[Keys.SELECTED_PLAN] = id }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFS] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        context.dataStore.edit {
            it[Keys.HOUR] = hour
            it[Keys.MINUTE] = minute
        }
    }

    suspend fun setNudgeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NUDGE] = enabled }
    }

    suspend fun setNudgeTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        context.dataStore.edit {
            it[Keys.NUDGE_HOUR] = hour
            it[Keys.NUDGE_MINUTE] = minute
        }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING] = seen }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_ENABLED] = enabled }
    }

    /** Stores only the hash, never the password. */
    suspend fun setLockPassword(password: String) {
        context.dataStore.edit {
            it[Keys.LOCK_HASH] = hashPassword(password)
            it[Keys.LOCK_ENABLED] = true
        }
    }

    suspend fun clearLock() {
        context.dataStore.edit {
            it[Keys.LOCK_HASH] = ""
            it[Keys.LOCK_ENABLED] = false
        }
    }

    fun matchesLock(password: String, expectedHash: String): Boolean =
        expectedHash.isNotEmpty() && hashPassword(password) == expectedHash

    companion object {
        private const val SALT = "thirty-lock-v1"

        fun hashPassword(password: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest((SALT + password).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun replaceAll(s: AppSettings) {
        context.dataStore.edit {
            it[Keys.THEME] = s.themeMode.name
            it[Keys.NOTIFS] = s.notificationsEnabled
            it[Keys.HOUR] = s.reminderHour
            it[Keys.MINUTE] = s.reminderMinute
            it[Keys.NUDGE] = s.nudgeEnabled
            it[Keys.NUDGE_HOUR] = s.nudgeHour
            it[Keys.NUDGE_MINUTE] = s.nudgeMinute
            it[Keys.ONBOARDING] = s.onboardingSeen
        }
    }
}
