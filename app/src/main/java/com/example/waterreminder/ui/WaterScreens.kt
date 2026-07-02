@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waterreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.settings.WaterSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WaterApp(viewModel: WaterViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == AppTab.Home,
                    onClick = { tab = AppTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.Statistics,
                    onClick = { tab = AppTab.Statistics },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("统计") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.Settings,
                    onClick = { tab = AppTab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5FAFD))
                .padding(padding)
        ) {
            when (tab) {
                AppTab.Home -> MainScreen(
                    state = state,
                    onAddWater = viewModel::addWater,
                    onDeleteRecord = viewModel::deleteRecord
                )

                AppTab.Statistics -> StatisticsScreen(state = state)
                AppTab.Settings -> SettingsScreen(
                    state = state,
                    onGoalChange = viewModel::updateDailyGoal,
                    onDefaultDrinkChange = viewModel::updateDefaultDrink,
                    onIntervalChange = viewModel::updateReminderInterval,
                    onStartChange = viewModel::updateReminderStart,
                    onEndChange = viewModel::updateReminderEnd,
                    onReminderEnabledChange = viewModel::updateReminderEnabled
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    state: WaterUiState,
    onAddWater: (Int) -> Unit,
    onDeleteRecord: (WaterRecord) -> Unit
) {
    var customDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "喝水记录",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            SummaryCard(state)
        }
        item {
            QuickButtons(onAddWater = onAddWater, onCustom = { customDialog = true })
        }
        item {
            SectionTitle("今日记录")
        }
        if (state.todayRecords.isEmpty()) {
            item { EmptyCard("今天还没有记录") }
        } else {
            items(state.todayRecords, key = { it.id }) { record ->
                RecordRow(record = record, onDelete = { onDeleteRecord(record) })
            }
        }
    }

    if (customDialog) {
        NumberInputDialog(
            title = "自定义饮水量",
            initialValue = state.settings.defaultDrinkMl,
            suffix = "ml",
            onDismiss = { customDialog = false },
            onConfirm = {
                onAddWater(it)
                customDialog = false
            }
        )
    }
}

@Composable
private fun SummaryCard(state: WaterUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("今日饮水", color = Color(0xFF667085))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${state.todayTotalMl} / ${state.settings.dailyGoalMl} ml",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF0B4F7A),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = Color(0xFF1378B8),
                trackColor = Color(0xFFD9EEF8)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "完成 ${(state.progress * 100).toInt()}%",
                color = Color(0xFF667085)
            )
        }
    }
}

@Composable
private fun QuickButtons(onAddWater: (Int) -> Unit, onCustom: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 200, 300).forEach { amount ->
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onAddWater(amount) }
                    ) {
                        Text("+${amount}ml")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onAddWater(500) }
                ) {
                    Text("+500ml")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCustom
                ) {
                    Text("自定义")
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: WaterRecord, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("+${record.amountMl} ml", fontWeight = FontWeight.SemiBold)
                Text(
                    formatter.format(Date(record.timestamp)),
                    color = Color(0xFF667085),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
fun StatisticsScreen(state: WaterUiState) {
    var range by rememberSaveable { mutableStateOf(7) }
    val points = if (range == 7) state.trend7 else state.trend30
    val stats = if (range == 7) state.statistics7 else state.statistics30

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("统计", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = range == 7, onClick = { range = 7 }, label = { Text("最近 7 天") })
            FilterChip(selected = range == 30, onClick = { range = 30 }, label = { Text("最近 30 天") })
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatText("平均每日饮水量", "${stats.averageMl} ml")
                StatText("达标天数", "${stats.reachedDays} 天")
                StatText("达标率", "${stats.reachedRate}%")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                points.forEach { point ->
                    TrendRow(point = point, goalMl = state.settings.dailyGoalMl)
                }
            }
        }
    }
}

@Composable
private fun TrendRow(point: TrendPoint, goalMl: Int) {
    val progress = if (goalMl <= 0) 0f else (point.totalMl.toFloat() / goalMl).coerceIn(0f, 1f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(point.shortDate, modifier = Modifier.width(58.dp), color = Color(0xFF667085))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(Color(0xFFE6F3FA), RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .background(Color(0xFF1378B8), RoundedCornerShape(5.dp))
                )
            }
            Text(
                text = "${point.totalMl}ml",
                modifier = Modifier.width(74.dp),
                color = Color(0xFF344054)
            )
        }
    }
}

