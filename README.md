# 喝水记录

Android 原生 Kotlin App，用于本地记录每日饮水量、提醒喝水，并提供透明桌面小组件。

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- DataStore Preferences
- WorkManager
- Jetpack Glance AppWidget

## 功能

- 首页显示今日饮水总量，例如 `1200 / 2000 ml`
- 首页显示完成进度条
- 快捷记录：`+100ml`、`+200ml`、`+300ml`、`+500ml`、自定义
- 每次记录保存 `amountMl`、`timestamp`、`date`
- 支持删除单条饮水记录
- 设置每日目标，默认 `2000ml`
- 设置默认饮水量，默认 `200ml`
- 设置提醒间隔：`15`、`30`、`45`、`60`、`90`、`120` 分钟
- 设置提醒开始和结束时间，默认 `08:00` 到 `23:00`
- 支持打开或关闭通知提醒
- Android 13 及以上申请 `POST_NOTIFICATIONS` 权限
- 通知操作：记录默认饮水量、稍后提醒、今天不再提醒
- 统计页面显示最近 7 天和 30 天趋势
- 统计页面显示平均每日饮水量、达标天数和达标率
- 1x4 透明 Glance 小组件，显示今日饮水量、每日目标、完成进度，并提供 `+200ml` 快捷按钮

## 本地存储

App 不需要登录、不需要联网。饮水记录保存在 Room 数据库，用户设置保存在 DataStore。

## 数据表

`WaterRecord`

- `id: Long`
- `amountMl: Int`
- `timestamp: Long`
- `date: String`
