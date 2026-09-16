package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.Course
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

    @Test
    fun unknownKeysAreTolerated() {
        val state = json.decodeFromString(
            AppStateData.serializer(),
            """{"schedule":{"courses":[],"futureField":1},"settings":{"firstWeekMonday":"2026-10-05","future":true}}""",
        )
        assertEquals("2026-10-05", state.settings.firstWeekMonday)
        assertEquals(emptyList<Course>(), state.schedule.courses)
    }
}