@Composable
fun SettingsScreen(
    state: WaterUiState,
    onGoalChange: (Int) -> Unit,
    onDefaultDrinkChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartChange: (Int, Int) -> Unit,
    onEndChange: (Int, Int) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit
) {
    val settings = state.settings
    var numberDialog by remember { mutableStateOf<NumberDialogConfig?>(null) }
    var timeDialog by remember { mutableStateOf<TimeDialogConfig?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow("每日饮水目标", "${settings.dailyGoalMl} ml") {
                    numberDialog = NumberDialogConfig("每日饮水目标", settings.dailyGoalMl, onGoalChange)
                }
                SettingRow("默认饮水量", "${settings.defaultDrinkMl} ml") {
                    numberDialog = NumberDialogConfig("默认饮水量", settings.defaultDrinkMl, onDefaultDrinkChange)
                }
                Text(
                    text = "通知快捷操作和桌面小组件按钮会使用这个数值。",
                    color = Color(0xFF667085),
                    style = MaterialTheme.typography.bodySmall
                )
                Text("提醒间隔", fontWeight = FontWeight.SemiBold)
                listOf(listOf(15, 30, 45), listOf(60, 90, 120)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            FilterChip(
                                selected = settings.reminderIntervalMinutes == minutes,
                                onClick = { onIntervalChange(minutes) },
                                label = { Text("${minutes} 分钟") }
                            )
                        }
                    }
                }
                SettingRow("提醒开始", formatTime(settings.reminderStartHour, settings.reminderStartMinute)) {
                    timeDialog = TimeDialogConfig(
                        "提醒开始",
                        settings.reminderStartHour,
                        settings.reminderStartMinute,
                        onStartChange
                    )
                }
                SettingRow("提醒结束", formatTime(settings.reminderEndHour, settings.reminderEndMinute)) {
                    timeDialog = TimeDialogConfig(
                        "提醒结束",
                        settings.reminderEndHour,
                        settings.reminderEndMinute,
                        onEndChange
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通知提醒", fontWeight = FontWeight.SemiBold)
                        Text("在设定时间段内提醒喝水", color = Color(0xFF667085))
                    }
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = onReminderEnabledChange
                    )
                }
            }
        }
    }

    numberDialog?.let { config ->
        NumberInputDialog(
            title = config.title,
            initialValue = config.initialValue,
            suffix = "ml",
            onDismiss = { numberDialog = null },
            onConfirm = {
                config.onConfirm(it)
                numberDialog = null
            }
        )
    }
    timeDialog?.let { config ->
        TimeInputDialog(
            config = config,
            onDismiss = { timeDialog = null },
            onConfirm = { hour, minute ->
                config.onConfirm(hour, minute)
                timeDialog = null
            }
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = Color(0xFF667085))
        }
        TextButton(onClick = onClick) { Text("修改") }
    }
}

@Composable
private fun StatText(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = Color(0xFF667085))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            color = Color(0xFF667085)
        )
    }
}

@Composable
fun NumberInputDialog(
    title: String,
    initialValue: Int,
    suffix: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                suffix = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toIntOrNull()?.let(onConfirm)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun TimeInputDialog(
    config: TimeDialogConfig,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hour by rememberSaveable { mutableStateOf(config.hour.toString().padStart(2, '0')) }
    var minute by rememberSaveable { mutableStateOf(config.minute.toString().padStart(2, '0')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                    label = { Text("时") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minute,
                    onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                    label = { Text("分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    onConfirm(h, m)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private enum class AppTab {
    Home,
    Statistics,
    Settings
}

private data class NumberDialogConfig(
    val title: String,
    val initialValue: Int,
    val onConfirm: (Int) -> Unit
)

private data class TimeDialogConfig(
    val title: String,
    val hour: Int,
    val minute: Int,
    val onConfirm: (Int, Int) -> Unit
)

private fun formatTime(hour: Int, minute: Int): String {
    return "%02d:%02d".format(hour, minute)
}
