package com.example.waterreminder.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.settings.WaterSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max

object ReminderScheduler {
    private const val REMINDER_WORK_NAME = "water_reminder_work"
    private const val SNOOZE_WORK_NAME = "water_snooze_work"

    fun scheduleNext(context: Context, settings: WaterSettings) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(REMINDER_WORK_NAME)
        if (!settings.reminderEnabled || settings.mutedDate == WaterRepository.todayDate()) return

        val delayMillis = nextDelayMillis(settings)
        val request = OneTimeWorkRequestBuilder<WaterReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(REMINDER_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleSnooze(context: Context, delayMinutes: Int = 15) {
        val request = OneTimeWorkRequestBuilder<WaterReminderWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SNOOZE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(SNOOZE_WORK_NAME)
    }

    fun isInsideReminderWindow(settings: WaterSettings): Boolean {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return isInsideReminderWindow(settings, nowMinutes)
    }

    private fun nextDelayMillis(settings: WaterSettings): Long {
        val now = Calendar.getInstance()
        val next = now.clone() as Calendar
        next.add(Calendar.MINUTE, settings.reminderIntervalMinutes)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        val candidateMinutes = next.get(Calendar.HOUR_OF_DAY) * 60 + next.get(Calendar.MINUTE)
        if (isInsideReminderWindow(settings, candidateMinutes)) {
            return max(0, next.timeInMillis - now.timeInMillis)
        }

        val startMinutes = settings.reminderStartHour * 60 + settings.reminderStartMinute
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMinutes / 60)
            set(Calendar.MINUTE, startMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return max(0, start.timeInMillis - now.timeInMillis)
    }

    private fun isInsideReminderWindow(settings: WaterSettings, minutes: Int): Boolean {
        val start = settings.reminderStartHour * 60 + settings.reminderStartMinute
        val end = settings.reminderEndHour * 60 + settings.reminderEndMinute
        return if (start <= end) {
            minutes in start..end
        } else {
            minutes >= start || minutes <= end
        }
    }
}
