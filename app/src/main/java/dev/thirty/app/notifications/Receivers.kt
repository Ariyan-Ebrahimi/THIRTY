package dev.thirty.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import dev.thirty.app.data.datastore.SettingsStore
import dev.thirty.app.data.local.ThirtyDatabase
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.widget.ThirtyWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        val challengeId = intent?.getLongExtra(NotificationHelper.EXTRA_CHALLENGE_ID, -1L) ?: -1L
        val isNudge = intent?.getBooleanExtra(NotificationHelper.EXTRA_IS_NUDGE, false) ?: false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    intent?.action == NotificationHelper.ACTION_MARK_DONE ->
                        markDone(app, challengeId)
                    isNudge -> ReminderScheduler.fireNudge(app, challengeId)
                    else -> ReminderScheduler.fireToday(app, challengeId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Notification action: complete today, clear the notification, refresh the widget. */
    private suspend fun markDone(app: Context, challengeId: Long) {
        try {
            if (challengeId <= 0) return
            val repo = ThirtyRepository(ThirtyDatabase.get(app), SettingsStore(app))
            repo.refreshDayStates()
            // null = keep whatever note the user already wrote for today.
            repo.completeToday(challengeId, null)
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ReminderScheduler.notifIdFor(challengeId))
            nm.cancel(ReminderScheduler.nudgeNotifIdFor(challengeId))
            ThirtyWidgetProvider.updateAll(app)
        } catch (_: Exception) { }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED ->
                ReminderScheduler.rescheduleFromStore(context.applicationContext)
        }
    }
}
