package com.example.waterreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterRecordDao {
    @Insert
    suspend fun insert(record: WaterRecord): Long

    @Delete
    suspend fun delete(record: WaterRecord)

    @Query("SELECT * FROM water_records WHERE date = :date ORDER BY timestamp DESC")
    fun observeRecordsForDate(date: String): Flow<List<WaterRecord>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_records WHERE date = :date")
    fun observeTotalForDate(date: String): Flow<Int>

    @Query(
        """
        SELECT date, COALESCE(SUM(amountMl), 0) AS totalMl
        FROM water_records
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
        """
    )
    fun observeTotalsBetween(startDate: String, endDate: String): Flow<List<DailyWaterTotal>>
}
