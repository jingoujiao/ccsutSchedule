# 长工课程表（ccsutSchedule）

一个只做一件事的安卓课表 App：**把你从教务处导出的 `xskb.xlsx` 直接变成手机上能用的课表**。
Kotlin + Jetpack Compose 从零实现，无第三方 UI 库、无网络请求、无账号、无广告。

## 功能

| 功能 | 说明 |
| --- | --- |
| 周课表网格 | 周一~周日 × 第 1~N 节，跨节次的课自动合并成一张卡片，按星期几高亮“今天” |
| 周次切换 | 顶部周次胶囊条，可左右切换、一键“回本周”；只显示当前周的课（可选显示非本周为淡色） |
| 今日 / 下节课 | “今日”页给出正在上 / 下一节课的名字、时间、教室与倒计时，下面列出今天全部课程 |
| 课程详情与增删改 | 点课程看详情（教师、教室、周次、冲突提示），可编辑、删除；点空格子直接加课 |
| xskb.xlsx 导入 | 解析教务处「上课啦」导出的课表，导入前有完整预览（学期、人数、课程清单、告警） |
| 作息时间设置 | 逐节编辑每节课的开始/结束时间，可增删节次 |
| 学期开始日期 | 设置第 1 周周一的日期后，自动判断“今天第几周” |
| 深色 / 浅色 / 跟随系统 | 一套由主色相推导的配色系统，5 种配色方案 |
| 课程配色 | 12 种课程色，同名课程同色，可在编辑页手动指定 |
| 自定义背景 | 选一张图片做背景，可调不透明度 |

## 数据与隐私

- 课表、设置全部存在 App 私有目录 `files/data/ccsut-schedule.json`，**不联网**。
- 导入的 `xskb.xlsx` 只在本机解析，解析完不保留原文件。
- 仓库**不包含**任何个人课表数据；`xskb.xlsx` 已在 `.gitignore` 中排除。

## 构建

环境：JDK 17+、Android SDK（platform 37 / build-tools 36+）。

```powershell
$env:JAVA_HOME='C:\Users\<你>\.jdks\java-21'
$env:ANDROID_HOME='D:\Android\Sdk'
.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（包名 `com.jingoujiao.ccsutschedule.debug`）。

跑单元测试（解析器 / 周次 / 日期换算）：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 已做过的验收（2026-09-16）

| 项目 | 结果 |
| --- | --- |
| 单元测试 `:app:testDebugUnitTest` | 17/17 通过（周次解析 6 + xskb 端到端 10 + 本机真实文件体检 1），`--rerun-tasks` 强制重跑确认非陈旧结果 |
| 真实 `xskb.xlsx` 解析 | 22 个课程块、节次 1-8、周次 3-19、周一~周六，逐条与源文件核对一致，零告警 |
| 模拟器（Android 36 / API 36） | 导入流程（选文件 → 预览 → 覆盖导入）、落库 JSON 22 条、第 6 周网格与源文件逐格一致、周三高亮、课程详情、手动加课（第 23 条落库）、删除（回到 22 条）、今日页“正在上课·还剩 12 分”倒计时、设置页开学日期改写后自动算出“第 6 周”、深色/浅色切换，全部实测通过 |
| APK | `:app:assembleDebug` 成功，产物约 12 MB，`adb install` 在本机模拟器安装并启动无崩溃 |

> 说明：仓库内不放截图，因为截图里会带个人课表与姓名；验收结论以上表为准。

## xskb.xlsx 解析约定

文件是「节次列 + 星期X 列」的网格，单元格里可能塞了多门课：

```
高等数学2（上）

于卫东【5-18周】
7-南203
```

解析规则（见 `data/XskbParser.kt`，全部可单测）：

1. 表头优先按文字识别“星期X”，识别不出来时退化为按列序（第 2-8 列 = 周一至周日）并给出告警；
2. 以「含【周次】的行」为锚点：它上面最近的非空行是课名，下面紧邻的行是教室；
3. 只有**节次相邻**且课名/教师/教室/周次完全相同的块才合并（第 1-2 节与第 5-6 节不会并成一张卡）；
4. 周次支持 `5-12周`、`16周`、`1-4,6-8周`、`6—17周`（全角破折号）、`5、7、9周`、`第5-12周`、单双周；
5. 文件底部只有节次号、没有课的行（如 `9.0`）不计入作息节次。

文件里**没有**开学日期和作息时间，所以这两项必须在设置里自己填——这是文件本身的限制，不是解析问题。

## 目录结构

```
app/src/main/java/com/jingoujiao/ccsutschedule/
├── MainActivity.kt          根界面：底部导航、全屏覆盖页、文件选择、轻提示
├── CcsutApp.kt              Application，持有全局仓库
├── data/
│   ├── Models.kt            Course / PeriodTime / ScheduleData / AppSettings
│   ├── WeekUtils.kt         周次表达式解析与格式化、日期↔教学周换算
│   ├── XlsxReader.kt        极简 xlsx 读取（java.util.zip + 系统 XML 解析器）
│   ├── XskbParser.kt        课表网格 → 课程块
│   └── ScheduleStore.kt     JSON 落盘 + 数据仓库（StateFlow）
└── ui/
    ├── theme/Theme.kt       主色相 → 完整配色方案，课程配色
    ├── Glyph.kt             自绘图标（不依赖 material-icons-extended）
    ├── Common.kt            卡片/胶囊/按钮/弹层/对话框等公共组件
    ├── WeekScreen.kt        周课表网格 + 周次切换
    ├── TodayScreen.kt       今日课程 / 下节课
    ├── CourseEditor.kt      课程详情弹层 + 课程编辑页
    ├── SettingsScreen.kt    设置页（学期、作息、外观、数据）
    └── ImportScreen.kt      xskb 导入（选文件 → 预览 → 覆盖/追加）
```
