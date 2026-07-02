package com.example.waterreminder.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.waterSettingsDataStore by preferencesDataStore(name = "water_settings")

class SettingsDataStore(
    private val context: Context
) {
    val settingsFlow: Flow<WaterSettings> = context.waterSettingsDataStore.data.map { prefs ->
        WaterSettings(
            dailyGoalMl = prefs[Keys.dailyGoalMl] ?: 2000,
            defaultDrinkMl = prefs[Keys.defaultDrinkMl] ?: 200,
            reminderIntervalMinutes = prefs[Keys.reminderIntervalMinutes] ?: 60,
            reminderStartHour = prefs[Keys.reminderStartHour] ?: 8,
            reminderStartMinute = prefs[Keys.reminderStartMinute] ?: 0,
            reminderEndHour = prefs[Keys.reminderEndHour] ?: 23,
            reminderEndMinute = prefs[Keys.reminderEndMinute] ?: 0,
            reminderEnabled = prefs[Keys.reminderEnabled] ?: true,
            mutedDate = prefs[Keys.mutedDate] ?: ""
        )
    }

    suspend fun updateDailyGoal(value: Int) {
        context.waterSettingsDataStore.edit { it[Keys.dailyGoalMl] = value.coerceIn(100, 10000) }
    }

    suspend fun updateDefaultDrink(value: Int) {
        context.waterSettingsDataStore.edit { it[Keys.defaultDrinkMl] = value.coerceIn(10, 2000) }
    }

    suspend fun updateReminderInterval(value: Int) {
        val allowed = setOf(15, 30, 45, 60, 90, 120)
        context.waterSettingsDataStore.edit {
            it[Keys.reminderIntervalMinutes] = if (value in allowed) value else 60
        }
    }

    suspend fun updateReminderStart(hour: Int, minute: Int) {
        context.waterSettingsDataStore.edit {
            it[Keys.reminderStartHour] = hour.coerceIn(0, 23)
            it[Keys.reminderStartMinute] = minute.coerceIn(0, 59)
        }
    }

    suspend fun updateReminderEnd(hour: Int, minute: Int) {
        context.waterSettingsDataStore.edit {
            it[Keys.reminderEndHour] = hour.coerceIn(0, 23)
            it[Keys.reminderEndMinute] = minute.coerceIn(0, 59)
        }
    }

    suspend fun updateReminderEnabled(enabled: Boolean) {
        context.waterSettingsDataStore.edit { it[Keys.reminderEnabled] = enabled }
    }

    suspend fun muteToday(date: String) {
        context.waterSettingsDataStore.edit { it[Keys.mutedDate] = date }
    }

    private object Keys {
        val dailyGoalMl = intPreferencesKey("dailyGoalMl")
        val defaultDrinkMl = intPreferencesKey("defaultDrinkMl")
        val reminderIntervalMinutes = intPreferencesKey("reminderIntervalMinutes")
        val reminderStartHour = intPreferencesKey("reminderStartHour")
        val reminderStartMinute = intPreferencesKey("reminderStartMinute")
        val reminderEndHour = intPreferencesKey("reminderEndHour")
        val reminderEndMinute = intPreferencesKey("reminderEndMinute")
        val reminderEnabled = booleanPreferencesKey("reminderEnabled")
        val mutedDate = stringPreferencesKey("mutedDate")
    }
}
