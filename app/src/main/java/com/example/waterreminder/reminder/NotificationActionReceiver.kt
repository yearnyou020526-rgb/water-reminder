package com.example.waterreminder.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.settings.SettingsDataStore
import com.example.waterreminder.widget.WaterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsStore = SettingsDataStore(context)
                val settings = settingsStore.settingsFlow.first()
                when (intent.action) {
                    ACTION_ADD_DEFAULT -> {
                        val repo = WaterRepository(
                            WaterDatabase.get(context).waterRecordDao()
                        )
                        repo.addWater(settings.defaultDrinkMl)
                        WaterWidget.updateAll(context)
                    }

                    ACTION_SNOOZE -> ReminderScheduler.scheduleSnooze(context)
                    ACTION_MUTE_TODAY -> {
                        settingsStore.muteToday(WaterRepository.todayDate())
                        ReminderScheduler.cancelAll(context)
                    }
                }
                context.getSystemService(NotificationManager::class.java).cancel(20_000)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD_DEFAULT = "com.example.waterreminder.ACTION_ADD_DEFAULT"
        const val ACTION_SNOOZE = "com.example.waterreminder.ACTION_SNOOZE"
        const val ACTION_MUTE_TODAY = "com.example.waterreminder.ACTION_MUTE_TODAY"
    }
}
