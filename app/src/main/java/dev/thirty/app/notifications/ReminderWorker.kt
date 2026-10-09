package dev.thirty.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.thirty.app.data.datastore.SettingsStore
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.local.ThirtyDatabase
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.util.DateUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Safety net for daily reminders.
 *
 * Exact alarms are the primary delivery path, but OEM battery managers,
 * missed boot events or revoked exact-alarm permission can silently kill
 * them. This worker runs ~every 30 minutes and delivers any reminder
 * whose time has passed but which was never shown today.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val app = applicationContext
            val repo = ThirtyRepository(ThirtyDatabase.get(app), SettingsStore(app))
            repo.refreshDayStates()
            val now = System.currentTimeMillis()
            val today = DateUtils.today()
            val settings = repo.currentSettings()
            repo.activeListWithProgress().forEach { p ->
                val c = p.challenge
                if (!c.reminderEnabled) return@forEach
                if (ReminderState.wasShown(app, c.id, today.toEpochDay())) {
                    // Main reminder already delivered — still check the nudge below.
                } else {
                    val due = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, c.reminderHour)
                        set(Calendar.MINUTE, c.reminderMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (now >= due) {
                        NotificationHelper.showReminder(
                            app,
                            ReminderScheduler.notifIdFor(c.id),
                            p.todayDayNumber,
                            c.title
                        )
                        ReminderState.markShown(app, c.id, today.toEpochDay())
                        // Re-arm the alarm too, in case it was lost.
                        ReminderScheduler.scheduleDaily(app, c.id, c.reminderHour, c.reminderMinute)
                    }
                }
                // Evening-nudge backup: past nudge time, day incomplete, not nudged yet.
                if (settings.nudgeEnabled &&
                    !ReminderState.wasNudged(app, c.id, today.toEpochDay())
                ) {
                    val nudgeDue = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, settings.nudgeHour)
                        set(Calendar.MINUTE, settings.nudgeMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val row = p.days.firstOrNull { it.dayNumber == p.todayDayNumber }
                    if (now >= nudgeDue && row != null &&
                        row.status != DayStatus.COMPLETED && row.status != DayStatus.REST
                    ) {
                        NotificationHelper.showNudge(
                            app,
                            ReminderScheduler.nudgeNotifIdFor(c.id),
                            p.todayDayNumber,
                            c.title
                        )
                        ReminderState.markNudged(app, c.id, today.toEpochDay())
                    }
                }
            }
        } catch (_: Exception) {
            // Never fail the periodic chain because of a transient error.
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "thirty_reminder_backup"

        fun enqueue(context: Context) {
            try {
                val req = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES)
                    .addTag(UNIQUE_NAME)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            } catch (_: Exception) { }
        }
    }
}
