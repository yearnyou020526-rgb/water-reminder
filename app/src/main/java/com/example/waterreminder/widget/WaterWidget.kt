package com.example.waterreminder.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceComposable
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.settings.SettingsDataStore
import kotlinx.coroutines.flow.first

class WaterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = WaterDatabase.get(context).waterRecordDao()
        val today = WaterRepository.todayDate()
        val total = dao.observeTotalForDate(today).first()
        val settings = SettingsDataStore(context).settingsFlow.first()
        val goal = settings.dailyGoalMl
        val progress = if (goal <= 0) 0f else (total.toFloat() / goal).coerceIn(0f, 1f)

        provideContent {
            WidgetContent(totalMl = total, goalMl = goal, progress = progress)
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            WaterWidget().updateAll(context)
        }
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}

class AddWaterWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repo = WaterRepository(WaterDatabase.get(context).waterRecordDao())
        repo.addWater(200)
        WaterWidget.updateAll(context)
    }
}

@Composable
@GlanceComposable
private fun WidgetContent(totalMl: Int, goalMl: Int, progress: Float) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Transparent))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "今日喝水",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = "$totalMl / $goalMl ml",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            androidx.glance.Button(
                text = "+200ml",
                onClick = actionRunCallback<AddWaterWidgetAction>()
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
                .background(ColorProvider(Color(0x55FFFFFF)))
        ) {
            Box(
                modifier = GlanceModifier
                    .width((220 * progress).dp)
                    .height(6.dp)
                    .background(ColorProvider(Color(0xFF40C4FF)))
            ) {}
        }
    }
}
