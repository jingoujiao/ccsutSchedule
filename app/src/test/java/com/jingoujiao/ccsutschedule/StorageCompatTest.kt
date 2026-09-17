package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.CourseCardStyle
import com.jingoujiao.ccsutschedule.data.JsonStore
import com.jingoujiao.ccsutschedule.data.LEGACY_DEFAULT_PERIOD_TIMES
import com.jingoujiao.ccsutschedule.data.PeriodTime
import com.jingoujiao.ccsutschedule.data.defaultPeriodTimes
import java.nio.file.Files
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 存储兼容性：改名后的字段必须还能读老数据，不能让用户升级后被清空设置。 */
class StorageCompatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun readsLegacyTermStartDateKeyIntoFirstWeekMonday() {
        val legacy = """
            {
              "schedule": { "title": "2026-2027学年第1学期 课表", "courses": [] },
              "settings": { "termStartDate": "2026-10-05", "totalWeeks": 18, "themeMode": "light" }
            }
        """.trimIndent()

        val state = json.decodeFromString(AppStateData.serializer(), legacy)

        assertEquals("2026-10-05", state.settings.firstWeekMonday)
        assertEquals(18, state.settings.totalWeeks)
        assertEquals("light", state.settings.themeMode)
    }

    @Test
    fun writesFirstWeekMondayUnderItsOwnKey() {
        val state = AppStateData()
            .let { it.copy(settings = it.settings.copy(firstWeekMonday = "2026-10-05")) }

        val encoded = json.encodeToString(AppStateData.serializer(), state)

        assertTrue(encoded, encoded.contains("\"firstWeekMonday\":\"2026-10-05\""))
        assertTrue(encoded, !encoded.contains("termStartDate"))
    }

    @Test
    fun missingSettingsFallsBackToDefaults() {
        val state = json.decodeFromString(AppStateData.serializer(), """{"schedule":{"courses":[]}}""")
        assertEquals("", state.settings.firstWeekMonday)
        assertEquals(20, state.settings.totalWeeks)
        assertEquals(10, state.settings.periods.size)
    }

    /** 1.4.0 新增的外观字段：老数据读进来必须是「课程配色 + 100% 字号 + 100% 不透明度」。 */
    @Test
    fun appearanceFieldsAddedIn140DefaultToCurrentLook() {
        val state = json.decodeFromString(AppStateData.serializer(), """{"schedule":{"courses":[]}}""")
        assertEquals(CourseCardStyle.COLORED, state.settings.courseCardStyle)
        assertEquals(1f, state.settings.scheduleFontScale, 0.0001f)
        assertEquals(1f, state.settings.scheduleFontAlpha, 0.0001f)
        assertTrue(CourseCardStyle.usesCourseColor(state.settings.courseCardStyle))
        assertTrue(!CourseCardStyle.usesCourseColor(CourseCardStyle.GLASS))
    }

    @Test
    fun appearanceFieldsRoundTrip() {
        val state = AppStateData().let {
            it.copy(
                settings = it.settings.copy(
                    courseCardStyle = CourseCardStyle.GLASS,
                    scheduleFontScale = 1.25f,
                    scheduleFontAlpha = 0.6f,
                )
            )
        }
        val encoded = json.encodeToString(AppStateData.serializer(), state)
        val decoded = json.decodeFromString(AppStateData.serializer(), encoded)
        assertEquals(CourseCardStyle.GLASS, decoded.settings.courseCardStyle)
        assertEquals(1.25f, decoded.settings.scheduleFontScale, 0.0001f)
        assertEquals(0.6f, decoded.settings.scheduleFontAlpha, 0.0001f)
    }

    @Test
    fun unknownKeysAreTolerated() {
        val state = json.decodeFromString(
            AppStateData.serializer(),
            """{"schedule":{"courses":[],"futureField":1},"settings":{"firstWeekMonday":"2026-10-05","future":true}}""",
        )
        assertEquals("2026-10-05", state.settings.firstWeekMonday)
        assertEquals(emptyList<Course>(), state.schedule.courses)
    }

    @Test
    fun schoolDefaultTimetableMatchesTheGivenTimes() {
        val periods = defaultPeriodTimes()
        assertEquals(listOf("08:20", "09:15", "10:20", "11:15"), periods.take(4).map { it.start })
        assertEquals(listOf("09:05", "10:00", "11:05", "12:00"), periods.take(4).map { it.end })
        assertEquals(10, periods.size)
    }

    @Test
    fun upgradesUntouchedLegacyTimetableToSchoolDefaults() {
        val store = storeWith(LEGACY_DEFAULT_PERIOD_TIMES)

        val loaded = store.load()

        assertEquals(defaultPeriodTimes(), loaded.settings.periods)
        assertEquals("08:20", loaded.settings.periods.first().start)
        // 升级结果要落盘，第二次读取直接就是新时间
        assertEquals(defaultPeriodTimes(), store.load().settings.periods)
    }

    @Test
    fun keepsUserCustomisedTimetable() {
        val customised = LEGACY_DEFAULT_PERIOD_TIMES.mapIndexed { index, period ->
            if (index == 0) period.copy(start = "08:35") else period
        }
        val store = storeWith(customised)

        assertEquals(customised, store.load().settings.periods)
    }

    private fun storeWith(periods: List<PeriodTime>): JsonStore {
        val dir = Files.createTempDirectory("ccsut-test").toFile()
        val store = JsonStore(dir)
        store.save(AppStateData().let { it.copy(settings = it.settings.copy(periods = periods)) })
        return store
    }
}
