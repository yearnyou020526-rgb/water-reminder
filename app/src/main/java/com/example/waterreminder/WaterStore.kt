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
    val weekdayGoalsMl: List<Int> = List(7) { 2000 },
    val defaultDrinkMl: Int = 500,
    val quickAmountsMl: List<Int> = listOf(100, 200, 300, 500),
    val reminderIntervalMinutes: Int = 60,
    val reminderStartHour: Int = 8,
    val reminderStartMinute: Int = 0,
    val reminderEndHour: Int = 23,
    val reminderEndMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val mutedDate: String = "",
    val widgetLargeText: Boolean = true,
    val widgetShowCup: Boolean = true
)

data class DayTotal(val date: String, val totalMl: Int)

object WaterStore {
    private const val PREFS = "water_store"
    private const val KEY_RECORDS = "records"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_GOAL = "daily_goal_ml"
    private const val KEY_WEEKDAY_GOAL_PREFIX = "weekday_goal_"
    private const val KEY_DEFAULT = "default_drink_ml"
    private const val KEY_QUICK_PREFIX = "quick_amount_"
    private const val KEY_INTERVAL = "reminder_interval"
    private const val KEY_START_HOUR = "start_hour"
    private const val KEY_START_MINUTE = "start_minute"
    private const val KEY_END_HOUR = "end_hour"
    private const val KEY_END_MINUTE = "end_minute"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_MUTED_DATE = "muted_date"
    private const val KEY_WIDGET_LARGE_TEXT = "widget_large_text"
    private const val KEY_WIDGET_SHOW_CUP = "widget_show_cup"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    fun todayDate(): String = dateFormat.format(Date())

    fun shortDate(date: String): String = runCatching {
        shortDateFormat.format(dateFormat.parse(date) ?: Date())
    }.getOrDefault(date.takeLast(5))

