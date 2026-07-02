package com.example.waterreminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

class WaterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WaterReminderScheduler.scheduleMidnightRefresh(context)
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val settings = WaterStore.getSettings(context)
            val total = WaterStore.todayTotal(context)
            val progress = if (settings.dailyGoalMl <= 0) 0 else (total * 100 / settings.dailyGoalMl).coerceIn(0, 100)
            val reached = progress >= 100
            val views = RemoteViews(context.packageName, R.layout.widget_water).apply {
                setTextViewText(R.id.widget_title, if (reached) "今日已达标" else "今天喝水目标")
                setTextViewText(R.id.widget_amount, "${total}/${settings.dailyGoalMl}ml")
                setTextViewText(R.id.widget_add_button, "+${settings.defaultDrinkMl}ml")
                setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, if (settings.widgetLargeText) 13f else 11f)
                setTextViewTextSize(R.id.widget_amount, TypedValue.COMPLEX_UNIT_SP, if (settings.widgetLargeText) 24f else 20f)
                setTextViewTextSize(R.id.widget_add_button, TypedValue.COMPLEX_UNIT_SP, if (settings.widgetLargeText) 18f else 15f)
                setViewVisibility(R.id.widget_cup, if (settings.widgetShowCup) View.VISIBLE else View.GONE)
                setProgressBar(R.id.widget_progress, 100, progress, false)
                setOnClickPendingIntent(R.id.widget_add_button, addIntent(context))
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun addIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                20,
                Intent(context, WaterActionReceiver::class.java).setAction(WaterActions.ACTION_ADD_DEFAULT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                21,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
