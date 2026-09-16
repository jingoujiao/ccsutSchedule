package com.jingoujiao.ccsutschedule

import android.app.Application
import com.jingoujiao.ccsutschedule.data.JsonStore
import com.jingoujiao.ccsutschedule.data.ScheduleRepository
import java.io.File

class CcsutApp : Application() {

    /** 全局唯一的数据仓库，Activity 重建也不会丢状态。 */
    val repository: ScheduleRepository by lazy {
        ScheduleRepository(JsonStore(File(filesDir, "data")))
    }
}
