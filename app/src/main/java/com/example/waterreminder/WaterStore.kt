package com.example.waterreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class WaterRecord(
    val id: Long,
    val amountMl: Int,
    val timestamp: Long,
    val date: String
)

data class WaterSettings(
    val dailyGoalMl: Int = 2000,
    val defaultDrinkMl: Int = 500,
    val reminderIntervalMinutes: Int = 60,
    val reminderStartHour: Int = 8,
    val reminderStartMinute: Int = 0,
    val reminderEndHour: Int = 23,
    val reminderEndMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val mutedDate: String = ""
)

data class DayTotal(val date: String, val totalMl: Int)

object WaterStore {
    private const val PREFS = "water_store"
    private const val KEY_RECORDS = "records"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_GOAL = "daily_goal_ml"
    private const val KEY_DEFAULT = "default_drink_ml"
    private const val KEY_INTERVAL = "reminder_interval"
    private const val KEY_START_HOUR = "start_hour"
    private const val KEY_START_MINUTE = "start_minute"
    private const val KEY_END_HOUR = "end_hour"
    private const val KEY_END_MINUTE = "end_minute"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_MUTED_DATE = "muted_date"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    fun todayDate(): String = dateFormat.format(Date())

    fun shortDate(date: String): String = runCatching {
        shortDateFormat.format(dateFormat.parse(date) ?: Date())
    }.getOrDefault(date.takeLast(5))

    fun getSettings(context: Context): WaterSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WaterSettings(
            dailyGoalMl = prefs.getInt(KEY_GOAL, 2000),
            defaultDrinkMl = prefs.getInt(KEY_DEFAULT, 500),
            reminderIntervalMinutes = prefs.getInt(KEY_INTERVAL, 60),
            reminderStartHour = prefs.getInt(KEY_START_HOUR, 8),
            reminderStartMinute = prefs.getInt(KEY_START_MINUTE, 0),
            reminderEndHour = prefs.getInt(KEY_END_HOUR, 23),
            reminderEndMinute = prefs.getInt(KEY_END_MINUTE, 0),
            reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, true),
            mutedDate = prefs.getString(KEY_MUTED_DATE, "") ?: ""
        )
    }

    fun updateSettings(context: Context, update: WaterSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_GOAL, update.dailyGoalMl.coerceIn(100, 10000))
            .putInt(KEY_DEFAULT, update.defaultDrinkMl.coerceIn(10, 5000))
            .putInt(KEY_INTERVAL, update.reminderIntervalMinutes)
            .putInt(KEY_START_HOUR, update.reminderStartHour.coerceIn(0, 23))
            .putInt(KEY_START_MINUTE, update.reminderStartMinute.coerceIn(0, 59))
            .putInt(KEY_END_HOUR, update.reminderEndHour.coerceIn(0, 23))
            .putInt(KEY_END_MINUTE, update.reminderEndMinute.coerceIn(0, 59))
            .putBoolean(KEY_REMINDER_ENABLED, update.reminderEnabled)
            .putString(KEY_MUTED_DATE, update.mutedDate)
            .apply()
        WaterReminderScheduler.scheduleNext(context)
        WaterWidgetProvider.updateAll(context)
    }

    fun muteToday(context: Context) {
        val settings = getSettings(context)
        updateSettings(context, settings.copy(mutedDate = todayDate()))
    }

    fun addWater(context: Context, amountMl: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_NEXT_ID, 1L)
        val now = System.currentTimeMillis()
        val records = getRecords(context).toMutableList()
        records.add(
            WaterRecord(
                id = id,
                amountMl = amountMl.coerceIn(1, 5000),
                timestamp = now,
                date = dateFormat.format(Date(now))
            )
        )
        saveRecords(context, records)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        WaterWidgetProvider.updateAll(context)
    }

    fun deleteRecord(context: Context, id: Long) {
        saveRecords(context, getRecords(context).filterNot { it.id == id })
        WaterWidgetProvider.updateAll(context)
    }

    fun getRecords(context: Context): List<WaterRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            WaterRecord(
                id = obj.optLong("id"),
                amountMl = obj.optInt("amountMl"),
                timestamp = obj.optLong("timestamp"),
                date = obj.optString("date")
            )
        }.sortedByDescending { it.timestamp }
    }

    fun todayRecords(context: Context): List<WaterRecord> {
        val today = todayDate()
        return getRecords(context).filter { it.date == today }
    }

    fun todayTotal(context: Context): Int = todayRecords(context).sumOf { it.amountMl }

    fun totalsLastDays(context: Context, days: Int): List<DayTotal> {
        val recordsByDate = getRecords(context).groupBy { it.date }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
        return (0 until days).map {
            val date = dateFormat.format(calendar.time)
            val total = recordsByDate[date]?.sumOf { record -> record.amountMl } ?: 0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            DayTotal(date, total)
        }
    }

    fun reachedRate(points: List<DayTotal>, goalMl: Int): Int {
        if (points.isEmpty()) return 0
        return (points.count { it.totalMl >= goalMl } * 100f / points.size).roundToInt()
    }

    private fun saveRecords(context: Context, records: List<WaterRecord>) {
        val array = JSONArray()
        records.sortedBy { it.timestamp }.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("amountMl", record.amountMl)
                    .put("timestamp", record.timestamp)
                    .put("date", record.date)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECORDS, array.toString())
            .apply()
    }
}
