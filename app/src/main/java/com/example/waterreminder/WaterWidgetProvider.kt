package com.example.waterreminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WaterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ADD_QUICK) {
            WaterRepository.addWater(context, WaterRepository.getQuickAmountMl(context))
            updateHomeWidgets(context)
        }
    }

    companion object {
        const val ACTION_ADD_QUICK = "com.example.waterreminder.ACTION_ADD_QUICK"

        fun updateHomeWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
            updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            ids.forEach { widgetId ->
                manager.updateAppWidget(widgetId, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val total = WaterRepository.getTodayTotal(context)
            val target = WaterRepository.getTargetMl(context)
            val percent = WaterRepository.progressPercent(context)
            val quick = WaterRepository.getQuickAmountMl(context)
            return RemoteViews(context.packageName, R.layout.widget_water).apply {
                setTextViewText(R.id.widget_amount, "$total / $target ml")
                setProgressBar(R.id.widget_progress, 100, percent, false)
                setTextViewText(R.id.widget_add_button, "+${quick}ml")
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                setOnClickPendingIntent(R.id.widget_add_button, addQuickIntent(context))
            }
        }

        private fun addQuickIntent(context: Context): PendingIntent {
            val intent = Intent(context, WaterWidgetProvider::class.java).apply {
                action = ACTION_ADD_QUICK
            }
            return PendingIntent.getBroadcast(
                context,
                90_100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                90_101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
