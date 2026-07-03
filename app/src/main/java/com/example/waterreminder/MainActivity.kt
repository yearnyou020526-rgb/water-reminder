package com.example.waterreminder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private var currentTab = Tab.Home
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        WaterReminderScheduler.scheduleNext(this)
        WaterReminderScheduler.scheduleMidnightRefresh(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) renderTab()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 250, 253))
        }
        root.addView(tabBar())
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderTab()
    }

    private fun tabBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        listOf(Tab.Home, Tab.Stats, Tab.Settings).forEach { tab ->
            row.addView(Button(this).apply {
                text = tab.title
                isAllCaps = false
                setOnClickListener {
                    currentTab = tab
                    renderTab()
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        return row
    }

    private fun renderTab() {
        content.removeAllViews()
        when (currentTab) {
            Tab.Home -> renderHome()
            Tab.Stats -> renderStats()
            Tab.Settings -> renderSettings()
        }
    }

    private fun renderHome() {
        val settings = WaterStore.getSettings(this)
        val total = WaterStore.todayTotal(this)
        val goal = WaterStore.todayGoal(this)
        val progress = if (goal <= 0) 0 else (total * 100 / goal).coerceIn(0, 100)
        val overGoal = WaterStore.overGoalMl(this)
        val completionTime = WaterStore.completionTimestampToday(this)
        val paceHint = WaterStore.paceHint(this)

        content.addView(title("喝水记录"))
        content.addView(card {
            addView(label("今日饮水", 14, Color.rgb(80, 96, 112)))
            addView(label("${total} / ${goal} ml", 34, Color.rgb(8, 81, 125), true))
            addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = progress
            }, LinearLayout.LayoutParams(-1, dp(14)).apply { setMargins(0, dp(12), 0, dp(6)) })
            addView(label("完成 $progress%", 14, Color.rgb(80, 96, 112)))
            if (completionTime != null) {
                addView(label("今天 ${timeFormat.format(Date(completionTime))} 达成目标", 15, Color.rgb(20, 128, 88), true))
            }
            if (overGoal > 0) {
                addView(label("已超出目标 ${overGoal} ml，多喝的水已继续记录", 15, Color.rgb(20, 128, 88), true))
            } else if (progress >= 100) {
                addView(label("已达标，今天不再发送提醒", 15, Color.rgb(20, 128, 88), true))
            } else if (paceHint.isNotBlank()) {
                val color = if (paceHint.contains("偏慢")) Color.rgb(190, 90, 45) else Color.rgb(64, 118, 96)
                addView(label(paceHint, 15, color, true))
            }
        })

        content.addView(card {
            val quick = settings.quickAmountsMl.take(4)
            addView(rowOfButtons(quick.getOrElse(0) { 100 }, quick.getOrElse(1) { 200 }))
            addView(rowOfButtons(quick.getOrElse(2) { 300 }, quick.getOrElse(3) { 500 }, -1))
            addView(Button(this@MainActivity).apply {
                text = "撤销上一次"
                isAllCaps = false
                setOnClickListener {
                    WaterStore.undoLastTodayRecord(this@MainActivity)
                    renderTab()
                }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply {
                setMargins(dp(4), dp(6), dp(4), 0)
            })
        })

        val missed = WaterStore.missedStreak(WaterStore.totalsLastDays(this, 8).dropLast(1), settings)
        if (missed >= 3) {
            content.addView(card {
                addView(label("已经连续 ${missed} 天未达标，今天可以早点开始喝水。", 15, Color.rgb(180, 80, 40), true))
            })
        }

        content.addView(section("今日记录"))
        val records = WaterStore.todayRecords(this)
        if (records.isEmpty()) {
            content.addView(card { addView(label("今天还没有记录", 16, Color.rgb(80, 96, 112))) })
        } else {
            records.forEach { record ->
                content.addView(recordRow(record))
            }
        }
    }

    private fun rowOfButtons(vararg amounts: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            amounts.forEach { amount ->
                addView(Button(this@MainActivity).apply {
                    text = if (amount > 0) "+${amount}ml" else "自定义"
                    isAllCaps = false
                    setOnClickListener {
                        if (amount > 0) addWater(amount) else showNumberDialog("自定义饮水量", WaterStore.getSettings(this@MainActivity).defaultDrinkMl) {
                            addWater(it)
                        }
                    }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
        }
    }

    private fun recordRow(record: WaterRecord): View {
        return card {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val textBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("+${record.amountMl} ml", 18, Color.rgb(30, 42, 56), true))
                addView(label(timeFormat.format(Date(record.timestamp)), 13, Color.rgb(100, 116, 132)))
            }
            addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@MainActivity).apply {
                text = "删除"
                isAllCaps = false
                setOnClickListener {
                    WaterStore.deleteRecord(this@MainActivity, record.id)
                    renderTab()
                }
            }, LinearLayout.LayoutParams(dp(78), dp(42)))
        }
    }

    private fun renderStats() {
        val settings = WaterStore.getSettings(this)
        content.addView(title("统计"))
        content.addView(distributionCard())
        content.addView(statsCard("最近 7 天", WaterStore.totalsLastDays(this, 7), settings))
        content.addView(statsCard("最近 30 天", WaterStore.totalsLastDays(this, 30), settings))
    }

    private fun distributionCard(): View {
        val (morning, afternoon, evening) = WaterStore.timeDistributionToday(this)
        return card {
            addView(section("今天喝水时间分布"))
            addView(label("上午：${morning} ml", 15, Color.rgb(50, 64, 78)))
            addView(label("下午：${afternoon} ml", 15, Color.rgb(50, 64, 78)))
            addView(label("晚上：${evening} ml", 15, Color.rgb(50, 64, 78)))
        }
    }

    private fun statsCard(title: String, points: List<DayTotal>, settings: WaterSettings): View {
        return card {
            addView(section(title))
            val average = if (points.isEmpty()) 0 else points.sumOf { it.totalMl } / points.size
            val reached = points.count { it.totalMl >= WaterStore.goalForDate(settings, it.date) }
            val best = WaterStore.bestDay(points)
            val streak = WaterStore.reachedStreak(points, settings)
            addView(label("平均每日饮水量：${average} ml", 15, Color.rgb(50, 64, 78)))
            addView(label("达标天数：${reached} 天", 15, Color.rgb(50, 64, 78)))
            addView(label("达标率：${WaterStore.reachedRate(points, settings)}%", 15, Color.rgb(50, 64, 78)))
            if (best != null) {
                addView(label("最高一天：${WaterStore.shortDate(best.date)} ${best.totalMl}ml", 15, Color.rgb(50, 64, 78)))
            }
            addView(label("连续达标：${streak} 天", 15, Color.rgb(50, 64, 78)))
            points.forEach { point ->
                val goal = WaterStore.goalForDate(settings, point.date)
                val progress = if (goal <= 0) 0 else (point.totalMl * 100 / goal).coerceIn(0, 100)
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(WaterStore.shortDate(point.date), 12, Color.rgb(80, 96, 112)), LinearLayout.LayoutParams(dp(48), -2))
                    addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        this.progress = progress
                    }, LinearLayout.LayoutParams(0, dp(10), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
                    addView(label("${point.totalMl}ml", 12, Color.rgb(80, 96, 112)), LinearLayout.LayoutParams(dp(70), -2))
                }
                addView(row, LinearLayout.LayoutParams(-1, dp(28)))
            }
        }
    }

    private fun renderSettings() {
        val settings = WaterStore.getSettings(this)
        content.addView(title("设置"))
        content.addView(card {
            addView(settingRow("默认每日目标", "${settings.dailyGoalMl} ml") {
                showNumberDialog("默认每日目标", settings.dailyGoalMl) {
                    saveSettings(settings.copy(dailyGoalMl = it, weekdayGoalsMl = List(7) { _ -> it }))
                }
            })
            addView(section("按星期设置目标"))
            weekdayNames.forEachIndexed { index, name ->
                addView(settingRow(name, "${settings.weekdayGoalsMl.getOrElse(index) { settings.dailyGoalMl }} ml") {
                    showNumberDialog("${name}目标", settings.weekdayGoalsMl.getOrElse(index) { settings.dailyGoalMl }) {
                        val goals = settings.weekdayGoalsMl.toMutableList()
                        while (goals.size < 7) goals.add(settings.dailyGoalMl)
                        goals[index] = it
                        saveSettings(settings.copy(weekdayGoalsMl = goals))
                    }
                })
            }
            addView(settingRow("默认饮水量", "${settings.defaultDrinkMl} ml") {
                showNumberDialog("默认饮水量", settings.defaultDrinkMl) { saveSettings(settings.copy(defaultDrinkMl = it)) }
            })
            addView(label("小组件右侧按钮会使用默认饮水量。", 13, Color.rgb(96, 108, 120)))
            addView(section("首页快捷按钮"))
            settings.quickAmountsMl.take(4).forEachIndexed { index, amount ->
                addView(settingRow("快捷 ${index + 1}", "${amount} ml") {
                    showNumberDialog("快捷 ${index + 1}", amount) {
                        val quick = settings.quickAmountsMl.toMutableList()
                        while (quick.size < 4) quick.add(listOf(100, 200, 300, 500)[quick.size])
                        quick[index] = it
                        saveSettings(settings.copy(quickAmountsMl = quick))
                    }
                })
            }
            addView(section("提醒间隔"))
            listOf(15, 30, 45, 60, 90, 120).chunked(3).forEach { row ->
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    row.forEach { minutes ->
                        addView(Button(this@MainActivity).apply {
                            text = "${minutes}分钟"
                            isAllCaps = false
                            isSelected = settings.reminderIntervalMinutes == minutes
                            setOnClickListener { saveSettings(settings.copy(reminderIntervalMinutes = minutes)) }
                        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(4))
                        })
                    }
                })
            }
            addView(settingRow("提醒开始", "%02d:%02d".format(settings.reminderStartHour, settings.reminderStartMinute)) {
                showTimeDialog(settings.reminderStartHour, settings.reminderStartMinute) { hour, minute ->
                    saveSettings(settings.copy(reminderStartHour = hour, reminderStartMinute = minute))
                }
            })
            addView(settingRow("提醒结束", "%02d:%02d".format(settings.reminderEndHour, settings.reminderEndMinute)) {
                showTimeDialog(settings.reminderEndHour, settings.reminderEndMinute) { hour, minute ->
                    saveSettings(settings.copy(reminderEndHour = hour, reminderEndMinute = minute))
                }
            })
            val switchRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label("通知提醒", 16, Color.rgb(35, 47, 60), true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(Switch(this@MainActivity).apply {
                    isChecked = settings.reminderEnabled
                    setOnCheckedChangeListener { _, checked ->
                        saveSettings(settings.copy(reminderEnabled = checked))
                        if (!checked) WaterReminderScheduler.cancelAll(this@MainActivity)
                    }
                })
            }
            addView(switchRow)
            addView(label("达到每日目标后，系统会自动停止当天提醒。", 13, Color.rgb(96, 108, 120)))
            addView(switchRow("小组件大字体", settings.widgetLargeText) { checked ->
                saveSettings(settings.copy(widgetLargeText = checked))
            })
            addView(switchRow("小组件显示水杯", settings.widgetShowCup) { checked ->
                saveSettings(settings.copy(widgetShowCup = checked))
            })
            addView(section("数据"))
            addView(label("已记录 ${WaterStore.recordedDays(this@MainActivity)} 天 / ${WaterStore.recordCount(this@MainActivity)} 条记录", 14, Color.rgb(96, 108, 120)))
            addView(label("超过 180 天的详细记录会自动整理为每日总量，长期统计仍会保留。", 13, Color.rgb(96, 108, 120)))
        })
    }

    private fun switchRow(name: String, checked: Boolean, onChecked: (Boolean) -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
            addView(label(name, 16, Color.rgb(35, 47, 60), true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChecked(value) }
            })
        }
    }

    private fun settingRow(name: String, value: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(name, 15, Color.rgb(35, 47, 60), true))
                addView(label(value, 14, Color.rgb(100, 116, 132)))
            }
            addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@MainActivity).apply {
                text = "修改"
                isAllCaps = false
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(dp(82), dp(42)))
        }
    }

    private fun addWater(amountMl: Int) {
        WaterStore.addWater(this, amountMl)
        renderTab()
    }

    private fun saveSettings(settings: WaterSettings) {
        WaterStore.updateSettings(this, settings)
        renderTab()
    }

    private fun showNumberDialog(title: String, initialValue: Int, onConfirm: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialValue.toString())
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("保存") { _, _ -> input.text.toString().toIntOrNull()?.let(onConfirm) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTimeDialog(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onConfirm(h, m) }, hour, minute, true).show()
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
            build()
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }
    }

    private fun title(text: String): TextView = label(text, 26, Color.rgb(20, 35, 50), true).apply {
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun section(text: String): TextView = label(text, 18, Color.rgb(35, 47, 60), true).apply {
        setPadding(0, dp(8), 0, dp(6))
    }

    private fun label(text: String, sp: Int, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sp.toFloat()
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = true
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private enum class Tab(val title: String) {
        Home("首页"),
        Stats("统计"),
        Settings("设置")
    }
}
