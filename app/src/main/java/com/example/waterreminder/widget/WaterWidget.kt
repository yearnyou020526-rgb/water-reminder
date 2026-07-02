package com.example.waterreminder.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.waterreminder.R
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
        val quickAmountMl = settings.defaultDrinkMl
        val progress = if (goal <= 0) 0f else (total.toFloat() / goal).coerceIn(0f, 1f)

        provideContent {
            WidgetContent(
                totalMl = total,
                goalMl = goal,
                quickAmountMl = quickAmountMl,
                progress = progress
            )
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
        val settings = SettingsDataStore(context).settingsFlow.first()
        val repo = WaterRepository(WaterDatabase.get(context).waterRecordDao())
        repo.addWater(settings.defaultDrinkMl)
        WaterWidget.updateAll(context)
    }
}

@Composable
private fun WidgetContent(totalMl: Int, goalMl: Int, quickAmountMl: Int, progress: Float) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Transparent))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_cup),
            contentDescription = "水杯",
            modifier = GlanceModifier.size(72.dp)
        )
        Spacer(modifier = GlanceModifier.width(16.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今天喝水目标",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8C8C8C)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "${totalMl}/${goalMl}ml",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8C8C8C)),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(ColorProvider(Color(0x22888888)))
            ) {
                Box(
                    modifier = GlanceModifier
                        .width((180 * progress).dp)
                        .height(5.dp)
                        .background(ColorProvider(Color(0x66888888)))
                ) {}
            }
        }
        Spacer(modifier = GlanceModifier.width(20.dp))
        Box(
            modifier = GlanceModifier
                .width(132.dp)
                .height(54.dp)
                .background(ColorProvider(Color(0x11FFFFFF))),
            contentAlignment = Alignment.Center
        ) {
            androidx.glance.Button(
                text = "+${quickAmountMl}ml",
                onClick = actionRunCallback<AddWaterWidgetAction>()
            )
        }
    }
}
