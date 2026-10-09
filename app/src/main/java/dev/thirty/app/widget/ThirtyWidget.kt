package dev.thirty.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import dev.thirty.app.MainActivity
import dev.thirty.app.R
import dev.thirty.app.data.datastore.SettingsStore
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.local.ThirtyDatabase
import dev.thirty.app.data.repository.ChallengeWithProgress
import dev.thirty.app.data.repository.ThirtyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Classic RemoteViews home-screen widget (no extra dependencies):
 * DAY x / N + goal title + I DID IT button.
 */
class ThirtyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext
                    val repo = ThirtyRepository(ThirtyDatabase.get(app), SettingsStore(app))
                    repo.refreshDayStates()
                    val plans = repo.activeListWithProgress()
                    val savedId = repo.selectedPlanId()
                    val plan = plans.firstOrNull { it.challenge.id == savedId } ?: plans.firstOrNull()
                    val mgr = AppWidgetManager.getInstance(app)
                    val ids = mgr.getAppWidgetIds(
                        ComponentName(app, ThirtyWidgetProvider::class.java)
                    )
                    ids.forEach { id ->
                        try {
                            mgr.updateAppWidget(id, views(app, plan))
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
        }

        private fun views(context: Context, plan: ChallengeWithProgress?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.thirty_widget)
            // Tap anywhere (except the button) opens the app.
            val openApp = PendingIntent.getActivity(
                context, 9100,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            if (plan == null) {
                views.setTextViewText(R.id.widget_day, "DAY –")
                views.setTextViewText(R.id.widget_total, "")
                views.setTextViewText(R.id.widget_title, "No active plan — tap to start.")
                views.setTextViewText(R.id.widget_action, "OPEN APP")
                views.setOnClickPendingIntent(R.id.widget_action, openApp)
                return views
            }
            val todayStatus = plan.days
                .firstOrNull { it.dayNumber == plan.todayDayNumber }?.status
            val done = todayStatus == DayStatus.COMPLETED || todayStatus == DayStatus.REST
            views.setTextViewText(
                R.id.widget_day,
                "DAY ${plan.todayDayNumber}"
            )
            views.setTextViewText(R.id.widget_total, "/ ${plan.challenge.lengthDays}")
            views.setTextViewText(R.id.widget_title, plan.challenge.title)
            if (done) {
                views.setTextViewText(R.id.widget_action, "✓ DONE")
                views.setInt(R.id.widget_action, "setBackgroundResource", R.drawable.widget_cta_done)
                views.setTextColor(R.id.widget_action, Color.WHITE)
                views.setOnClickPendingIntent(R.id.widget_action, openApp)
            } else {
                views.setTextViewText(R.id.widget_action, "I DID IT")
                views.setInt(R.id.widget_action, "setBackgroundResource", R.drawable.widget_cta)
                views.setTextColor(R.id.widget_action, Color.BLACK)
                val complete = PendingIntent.getBroadcast(
                    context, 9101,
                    Intent(context, WidgetActionReceiver::class.java).apply {
                        action = WidgetActionReceiver.ACTION_COMPLETE
                        putExtra(
                            WidgetActionReceiver.EXTRA_PLAN_ID,
                            plan.challenge.id
                        )
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_action, complete)
            }
            return views
        }
    }
}

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_COMPLETE) return
        val pending = goAsync()
        val planId = intent.getLongExtra(EXTRA_PLAN_ID, -1L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                val repo = ThirtyRepository(ThirtyDatabase.get(app), SettingsStore(app))
                repo.refreshDayStates()
                val plan = if (planId > 0) {
                    repo.challengeWithProgress(planId)
                } else {
                    val plans = repo.activeListWithProgress()
                    val savedId = repo.selectedPlanId()
                    plans.firstOrNull { it.challenge.id == savedId } ?: plans.firstOrNull()
                }
                plan?.let { repo.completeToday(it.challenge.id, null) }
            } catch (_: Exception) { }
            try {
                ThirtyWidgetProvider.updateAll(context.applicationContext)
            } catch (_: Exception) { }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_COMPLETE = "dev.thirty.app.widget.COMPLETE_TODAY"
        const val EXTRA_PLAN_ID = "plan_id"
    }
}

object ThirtyWidgetSync {
    fun updateAll(context: Context) {
        ThirtyWidgetProvider.updateAll(context)
    }
}
