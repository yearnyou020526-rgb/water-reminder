package com.example.waterreminder.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

class WaterRepository(
    private val dao: WaterRecordDao
) {
    fun observeTodayRecords(): Flow<List<WaterRecord>> = observeRecordsForDate(todayDate())

    fun observeTodayTotal(): Flow<Int> = dao.observeTotalForDate(todayDate())

    fun observeRecordsForDate(date: String): Flow<List<WaterRecord>> {
        return dao.observeRecordsForDate(date)
    }

    fun observeTotalsBetween(startDate: String, endDate: String): Flow<List<DailyWaterTotal>> {
        return dao.observeTotalsBetween(startDate, endDate)
    }

    suspend fun addWater(amountMl: Int) {
        val now = System.currentTimeMillis()
        dao.insert(
            WaterRecord(
                amountMl = amountMl.coerceIn(1, 5000),
                timestamp = now,
                date = dateForTimestamp(now)
            )
        )
    }

    suspend fun deleteRecord(record: WaterRecord) {
        dao.delete(record)
    }

    companion object {
        private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun todayDate(): String = LocalDate.now().format(formatter)

        fun dateForTimestamp(timestamp: Long): String {
            return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(formatter)
        }

        fun dateDaysAgo(daysAgo: Long): String {
            return LocalDate.now().minusDays(daysAgo).format(formatter)
        }
    }
}
