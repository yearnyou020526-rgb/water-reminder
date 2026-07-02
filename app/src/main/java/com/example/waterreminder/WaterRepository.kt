package com.example.waterreminder

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WaterRepository {
    private const val PREFS_NAME = "water_reminder"
    private const val KEY_TARGET_ML = "target_ml"
    private const val KEY_QUICK_AMOUNT_ML = "quick_amount_ml"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_START_MINUTES = "start_minutes"
    private const val KEY_END_MINUTES = "end_minutes"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_RECORDS = "records"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    fun addWater(context: Context, amountMl: Int, now: Long = System.currentTimeMillis()) {
        val amount = amountMl.coerceIn(1, 5000)
        val records = getAllRecords(context).toMutableSet()
        records.add("$now|$amount")
        prefs(context).edit().putStringSet(KEY_RECORDS, records).apply()
    }

    fun undoLastToday(context: Context): Boolean {
        val today = todayKey()
        val records = getAllRecords(context).toMutableList()
        val last = records
            .mapNotNull { parseRecord(it) }
            .filter { it.dateKey == today }
            .maxByOrNull { it.timestampMillis }
            ?: return false
        val removed = records.remove("${last.timestampMillis}|${last.amountMl}")
        if (removed) {
            prefs(context).edit().putStringSet(KEY_RECORDS, records.toSet()).apply()
        }
        return removed
    }

    fun getTodayTotal(context: Context): Int = getTotalForDate(context, todayKey())

    fun getTotalForDate(context: Context, dateKey: String): Int {
        return getRecordsForDate(context, dateKey).sumOf { it.amountMl }
    }

    fun getTodayRecords(context: Context): List<WaterRecord> {
        return getRecordsForDate(context, todayKey()).sortedByDescending { it.timestampMillis }
    }

    fun getRecordsForDate(context: Context, dateKey: String): List<WaterRecord> {
        return getAllRecords(context).mapNotNull { parseRecord(it) }.filter { it.dateKey == dateKey }
    }

    fun getRecentDays(context: Context, count: Int = 30): List<DayWaterTotal> {
        val cursor = Calendar.getInstance()
        return (0 until count).map {
            val dateKey = dateFormat.format(cursor.time)
            val total = getTotalForDate(context, dateKey)
            val result = DayWaterTotal(
                dateKey = dateKey,
                displayDate = displayDateFormat.format(cursor.time),
                totalMl = total,
                targetMl = getTargetMl(context)
            )
            cursor.add(Calendar.DAY_OF_YEAR, -1)
            result
        }
    }

    fun getStats(context: Context): WaterStats {
        val days7 = getRecentDays(context, 7)
        val days30 = getRecentDays(context, 30)
        val target = getTargetMl(context)
        val streak = days30.takeWhile { it.totalMl >= target }.size
        val average7 = if (days7.isEmpty()) 0 else days7.sumOf { it.totalMl } / days7.size
        val average30 = if (days30.isEmpty()) 0 else days30.sumOf { it.totalMl } / days30.size
        val reached30 = days30.count { it.totalMl >= target }
        val allRecords = getAllRecords(context).mapNotNull { parseRecord(it) }
        val totalMl = allRecords.sumOf { it.amountMl }
        val recordedDays = allRecords.map { it.dateKey }.distinct().size
        return WaterStats(streak, average7, average30, reached30, recordedDays, totalMl)
    }

    fun getTargetMl(context: Context): Int {
        return prefs(context).getInt(KEY_TARGET_ML, 2000).coerceIn(100, 10000)
    }

    fun setTargetMl(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_TARGET_ML, value.coerceIn(100, 10000)).apply()
    }

    fun getQuickAmountMl(context: Context): Int {
        return prefs(context).getInt(KEY_QUICK_AMOUNT_ML, 250).coerceIn(10, 2000)
    }

    fun setQuickAmountMl(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_QUICK_AMOUNT_ML, value.coerceIn(10, 2000)).apply()
    }

    fun getIntervalMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_INTERVAL_MINUTES, 60).coerceIn(15, 360)
    }

    fun setIntervalMinutes(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_MINUTES, value.coerceIn(15, 360)).apply()
    }

    fun getStartMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_START_MINUTES, 9 * 60).coerceIn(0, 23 * 60 + 59)
    }

    fun setStartMinutes(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_START_MINUTES, value.coerceIn(0, 23 * 60 + 59)).apply()
    }

    fun getEndMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_END_MINUTES, 22 * 60).coerceIn(0, 23 * 60 + 59)
    }

    fun setEndMinutes(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_END_MINUTES, value.coerceIn(0, 23 * 60 + 59)).apply()
    }

    fun isReminderEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDERS_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    fun progressPercent(context: Context): Int {
        val target = getTargetMl(context)
        if (target <= 0) return 0
        return ((getTodayTotal(context) * 100f) / target).toInt().coerceIn(0, 100)
    }

    fun todayKey(): String = dateFormat.format(Date())

    fun formatTime(minutesOfDay: Int): String {
        return "%02d:%02d".format(Locale.US, minutesOfDay / 60, minutesOfDay % 60)
    }

    fun minutesFrom(hour: Int, minute: Int): Int = (hour * 60 + minute).coerceIn(0, 23 * 60 + 59)

    private fun getAllRecords(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_RECORDS, emptySet()) ?: emptySet()
    }

    private fun parseRecord(raw: String): WaterRecord? {
        val parts = raw.split("|")
        if (parts.size != 2) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val amount = parts[1].toIntOrNull() ?: return null
        return WaterRecord(
            timestampMillis = timestamp,
            amountMl = amount,
            dateKey = dateFormat.format(Date(timestamp)),
            displayTime = timeFormat.format(Date(timestamp))
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

data class WaterRecord(
    val timestampMillis: Long,
    val amountMl: Int,
    val dateKey: String,
    val displayTime: String
)

data class DayWaterTotal(
    val dateKey: String,
    val displayDate: String,
    val totalMl: Int,
    val targetMl: Int
) {
    val reached: Boolean = totalMl >= targetMl
}

data class WaterStats(
    val streakDays: Int,
    val average7Ml: Int,
    val average30Ml: Int,
    val reachedDays30: Int,
    val recordedDays: Int,
    val totalMl: Int
)
