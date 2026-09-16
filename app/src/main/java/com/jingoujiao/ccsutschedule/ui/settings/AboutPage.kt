package com.jingoujiao.ccsutschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.UpdateChecker
import com.jingoujiao.ccsutschedule.ui.CardSurface
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.GlyphIcon
import com.jingoujiao.ccsutschedule.ui.SectionLabel
import com.jingoujiao.ccsutschedule.ui.SettingRow

@Composable
fun AboutPage(
    versionName: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    PageScaffold(
        title = "关于",
        subtitle = "版本 $versionName",
        onBack = onBack,
    ) {
        CardSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Calendar, MaterialTheme.colorScheme.onPrimary, size = 28.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "长工课程表",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "版本 $versionName",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        CardSurface {
            SectionLabel("作者与项目")
            SettingRow(
                title = "作者",
                subtitle = UpdateChecker.AUTHOR,
                glyph = Glyph.Info,
            )
            SettingRow(
                title = "项目仓库",
                subtitle = "github.com/jingoujiao/ccsutSchedule",
                glyph = Glyph.Import,
                onClick = { onOpenUrl(UpdateChecker.REPO_URL) },
                trailing = { Chevron() },
            )
            SettingRow(
                title = "Gitee 镜像",
                subtitle = "gitee.com/jingoujiao/ccsut-schedule（国内下载更快）",
                glyph = Glyph.Import,
                onClick = { onOpenUrl(UpdateChecker.GITEE_REPO_URL) },
                trailing = { Chevron() },
            )
            SettingRow(
                title = "反馈问题",
                subtitle = "GitHub Issues，点这里提 Issue",
                glyph = Glyph.Warning,
                onClick = { onOpenUrl(UpdateChecker.ISSUES_URL) },
                trailing = { Chevron() },
            )
        }

        CardSurface {
            SectionLabel("说明")
            Text(
                "本地离线运行：课表与设置只存在手机里，不上传任何数据。\n" +
                    "只有在「检查更新 / 自动检查更新」时会访问 GitHub Releases。\n" +
                    "课表解析全部在本机完成，导入的 xskb.xlsx 不会被保存。",
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
