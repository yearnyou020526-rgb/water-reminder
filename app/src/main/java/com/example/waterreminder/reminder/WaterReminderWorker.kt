package com.example.waterreminder.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.waterreminder.MainActivity
import com.example.waterreminder.R
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.settings.SettingsDataStore
import kotlinx.coroutines.flow.first

class WaterReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settingsStore = SettingsDataStore(applicationContext)
        val settings = settingsStore.settingsFlow.first()

        if (settings.reminderEnabled &&
            settings.mutedDate != WaterRepository.todayDate() &&
            ReminderScheduler.isInsideReminderWindow(settings) &&
            canPostNotifications()
        ) {
            showNotification(settings.defaultDrinkMl)
        }

        ReminderScheduler.scheduleNext(applicationContext, settings)
        return Result.success()
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showNotification(defaultDrinkMl: Int) {
        createChannel()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该喝水了")
            .setContentText("记录一杯水，保持今天的饮水目标。")
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_notification,
                "记录 ${defaultDrinkMl}ml",
                actionIntent(NotificationActionReceiver.ACTION_ADD_DEFAULT)
            )
            .addAction(
                R.drawable.ic_notification,
                "稍后提醒",
                actionIntent(NotificationActionReceiver.ACTION_SNOOZE)
            )
            .addAction(
                R.drawable.ic_notification,
                "今天不再提醒",
                actionIntent(NotificationActionReceiver.ACTION_MUTE_TODAY)
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            applicationContext,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            120_200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "喝水提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "water_reminders"
        private const val NOTIFICATION_ID = 20_000
    }
}
