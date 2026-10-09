package dev.thirty.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dev.thirty.app.MainActivity
import dev.thirty.app.R
import dev.thirty.app.data.datastore.SettingsStore
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.local.ThirtyDatabase
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object NotificationHelper {
    const val CHANNEL_ID = "thirty_reminder"
    const val ACTION_SHOW = "dev.thirty.app.SHOW_REMINDER"
    const val ACTION_MARK_DONE = "dev.thirty.app.MARK_DONE"
    const val EXTRA_CHALLENGE_ID = "challenge_id"
    const val EXTRA_IS_NUDGE = "is_nudge"
    private const val MARK_DONE_CODE = 60000

    /** "Mark done" button — completes today straight from the notification. */
    private fun addMarkDoneAction(
        builder: NotificationCompat.Builder,
        context: Context,
        challengeId: Long
    ) {
        if (challengeId <= 0) return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_CHALLENGE_ID, challengeId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            MARK_DONE_CODE + (challengeId % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action(R.drawable.ic_notification, "Mark done", pi)
        )
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Daily reminder",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Your daily THIRTY reminder." }
                )
            }
        }
    }

    fun showReminder(
        context: Context,
        notifId: Int,
        dayNumber: Int,
        title: String,
        challengeId: Long = -1L
    ) {
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 9000 + notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("THIRTY — Day $dayNumber")
            .setContentText(title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Day $dayNumber.\nShow up today.\n$title")
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        addMarkDoneAction(builder, context, challengeId)
        val notif = builder.build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notifId, notif)
        } catch (_: Exception) { /* permission revoked or system busy — backup paths retry */ }
    }

    fun showNudge(
        context: Context,
        notifId: Int,
        dayNumber: Int,
        title: String,
        challengeId: Long = -1L
    ) {
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 9500 + notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Still time today")
            .setContentText("Day $dayNumber — $title")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Day $dayNumber isn\u2019t done yet.\nStill time today.\n$title")
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        addMarkDoneAction(builder, context, challengeId)
        val notif = builder.build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notifId, notif)
        } catch (_: Exception) { }
    }
}

object ReminderScheduler {

    private fun requestCodeFor(challengeId: Long): Int = (20000 + (challengeId % 10000)).toInt()

    private fun nudgeRequestCodeFor(challengeId: Long): Int = (40000 + (challengeId % 10000)).toInt()

    fun nudgeNotifIdFor(challengeId: Long): Int = (3000 + (challengeId % 10000)).toInt()

