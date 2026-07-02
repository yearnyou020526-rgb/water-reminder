package com.example.waterreminder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var totalText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var targetButton: Button
    private lateinit var quickButton: Button
    private lateinit var intervalButton: Button
    private lateinit var startButton: Button
    private lateinit var endButton: Button
    private lateinit var reminderSwitch: Switch
    private lateinit var statsContainer: LinearLayout
    private lateinit var todayRecordsContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        bindActions()
        requestNotificationPermissionIfNeeded()
        WaterReminderScheduler.scheduleNext(this)
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        WaterWidgetProvider.updateHomeWidgets(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "没有通知权限时，提醒消息不会弹出。", Toast.LENGTH_LONG).show()
        }
        WaterReminderScheduler.scheduleNext(this)
        refreshUi()
    }

    private fun buildContentView(): View {
        return ScrollView(this).apply {
            setBackgroundColor(COLOR_PAGE)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(28))

                addView(TextView(this@MainActivity).apply {
                    text = "喝水提醒"
                    textSize = 28f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(COLOR_TEXT)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "记录今天喝了多少水，按设定间隔提醒。"
                    textSize = 14f
                    setTextColor(COLOR_MUTED)
                    setPadding(0, dp(4), 0, dp(14))
                })

                addView(card().apply {
                    totalText = TextView(this@MainActivity).apply {
                        gravity = Gravity.CENTER
                        textSize = 34f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(COLOR_BLUE_DARK)
                    }
                    addView(totalText)

                    progressText = TextView(this@MainActivity).apply {
                        gravity = Gravity.CENTER
                        textSize = 14f
                        setTextColor(COLOR_MUTED)
                        setPadding(0, dp(4), 0, dp(12))
                    }
                    addView(progressText)

                    progressBar = ProgressBar(
                        this@MainActivity,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                    ).apply {
                        max = 100
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(10)
                        )
                    }
                    addView(progressBar)

                    addSpacer(this, 14)
                    addView(horizontalRow().apply {
                        addView(actionButton("+100ml") { addWater(100) })
                        addView(actionButton("+200ml") { addWater(200) })
                    })
                    addSpacer(this, 8)
                    addView(horizontalRow().apply {
                        addView(actionButton("+250ml") { addWater(250) })
                        addView(actionButton("+500ml") { addWater(500) })
                    })
                    addSpacer(this, 8)
                    addView(horizontalRow().apply {
                        addView(actionButton("自定义") { showAmountDialog() })
                        addView(secondaryButton("撤销最后一次") { undoLast() })
                    })
                })

                addView(sectionTitle("设置"))
                addView(card().apply {
                    targetButton = settingButton()
                    quickButton = settingButton()
                    intervalButton = settingButton()
                    startButton = settingButton()
                    endButton = settingButton()
                    reminderSwitch = Switch(this@MainActivity).apply {
                        text = "开启提醒"
                        textSize = 16f
                        setTextColor(COLOR_TEXT)
                        setPadding(0, 0, 0, dp(8))
                    }
                    addView(reminderSwitch)
                    addView(targetButton)
                    addView(quickButton)
                    addView(intervalButton)
                    addView(horizontalRow().apply {
                        startButton.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(4))
                        }
                        endButton.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(4))
                        }
                        addView(startButton)
                        addView(endButton)
                    })
                })

                addView(sectionTitle("统计"))
                statsContainer = card()
                addView(statsContainer)

                addView(sectionTitle("今天记录"))
                todayRecordsContainer = card()
                addView(todayRecordsContainer)

                addView(sectionTitle("最近 30 天"))
                historyContainer = card()
                addView(historyContainer)
            })
        }
    }

    private fun bindActions() {
        targetButton.setOnClickListener {
            showNumberDialog("每日目标 ml", WaterRepository.getTargetMl(this), 100, 10000) {
                WaterRepository.setTargetMl(this, it)
                afterDataChanged()
            }
        }
        quickButton.setOnClickListener {
            showNumberDialog("小组件快捷记录 ml", WaterRepository.getQuickAmountMl(this), 10, 2000) {
                WaterRepository.setQuickAmountMl(this, it)
                afterDataChanged()
            }
        }
        intervalButton.setOnClickListener {
            showIntervalDialog()
        }
        startButton.setOnClickListener {
            showTimeDialog("提醒开始时间", WaterRepository.getStartMinutes(this)) {
                WaterRepository.setStartMinutes(this, it)
                afterSettingsChanged()
            }
        }
        endButton.setOnClickListener {
            showTimeDialog("提醒结束时间", WaterRepository.getEndMinutes(this)) {
                WaterRepository.setEndMinutes(this, it)
                afterSettingsChanged()
            }
        }
        reminderSwitch.setOnCheckedChangeListener { _, checked ->
            WaterRepository.setReminderEnabled(this, checked)
            afterSettingsChanged()
        }
    }

    private fun refreshUi() {
        val total = WaterRepository.getTodayTotal(this)
        val target = WaterRepository.getTargetMl(this)
        val percent = WaterRepository.progressPercent(this)
        totalText.text = "$total ml"
        progressText.text = "今日目标 $target ml，已完成 $percent%"
        progressBar.progress = percent

        targetButton.text = "每日目标：$target ml"
        quickButton.text = "小组件快捷：${WaterRepository.getQuickAmountMl(this)} ml"
        intervalButton.text = "提醒间隔：${WaterRepository.getIntervalMinutes(this)} 分钟"
        startButton.text = "开始：${WaterRepository.formatTime(WaterRepository.getStartMinutes(this))}"
        endButton.text = "结束：${WaterRepository.formatTime(WaterRepository.getEndMinutes(this))}"
        reminderSwitch.isChecked = WaterRepository.isReminderEnabled(this)

        renderStats()
        renderTodayRecords()
        renderHistory()
    }

    private fun renderStats() {
        statsContainer.removeAllViews()
        val stats = WaterRepository.getStats(this)
        statsContainer.addView(statLine("连续达标", "${stats.streakDays} 天"))
        statsContainer.addView(statLine("7 天平均", "${stats.average7Ml} ml/天"))
        statsContainer.addView(statLine("30 天平均", "${stats.average30Ml} ml/天"))
        statsContainer.addView(statLine("30 天达标", "${stats.reachedDays30} 天"))
        statsContainer.addView(statLine("累计记录", "${stats.recordedDays} 天"))
        statsContainer.addView(statLine("累计喝水", "${stats.totalMl} ml"))
    }

    private fun renderTodayRecords() {
        todayRecordsContainer.removeAllViews()
        val records = WaterRepository.getTodayRecords(this)
        if (records.isEmpty()) {
            todayRecordsContainer.addView(emptyText("今天还没有记录。"))
            return
        }
        records.take(12).forEach { record ->
            todayRecordsContainer.addView(statLine(record.displayTime, "+${record.amountMl} ml"))
        }
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        WaterRepository.getRecentDays(this, 30).forEach { day ->
            val suffix = if (day.reached) "达标" else ""
            historyContainer.addView(statLine(day.displayDate, "${day.totalMl} ml $suffix".trim()))
        }
    }

    private fun addWater(amount: Int) {
        WaterRepository.addWater(this, amount)
        afterDataChanged()
        Toast.makeText(this, "已记录 ${amount}ml", Toast.LENGTH_SHORT).show()
    }

    private fun undoLast() {
        if (WaterRepository.undoLastToday(this)) {
            afterDataChanged()
            Toast.makeText(this, "已撤销最后一次记录", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "今天没有可撤销的记录", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAmountDialog() {
        showNumberDialog("这次喝了多少 ml", WaterRepository.getQuickAmountMl(this), 1, 5000) {
            addWater(it)
        }
    }

    private fun showIntervalDialog() {
        val labels = arrayOf("30 分钟", "45 分钟", "60 分钟", "90 分钟", "120 分钟")
        val values = intArrayOf(30, 45, 60, 90, 120)
        val current = WaterRepository.getIntervalMinutes(this)
        val checked = values.indexOf(current).takeIf { it >= 0 } ?: 2
        AlertDialog.Builder(this)
            .setTitle("提醒间隔")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                WaterRepository.setIntervalMinutes(this, values[which])
                dialog.dismiss()
                afterSettingsChanged()
            }
            .show()
    }

    private fun showNumberDialog(
        title: String,
        current: Int,
        min: Int,
        max: Int,
        onSave: (Int) -> Unit
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            selectAll()
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value == null || value !in min..max) {
                    Toast.makeText(this, "请输入 $min 到 $max 之间的数字", Toast.LENGTH_SHORT).show()
                } else {
                    onSave(value)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTimeDialog(title: String, currentMinutes: Int, onSave: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                onSave(WaterRepository.minutesFrom(hourOfDay, minute))
            },
            currentMinutes / 60,
            currentMinutes % 60,
            true
        ).apply {
            setTitle(title)
        }.show()
    }

    private fun afterDataChanged() {
        refreshUi()
        WaterWidgetProvider.updateHomeWidgets(this)
    }

    private fun afterSettingsChanged() {
        WaterReminderScheduler.scheduleNext(this)
        afterDataChanged()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(COLOR_CARD, 14f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(8), 0, dp(12))
            }
        }
    }

    private fun sectionTitle(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
            setPadding(0, dp(10), 0, 0)
        }
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setAllCaps(false)
            background = rounded(COLOR_BLUE, 12f)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            setOnClickListener { action() }
        }
    }

    private fun secondaryButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            setTextColor(COLOR_BLUE_DARK)
            setAllCaps(false)
            background = rounded(COLOR_BLUE_SOFT, 12f)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            setOnClickListener { action() }
        }
    }

    private fun settingButton(): Button {
        return Button(this).apply {
            textSize = 15f
            setTextColor(COLOR_TEXT)
            setAllCaps(false)
            gravity = Gravity.CENTER
            background = rounded(COLOR_SETTING, 10f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
    }

    private fun statLine(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label    $value"
            textSize = 15f
            setTextColor(COLOR_TEXT)
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun emptyText(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
    }

    private fun horizontalRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
    }

    private fun addSpacer(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        })
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 81_200
        private val COLOR_PAGE = Color.rgb(245, 250, 253)
        private val COLOR_CARD = Color.WHITE
        private val COLOR_BLUE = Color.rgb(19, 120, 184)
        private val COLOR_BLUE_DARK = Color.rgb(11, 79, 122)
        private val COLOR_BLUE_SOFT = Color.rgb(225, 244, 252)
        private val COLOR_SETTING = Color.rgb(242, 247, 250)
        private val COLOR_TEXT = Color.rgb(31, 41, 55)
        private val COLOR_MUTED = Color.rgb(107, 114, 128)
    }
}