    fun getSettings(context: Context): WaterSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaultGoal = prefs.getInt(KEY_GOAL, 2000)
        return WaterSettings(
            dailyGoalMl = defaultGoal,
            weekdayGoalsMl = (0..6).map { index ->
                prefs.getInt("$KEY_WEEKDAY_GOAL_PREFIX$index", defaultGoal)
            },
            defaultDrinkMl = prefs.getInt(KEY_DEFAULT, 500),
            quickAmountsMl = (0..3).map { index ->
                prefs.getInt("$KEY_QUICK_PREFIX$index", listOf(100, 200, 300, 500)[index])
            },
            reminderIntervalMinutes = prefs.getInt(KEY_INTERVAL, 60),
            reminderStartHour = prefs.getInt(KEY_START_HOUR, 8),
            reminderStartMinute = prefs.getInt(KEY_START_MINUTE, 0),
            reminderEndHour = prefs.getInt(KEY_END_HOUR, 23),
            reminderEndMinute = prefs.getInt(KEY_END_MINUTE, 0),
            reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, true),
            mutedDate = prefs.getString(KEY_MUTED_DATE, "") ?: "",
            widgetLargeText = prefs.getBoolean(KEY_WIDGET_LARGE_TEXT, true),
            widgetShowCup = prefs.getBoolean(KEY_WIDGET_SHOW_CUP, true)
        )
    }

    fun updateSettings(context: Context, update: WaterSettings) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_GOAL, update.dailyGoalMl.coerceIn(100, 10000))
            .putInt(KEY_DEFAULT, update.defaultDrinkMl.coerceIn(10, 5000))
            .putInt(KEY_INTERVAL, update.reminderIntervalMinutes)
            .putInt(KEY_START_HOUR, update.reminderStartHour.coerceIn(0, 23))
            .putInt(KEY_START_MINUTE, update.reminderStartMinute.coerceIn(0, 59))
            .putInt(KEY_END_HOUR, update.reminderEndHour.coerceIn(0, 23))
            .putInt(KEY_END_MINUTE, update.reminderEndMinute.coerceIn(0, 59))
            .putBoolean(KEY_REMINDER_ENABLED, update.reminderEnabled)
            .putString(KEY_MUTED_DATE, update.mutedDate)
            .putBoolean(KEY_WIDGET_LARGE_TEXT, update.widgetLargeText)
            .putBoolean(KEY_WIDGET_SHOW_CUP, update.widgetShowCup)
        update.weekdayGoalsMl.take(7).forEachIndexed { index, goal ->
            editor.putInt("$KEY_WEEKDAY_GOAL_PREFIX$index", goal.coerceIn(100, 10000))
        }
        update.quickAmountsMl.take(4).forEachIndexed { index, amount ->
            editor.putInt("$KEY_QUICK_PREFIX$index", amount.coerceIn(10, 5000))
        }
        editor.apply()
        WaterReminderScheduler.scheduleNext(context)
        WaterReminderScheduler.scheduleMidnightRefresh(context)
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
        cleanupOldDetails(context, records)
        saveRecords(context, records)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        muteIfGoalReached(context)
        WaterWidgetProvider.updateAll(context)
    }

    fun deleteRecord(context: Context, id: Long) {
        saveRecords(context, getRecords(context).filterNot { it.id == id })
        WaterWidgetProvider.updateAll(context)
    }

    fun undoLastTodayRecord(context: Context): Boolean {
        val last = todayRecords(context).maxByOrNull { it.timestamp } ?: return false
        deleteRecord(context, last.id)
        return true
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

    fun todayGoal(context: Context): Int = goalForDate(getSettings(context), todayDate())

    fun overGoalMl(context: Context): Int {
        return (todayTotal(context) - todayGoal(context)).coerceAtLeast(0)
    }

    fun lastDrinkTimestamp(context: Context): Long? {
        return todayRecords(context).maxByOrNull { it.timestamp }?.timestamp
    }

    fun wasWaterAddedRecently(context: Context, minutes: Int): Boolean {
        val last = lastDrinkTimestamp(context) ?: return false
        return System.currentTimeMillis() - last < minutes.coerceAtLeast(1) * 60 * 1000L
    }

    fun completionTimestampToday(context: Context): Long? {
        val goal = todayGoal(context)
        if (goal <= 0) return null
        var total = 0
        todayRecords(context).sortedBy { it.timestamp }.forEach { record ->
            total += record.amountMl
            if (total >= goal) return record.timestamp
        }
        return null
    }

    fun paceHint(context: Context): String {
        val settings = getSettings(context)
        val goal = todayGoal(context)
        val total = todayTotal(context)
        if (goal <= 0) return ""
        if (total >= goal) {
            val over = (total - goal).coerceAtLeast(0)
            return if (over > 0) "已达标，并超出目标 ${over} ml" else "已达标，今天不再发送提醒"
        }

        val start = settings.reminderStartHour * 60 + settings.reminderStartMinute
        val end = settings.reminderEndHour * 60 + settings.reminderEndMinute
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val span = reminderWindowLength(start, end)
        val elapsed = elapsedReminderMinutes(start, end, current)

        if (elapsed == null) {
            val remaining = goal - total
            return if (currentBeforeWindow(start, end, current)) {
                "还没到提醒时间，今天还差 ${remaining} ml"
            } else {
                "今天进度偏慢，还差 ${remaining} ml 达标"
            }
        }

        val expected = (goal * elapsed / span.toFloat()).roundToInt()
        val deficit = expected - total
        return if (deficit >= 200) {
            "当前进度偏慢，建议补充 ${deficit} ml"
        } else {
            "当前进度正常，继续保持"
        }
    }

    fun goalForDate(settings: WaterSettings, date: String): Int {
        val index = weekdayIndex(date)
        return settings.weekdayGoalsMl.getOrNull(index) ?: settings.dailyGoalMl
    }

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

    fun reachedRate(points: List<DayTotal>, settings: WaterSettings): Int {
        if (points.isEmpty()) return 0
        return (points.count { it.totalMl >= goalForDate(settings, it.date) } * 100f / points.size).roundToInt()
    }

    fun bestDay(points: List<DayTotal>): DayTotal? = points.maxByOrNull { it.totalMl }

    fun reachedStreak(points: List<DayTotal>, settings: WaterSettings): Int {
        var streak = 0
        for (point in points.asReversed()) {
            if (point.totalMl >= goalForDate(settings, point.date)) streak++ else break
        }
        return streak
    }

    fun missedStreak(points: List<DayTotal>, settings: WaterSettings): Int {
        var streak = 0
        for (point in points.asReversed()) {
            if (point.totalMl < goalForDate(settings, point.date)) streak++ else break
        }
        return streak
    }

    fun timeDistributionToday(context: Context): Triple<Int, Int, Int> {
        var morning = 0
        var afternoon = 0
        var evening = 0
        todayRecords(context).forEach { record ->
            val hour = Calendar.getInstance().apply {
                timeInMillis = record.timestamp
            }.get(Calendar.HOUR_OF_DAY)
            when {
                hour < 12 -> morning += record.amountMl
                hour < 18 -> afternoon += record.amountMl
                else -> evening += record.amountMl
            }
        }
        return Triple(morning, afternoon, evening)
    }

    fun recordedDays(context: Context): Int = getRecords(context).map { it.date }.distinct().size

    fun recordCount(context: Context): Int = getRecords(context).size

    fun isGoalReached(context: Context): Boolean {
        val goal = todayGoal(context)
        return goal > 0 && todayTotal(context) >= goal
    }

    fun muteIfGoalReached(context: Context) {
        if (!isGoalReached(context)) return
        val settings = getSettings(context)
        if (settings.mutedDate != todayDate()) {
            updateSettings(context, settings.copy(mutedDate = todayDate()))
        } else {
            WaterReminderScheduler.cancelReminderOnly(context)
        }
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

    private fun weekdayIndex(date: String): Int {
        val parsed = runCatching { dateFormat.parse(date) }.getOrNull() ?: return weekdayIndex(Calendar.getInstance())
        return weekdayIndex(Calendar.getInstance().apply { time = parsed })
    }

    private fun weekdayIndex(calendar: Calendar): Int {
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    private fun reminderWindowLength(start: Int, end: Int): Int {
        val raw = if (start <= end) end - start else 24 * 60 - start + end
        return raw.coerceAtLeast(1)
    }

    private fun elapsedReminderMinutes(start: Int, end: Int, current: Int): Int? {
        return if (start <= end) {
            if (current in start..end) current - start else null
        } else {
            when {
                current >= start -> current - start
                current <= end -> 24 * 60 - start + current
                else -> null
            }
        }
    }

    private fun currentBeforeWindow(start: Int, end: Int, current: Int): Boolean {
        return if (start <= end) current < start else current > end && current < start
    }

    private fun cleanupOldDetails(context: Context, records: MutableList<WaterRecord>) {
        val cutoff = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -180)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val oldGroups = records.filter { it.timestamp < cutoff }.groupBy { it.date }
        if (oldGroups.isEmpty()) return

        records.removeAll { it.timestamp < cutoff }
        oldGroups.forEach { (date, oldRecords) ->
            records.add(
                WaterRecord(
                    id = oldRecords.minOf { it.id },
                    amountMl = oldRecords.sumOf { it.amountMl },
                    timestamp = middayTimestamp(date),
                    date = date
                )
            )
        }
    }

    private fun middayTimestamp(date: String): Long {
        val parsed = runCatching { dateFormat.parse(date) }.getOrNull() ?: return System.currentTimeMillis()
        return Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
