package com.example.waterreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WaterRecord::class],
    version = 1,
    exportSchema = false
)
abstract class WaterDatabase : RoomDatabase() {
    abstract fun waterRecordDao(): WaterRecordDao

    companion object {
        @Volatile
        private var instance: WaterDatabase? = null

        fun get(context: Context): WaterDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaterDatabase::class.java,
                    "water_records.db"
                ).build().also { instance = it }
            }
        }
    }
}
