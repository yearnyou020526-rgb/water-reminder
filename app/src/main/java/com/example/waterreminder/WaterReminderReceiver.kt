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

class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (WaterRepository.isReminderEnabled(context) &&
            WaterReminderScheduler.isInsideReminderWindow(context) &&
            WaterReminderScheduler.canPostNotifications(context)
        ) {
            WaterReminderScheduler.showReminder(context)
        }
        WaterReminderScheduler.scheduleNext(context)
        WaterWidgetProvider.updateHomeWidgets(context)
    }
}

class WaterBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterReminderScheduler.scheduleNext(context)
        WaterWidgetProvider.updateHomeWidgets(context)
    }
}

object WaterReminderScheduler {
    private const val CHANNEL_ID = "water_reminders"
    private const val CHANNEL_NAME = "喝水提醒"
    private const val REQUEST_CODE = 73_000

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(reminderPendingIntent(context))
        if (!WaterRepository.isReminderEnabled(context)) return
        scheduleAt(alarmManager, nextTriggerMillis(context), reminderPendingIntent(context))
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun isInsideReminderWindow(context: Context): Boolean {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = WaterRepository.getStartMinutes(context)
        val end = WaterRepository.getEndMinutes(context)
        return if (start <= end) {
            minutes in start..end
        } else {
            minutes >= start || minutes <= end
        }
    }

    fun showReminder(context: Context) {
        createNotificationChannel(context)
        val total = WaterRepository.getTodayTotal(context)
        val target = WaterRepository.getTargetMl(context)
        val quick = WaterRepository.getQuickAmountMl(context)
        val content = "今天已喝 $total ml，目标 $target ml。点开记录，或用桌面小组件快速 +${quick}ml。"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该喝水了")
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(REQUEST_CODE, notification)
    }

    private fun nextTriggerMillis(context: Context): Long {
        val now = Calendar.getInstance()
        val candidate = now.clone() as Calendar
        val interval = WaterRepository.getIntervalMinutes(context)
        candidate.add(Calendar.MINUTE, interval)
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)

        val start = WaterRepository.getStartMinutes(context)
        val end = WaterRepository.getEndMinutes(context)
        val candidateMinutes = candidate.get(Calendar.HOUR_OF_DAY) * 60 + candidate.get(Calendar.MINUTE)
        val inside = if (start <= end) candidateMinutes in start..end else candidateMinutes >= start || candidateMinutes <= end
        if (inside) return candidate.timeInMillis

        val nextStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, start / 60)
            set(Calendar.MINUTE, start % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return nextStart.timeInMillis
    }

    private fun scheduleAt(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WaterReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
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
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
