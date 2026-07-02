package com.example.waterreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_records")
data class WaterRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMl: Int,
    val timestamp: Long,
    val date: String
)

data class DailyWaterTotal(
    val date: String,
    val totalMl: Int
)
