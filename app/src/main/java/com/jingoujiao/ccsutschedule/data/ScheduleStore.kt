package com.jingoujiao.ccsutschedule.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** JSON 文件存储：写入走「临时文件 + 改名」，避免中途被杀导致文件损坏。 */
class JsonStore(private val dir: File) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File get() = File(dir, "ccsut-schedule.json")

    fun load(): AppStateData {
        if (!file.exists()) return AppStateData()
        return try {
            val decoded = json.decodeFromString(AppStateData.serializer(), file.readText(Charsets.UTF_8))
            val migrated = migrateLegacyDefaults(decoded)
            if (migrated != decoded) save(migrated) // 让升级结果落盘，避免每次启动都重算
            migrated
        } catch (_: Exception) {
            // 数据损坏时保留现场，回退到空白数据，不让 App 起不来
            runCatching { file.renameTo(File(dir, "ccsut-schedule.corrupt.json")) }
            AppStateData()
        }
    }

    /**
     * 默认作息改成本校时间后，老数据的 `periods` 还是旧默认值。
     * 只在「一个字都没改过」时才替换，用户自己调过的时间绝不动。
     */
    private fun migrateLegacyDefaults(data: AppStateData): AppStateData {
        if (data.settings.periods != LEGACY_DEFAULT_PERIOD_TIMES) return data
        return data.copy(settings = data.settings.copy(periods = defaultPeriodTimes()))
    }

    fun save(data: AppStateData) {
        dir.mkdirs()
        val temp = File(dir, "ccsut-schedule.json.tmp")
        temp.writeText(json.encodeToString(AppStateData.serializer(), data), Charsets.UTF_8)
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}

/** 课表数据仓库：内存里只有一份状态，任何改动都立即落盘并推给 UI。 */
class ScheduleRepository(private val store: JsonStore) {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppStateData> = _state.asStateFlow()

    val current: AppStateData get() = _state.value

    private fun mutate(block: (AppStateData) -> AppStateData) {
        val next = block(_state.value)
        _state.value = next
        store.save(next)
    }

    /** 整体替换课表（导入时用）。 */
    fun replaceSchedule(schedule: ScheduleData) = mutate { it.copy(schedule = schedule) }

    /** 追加导入（保留已有课程）。 */
    fun appendCourses(courses: List<Course>, sourceLabel: String) = mutate { state ->
        val existing = state.schedule.courses
        val merged = existing + courses.map { it.copy(colorKey = it.colorKey % COURSE_COLOR_COUNT) }
        state.copy(
            schedule = state.schedule.copy(
                courses = normalize(merged),
                importSource = sourceLabel,
            )
        )
    }

    fun saveCourse(course: Course) = mutate { state ->
        val courses = state.schedule.courses
        val replaced = if (courses.any { it.id == course.id }) {
            courses.map { if (it.id == course.id) course else it }
        } else {
            courses + course
        }
        state.copy(schedule = state.schedule.copy(courses = normalize(replaced)))
    }

    fun deleteCourse(id: String) = mutate { state ->
        state.copy(schedule = state.schedule.copy(courses = state.schedule.courses.filterNot { it.id == id }))
    }

    fun clearCourses() = mutate { state ->
        state.copy(schedule = state.schedule.copy(courses = emptyList(), title = "", ownerLabel = "", importSource = ""))
    }

    fun updateTitle(title: String) = mutate { state ->
        state.copy(schedule = state.schedule.copy(title = title))
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) = mutate { state ->
        state.copy(settings = block(state.settings))
    }

    fun clearAll() = mutate { AppStateData() }

    private fun normalize(courses: List<Course>): List<Course> =
        courses.sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.name }))
}

/** 新建课程时的默认值与配色分配。 */
object CourseFactory {

    fun newId(): String = UUID.randomUUID().toString().take(8)

    /** 选一个当前用得最少的配色，尽量让一周内的颜色区分开。 */
    fun nextColorKey(courses: List<Course>): Int {
        val usage = IntArray(COURSE_COLOR_COUNT)
        courses.forEach { usage[it.colorKey.mod(COURSE_COLOR_COUNT)]++ }
        var best = 0
        for (index in 1 until COURSE_COLOR_COUNT) {
            if (usage[index] < usage[best]) best = index
        }
        return best
    }

    fun blank(courses: List<Course>, weekday: Int, period: Int): Course = Course(
        id = newId(),
        name = "",
        teacher = "",
        location = "",
        weekday = weekday,
        periods = listOf(period),
        weeks = (1..16).toList(),
        colorKey = nextColorKey(courses),
    )
}
