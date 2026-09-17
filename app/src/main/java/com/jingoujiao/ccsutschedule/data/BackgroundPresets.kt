package com.jingoujiao.ccsutschedule.data

import androidx.annotation.DrawableRes
import com.jingoujiao.ccsutschedule.R

/**
 * 内置壁纸。
 *
 * 图片放在 `res/drawable-nodpi/`（不按屏幕密度缩放），已经压到 1080px 宽，三张合计约 0.8 MB。
 * 用户选「自动配色」时不用任何图片，只用由主色相推导的渐变底色。
 */
object BackgroundPresets {

    /** 默认壁纸：新装用户第一眼看到的就是它。 */
    const val DEFAULT_ID = "mountain"

    /** 「不用图片，只用渐变底色」的取值。 */
    const val NONE = ""

    data class Preset(
        val id: String,
        val label: String,
        @param:DrawableRes val resId: Int,
    )

    val all: List<Preset> = listOf(
        Preset("mountain", "雪山草原", R.drawable.bg_mountain),
        Preset("beach", "荧光海滩", R.drawable.bg_beach),
        Preset("lake", "湖畔黄昏", R.drawable.bg_lake),
    )

    fun of(id: String): Preset? = all.firstOrNull { it.id == id }

    fun label(id: String): String = of(id)?.label ?: "自动配色"
}