    fun notifIdFor(challengeId: Long): Int = (1000 + (challengeId % 10000)).toInt()

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) { }
        }
    }

    private fun alarmIntent(context: Context, challengeId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SHOW
            putExtra(NotificationHelper.EXTRA_CHALLENGE_ID, challengeId)
            putExtra(NotificationHelper.EXTRA_IS_NUDGE, false)
        }
        return PendingIntent.getBroadcast(
            context, requestCodeFor(challengeId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nudgeIntent(context: Context, challengeId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SHOW
            putExtra(NotificationHelper.EXTRA_CHALLENGE_ID, challengeId)
            putExtra(NotificationHelper.EXTRA_IS_NUDGE, true)
        }
        return PendingIntent.getBroadcast(
            context, nudgeRequestCodeFor(challengeId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun armAt(context: Context, pi: PendingIntent, hour: Int, minute: Int): Long {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (hasExactAlarmPermission(context)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                return cal.timeInMillis
            } catch (_: SecurityException) { /* fall through to inexact */ }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        return cal.timeInMillis
    }

    /**
     * Schedule the next daily alarm for one challenge.
     * NOTE: only ONE alarm call is made — a previous version also called
     * setInexactRepeating with the same PendingIntent, which silently
     * replaced (and killed) the exact alarm. That was the reminder bug.
     */
    fun scheduleDaily(context: Context, challengeId: Long, hour: Int, minute: Int) {
        val trigger = armAt(context, alarmIntent(context, challengeId), hour, minute)
        ReminderState.setScheduled(context, challengeId, trigger)
    }

    /** Evening nudge: a second alarm that only fires if the day isn't done. */
    fun scheduleNudge(context: Context, challengeId: Long, hour: Int, minute: Int) {
        armAt(context, nudgeIntent(context, challengeId), hour, minute)
    }

    fun cancel(context: Context, challengeId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context, challengeId))
    }

    fun cancelNudge(context: Context, challengeId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(nudgeIntent(context, challengeId))
    }

    /** Re-arm alarms for every active challenge with reminders on. */
    fun rescheduleFromStore(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ThirtyDatabase.get(context)
                val store = dev.thirty.app.data.datastore.SettingsStore(context)
                val settings = store.current()
                val actives = db.challengeDao().activeListOnce()
                actives.forEach { c ->
                    if (c.reminderEnabled) {
                        scheduleDaily(context, c.id, c.reminderHour, c.reminderMinute)
                    } else {
                        cancel(context, c.id)
                    }
                    if (settings.nudgeEnabled && c.reminderEnabled) {
                        scheduleNudge(context, c.id, settings.nudgeHour, settings.nudgeMinute)
                    } else {
                        cancelNudge(context, c.id)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /** Fire the reminder for one challenge and re-arm tomorrow's alarm. */
    suspend fun fireToday(context: Context, challengeId: Long) {
        try {
            val db = ThirtyDatabase.get(context)
            val challenge = if (challengeId > 0) {
                db.challengeDao().byId(challengeId)
            } else {
                db.challengeDao().activeListOnce().firstOrNull()
            } ?: return
            if (challenge.status != dev.thirty.app.data.local.ChallengeStatus.ACTIVE) return
            if (!challenge.reminderEnabled) return
            val today = DateUtils.today()
            if (ReminderState.wasShown(context, challenge.id, today.toEpochDay())) {
                // Already delivered today (e.g. by the backup worker) — just re-arm.
                scheduleDaily(context, challenge.id, challenge.reminderHour, challenge.reminderMinute)
                return
            }
            val start = DateUtils.fromEpochDay(challenge.startEpochDay)
            val day = ChallengeLogic.dayNumberFor(start, today, challenge.lengthDays)
            NotificationHelper.showReminder(
                context, notifIdFor(challenge.id), day, challenge.title, challenge.id
            )
            ReminderState.markShown(context, challenge.id, today.toEpochDay())
            scheduleDaily(context, challenge.id, challenge.reminderHour, challenge.reminderMinute)
        } catch (_: Exception) { }
    }

    /** Evening nudge: only fires if today isn't done yet. Never nags. */
    suspend fun fireNudge(context: Context, challengeId: Long) {
        try {
            val settings = SettingsStore(context).current()
            val db = ThirtyDatabase.get(context)
            val challenge = if (challengeId > 0) {
                db.challengeDao().byId(challengeId)
            } else {
                db.challengeDao().activeListOnce().firstOrNull()
            } ?: return
            // Always re-arm for tomorrow first.
            if (settings.nudgeEnabled && challenge.reminderEnabled) {
                scheduleNudge(context, challenge.id, settings.nudgeHour, settings.nudgeMinute)
            }
            if (challenge.status != dev.thirty.app.data.local.ChallengeStatus.ACTIVE) return
            if (!challenge.reminderEnabled || !settings.nudgeEnabled) return
            val today = DateUtils.today()
            if (ReminderState.wasNudged(context, challenge.id, today.toEpochDay())) return
            val start = DateUtils.fromEpochDay(challenge.startEpochDay)
            val day = ChallengeLogic.dayNumberFor(start, today, challenge.lengthDays)
            val row = db.progressDao().dayOnce(challenge.id, day) ?: return
            if (row.status == DayStatus.COMPLETED || row.status == DayStatus.REST) return
            val startDay = DateUtils.fromEpochDay(challenge.startEpochDay)
            NotificationHelper.showNudge(
                context, nudgeNotifIdFor(challenge.id),
                ChallengeLogic.dayNumberFor(startDay, today, challenge.lengthDays), challenge.title,
                challenge.id
            )
            ReminderState.markNudged(context, challenge.id, today.toEpochDay())
        } catch (_: Exception) { }
    }
}
