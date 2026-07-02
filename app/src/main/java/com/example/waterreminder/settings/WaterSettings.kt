package com.example.waterreminder.settings

data class WaterSettings(
    val dailyGoalMl: Int = 2000,
    val defaultDrinkMl: Int = 200,
    val reminderIntervalMinutes: Int = 60,
    val reminderStartHour: Int = 8,
    val reminderStartMinute: Int = 0,
    val reminderEndHour: Int = 23,
    val reminderEndMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val mutedDate: String = ""
)
