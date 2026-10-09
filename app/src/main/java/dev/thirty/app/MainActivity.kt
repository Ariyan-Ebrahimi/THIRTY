package dev.thirty.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thirty.app.data.datastore.ThemeMode
import dev.thirty.app.navigation.ThirtyNav
import dev.thirty.app.ui.lock.LockGate
import dev.thirty.app.ui.theme.ThirtyTheme

class MainActivity : ComponentActivity() {

    /** Unlocked for this session; reset whenever the app goes to the background. */
    private var unlocked by mutableStateOf(false)
    private var pendingLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ThirtyApp
        setContent {
            val settings by app.repository.settingsFlow.collectAsState(initial = null)
            val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ThirtyTheme(darkTheme = darkTheme) {
                LockGate(
                    settings = settings,
                    unlocked = unlocked,
                    onUnlocked = { unlocked = true }
                ) {
                    ThirtyNav(repository = app.repository)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingLock) {
            pendingLock = false
            unlocked = false
        }
        // Re-arm daily alarms every time the app comes to the foreground,
        // so killed alarms are restored without waiting for a reboot.
        dev.thirty.app.notifications.ReminderScheduler.rescheduleFromStore(applicationContext)
        dev.thirty.app.widget.ThirtyWidgetSync.updateAll(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        // Left the app: ask for the password again on return (if a lock is set).
        pendingLock = true
    }
}
