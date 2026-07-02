package com.example.waterreminder.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.waterreminder.data.DailyWaterTotal
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.reminder.ReminderScheduler
import com.example.waterreminder.settings.SettingsDataStore
import com.example.waterreminder.settings.WaterSettings
import com.example.waterreminder.widget.WaterWidget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WaterViewModel(
    private val appContext: Context,
    private val repository: WaterRepository,
    private val settingsStore: SettingsDataStore
) : ViewModel() {
    private val todayTotals = repository.observeTodayTotal()
    private val todayRecords = repository.observeTodayRecords()
    private val totals30 = repository.observeTotalsBetween(
        WaterRepository.dateDaysAgo(29),
        WaterRepository.todayDate()
    )

    val uiState: StateFlow<WaterUiState> = combine(
        settingsStore.settingsFlow,
        todayTotals,
        todayRecords,
        totals30
    ) { settings, todayTotal, records, rawTotals30 ->
        val trend30 = buildTrend(rawTotals30, 30)
        val trend7 = trend30.takeLast(7)
        WaterUiState(
            settings = settings,
            todayTotalMl = todayTotal,
            todayRecords = records,
            trend7 = trend7,
            trend30 = trend30,
            statistics7 = trend7.toStatistics(settings.dailyGoalMl),
            statistics30 = trend30.toStatistics(settings.dailyGoalMl)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WaterUiState()
    )

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                ReminderScheduler.scheduleNext(appContext, settings)
                WaterWidget.updateAll(appContext)
            }
        }
    }

    fun addWater(amountMl: Int) {
        viewModelScope.launch {
            repository.addWater(amountMl)
            WaterWidget.updateAll(appContext)
        }
    }

    fun deleteRecord(record: WaterRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
            WaterWidget.updateAll(appContext)
        }
    }

    fun updateDailyGoal(value: Int) {
        viewModelScope.launch { settingsStore.updateDailyGoal(value) }
    }

    fun updateDefaultDrink(value: Int) {
        viewModelScope.launch { settingsStore.updateDefaultDrink(value) }
    }

    fun updateReminderInterval(value: Int) {
        viewModelScope.launch { settingsStore.updateReminderInterval(value) }
    }

    fun updateReminderStart(hour: Int, minute: Int) {
        viewModelScope.launch { settingsStore.updateReminderStart(hour, minute) }
    }

    fun updateReminderEnd(hour: Int, minute: Int) {
        viewModelScope.launch { settingsStore.updateReminderEnd(hour, minute) }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateReminderEnabled(enabled)
            if (!enabled) ReminderScheduler.cancelAll(appContext)
        }
    }

    private fun buildTrend(rawTotals: List<DailyWaterTotal>, days: Int): List<TrendPoint> {
        val byDate = rawTotals.associateBy { it.date }
        return (days - 1 downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong())
            val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            TrendPoint(date = key, totalMl = byDate[key]?.totalMl ?: 0)
        }
    }

    companion object {
        fun factory(
            context: Context,
            repository: WaterRepository,
            settingsStore: SettingsDataStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WaterViewModel(context.applicationContext, repository, settingsStore) as T
            }
        }
    }
}

data class WaterUiState(
    val settings: WaterSettings = WaterSettings(),
    val todayTotalMl: Int = 0,
    val todayRecords: List<WaterRecord> = emptyList(),
    val trend7: List<TrendPoint> = emptyList(),
    val trend30: List<TrendPoint> = emptyList(),
    val statistics7: StatisticsSummary = StatisticsSummary(),
    val statistics30: StatisticsSummary = StatisticsSummary()
) {
    val progress: Float
        get() = if (settings.dailyGoalMl <= 0) 0f
        else (todayTotalMl.toFloat() / settings.dailyGoalMl).coerceIn(0f, 1f)
}

data class TrendPoint(
    val date: String,
    val totalMl: Int
) {
    val shortDate: String = date.substring(5)
}

data class StatisticsSummary(
    val averageMl: Int = 0,
    val reachedDays: Int = 0,
    val reachedRate: Int = 0
)

private fun List<TrendPoint>.toStatistics(goalMl: Int): StatisticsSummary {
    if (isEmpty()) return StatisticsSummary()
    val reached = count { it.totalMl >= goalMl }
    return StatisticsSummary(
        averageMl = sumOf { it.totalMl } / size,
        reachedDays = reached,
        reachedRate = (reached * 100) / size
    )
}
