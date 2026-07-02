package com.example.waterreminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar
import kotlin.math.max

object WaterActions {
    const val ACTION_REMINDER = "com.example.waterreminder.ACTION_REMINDER"
    const val ACTION_ADD_DEFAULT = "com.example.waterreminder.ACTION_ADD_DEFAULT"
    const val ACTION_SNOOZE = "com.example.waterreminder.ACTION_SNOOZE"
    const val ACTION_MUTE_TODAY = "com.example.waterreminder.ACTION_MUTE_TODAY"
    const val ACTION_MIDNIGHT_REFRESH = "com.example.waterreminder.ACTION_MIDNIGHT_REFRESH"
    const val ACTION_BOOT = "com.example.waterreminder.ACTION_BOOT"
}

object WaterReminderScheduler {
    private const val CHANNEL_ID = "water_reminders"
    private const val NOTIFICATION_ID = 20000
    private const val REQUEST_REMINDER = 4100
    private const val REQUEST_SNOOZE = 4101
    private const val REQUEST_MIDNIGHT = 4102

    fun scheduleNext(context: Context) {
        val settings = WaterStore.getSettings(context)
        cancel(context, REQUEST_REMINDER, WaterActions.ACTION_REMINDER)
        if (!settings.reminderEnabled ||
            settings.mutedDate == WaterStore.todayDate() ||
            WaterStore.isGoalReached(context)
        ) {
            return
        }
        scheduleAt(context, WaterActions.ACTION_REMINDER, REQUEST_REMINDER, nextTriggerMillis(settings))
    }

    fun scheduleMidnightRefresh(context: Context) {
        cancel(context, REQUEST_MIDNIGHT, WaterActions.ACTION_MIDNIGHT_REFRESH)
        scheduleAt(context, WaterActions.ACTION_MIDNIGHT_REFRESH, REQUEST_MIDNIGHT, nextMidnightMillis())
    }

    fun scheduleSnooze(context: Context) {
        scheduleAt(
            context,
            WaterActions.ACTION_REMINDER,
            REQUEST_SNOOZE,
            System.currentTimeMillis() + 15 * 60 * 1000L
        )
    }

    fun cancelAll(context: Context) {
        cancel(context, REQUEST_REMINDER, WaterActions.ACTION_REMINDER)
        cancel(context, REQUEST_SNOOZE, WaterActions.ACTION_REMINDER)
    }

    fun cancelReminderOnly(context: Context) {
        cancel(context, REQUEST_REMINDER, WaterActions.ACTION_REMINDER)
        cancel(context, REQUEST_SNOOZE, WaterActions.ACTION_REMINDER)
    }

    fun showReminder(context: Context) {
        val settings = WaterStore.getSettings(context)
        if (!settings.reminderEnabled ||
            settings.mutedDate == WaterStore.todayDate() ||
            WaterStore.isGoalReached(context) ||
            !isInsideReminderWindow(settings) ||
            !canPostNotifications(context)
        ) {
            if (WaterStore.isGoalReached(context)) WaterStore.muteIfGoalReached(context)
            scheduleNext(context)
            return
        }

        createChannel(context)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该喝水了")
            .setContentText("记录一杯水，继续完成今天的饮水目标。")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_notification,
                "记录 ${settings.defaultDrinkMl}ml",
                actionIntent(context, WaterActions.ACTION_ADD_DEFAULT, 1)
            )
            .addAction(
                R.drawable.ic_notification,
                "稍后提醒",
                actionIntent(context, WaterActions.ACTION_SNOOZE, 2)
            )
            .addAction(
                R.drawable.ic_notification,
                "今天不再提醒",
                actionIntent(context, WaterActions.ACTION_MUTE_TODAY, 3)
            )
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        scheduleNext(context)
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun isInsideReminderWindow(settings: WaterSettings): Boolean {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = settings.reminderStartHour * 60 + settings.reminderStartMinute
        val end = settings.reminderEndHour * 60 + settings.reminderEndMinute
        return if (start <= end) minutes in start..end else minutes >= start || minutes <= end
    }

    private fun nextTriggerMillis(settings: WaterSettings): Long {
        val now = Calendar.getInstance()
        val next = now.clone() as Calendar
        next.add(Calendar.MINUTE, settings.reminderIntervalMinutes)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        val candidate = next.get(Calendar.HOUR_OF_DAY) * 60 + next.get(Calendar.MINUTE)
        val start = settings.reminderStartHour * 60 + settings.reminderStartMinute
        val end = settings.reminderEndHour * 60 + settings.reminderEndMinute
        val inside = if (start <= end) candidate in start..end else candidate >= start || candidate <= end
        if (inside) return max(System.currentTimeMillis(), next.timeInMillis)

        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reminderStartHour)
            set(Calendar.MINUTE, settings.reminderStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    private fun nextMidnightMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun scheduleAt(context: Context, action: String, requestCode: Int, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WaterActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun cancel(context: Context, requestCode: Int, action: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WaterActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WaterActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            8,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "喝水提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

class WaterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WaterActions.ACTION_REMINDER -> WaterReminderScheduler.showReminder(context)
            WaterActions.ACTION_ADD_DEFAULT -> {
                WaterStore.addWater(context, WaterStore.getSettings(context).defaultDrinkMl)
                context.getSystemService(NotificationManager::class.java).cancel(20000)
            }
            WaterActions.ACTION_SNOOZE -> {
                WaterReminderScheduler.scheduleSnooze(context)
                context.getSystemService(NotificationManager::class.java).cancel(20000)
            }
            WaterActions.ACTION_MUTE_TODAY -> {
                WaterStore.muteToday(context)
                WaterReminderScheduler.cancelAll(context)
                context.getSystemService(NotificationManager::class.java).cancel(20000)
            }
            WaterActions.ACTION_MIDNIGHT_REFRESH -> {
                WaterWidgetProvider.updateAll(context)
                WaterReminderScheduler.scheduleNext(context)
                WaterReminderScheduler.scheduleMidnightRefresh(context)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterReminderScheduler.scheduleNext(context)
        WaterReminderScheduler.scheduleMidnightRefresh(context)
        WaterWidgetProvider.updateAll(context)
    }
}
