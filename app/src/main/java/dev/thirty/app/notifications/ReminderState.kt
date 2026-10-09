package dev.thirty.app.notifications

import android.content.Context

/**
 * Tracks reminder delivery state so the alarm path and the
 * WorkManager backup path never double-notify and we can tell
 * whether a scheduled reminder actually fired.
 */
object ReminderState {
    private const val PREFS = "thirty_reminder_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markShown(context: Context, challengeId: Long, epochDay: Long) {
        prefs(context).edit().putLong("shown_$challengeId", epochDay).apply()
    }

    fun wasShown(context: Context, challengeId: Long, epochDay: Long): Boolean =
        prefs(context).getLong("shown_$challengeId", Long.MIN_VALUE) == epochDay

    fun markNudged(context: Context, challengeId: Long, epochDay: Long) {
        prefs(context).edit().putLong("nudged_$challengeId", epochDay).apply()
    }

    fun wasNudged(context: Context, challengeId: Long, epochDay: Long): Boolean =
        prefs(context).getLong("nudged_$challengeId", Long.MIN_VALUE) == epochDay

    fun setScheduled(context: Context, challengeId: Long, triggerMillis: Long) {
        prefs(context).edit().putLong("scheduled_$challengeId", triggerMillis).apply()
    }

    fun getScheduled(context: Context, challengeId: Long): Long =
        prefs(context).getLong("scheduled_$challengeId", -1L)

    fun clearFor(context: Context, challengeId: Long) {
        prefs(context).edit()
            .remove("shown_$challengeId")
            .remove("scheduled_$challengeId")
            .apply()
    }
}
