package com.example.waterreminder

import android.content.Context
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.settings.SettingsDataStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = WaterDatabase.get(appContext)

    val waterRepository = WaterRepository(database.waterRecordDao())
    val settingsDataStore = SettingsDataStore(appContext)
}
