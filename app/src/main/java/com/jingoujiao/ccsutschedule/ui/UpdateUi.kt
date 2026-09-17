package com.jingoujiao.ccsutschedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.UpdateChecker
import java.io.File

/** 检查更新的界面状态。 */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data object NoRelease : UpdateUi
    data class Failed(val message: String) : UpdateUi
    data class Found(val update: UpdateChecker.Update) : UpdateUi
    data class Downloading(
        val progress: Float,
        val sourceLabel: String = "",
        val probing: Boolean = false,
    ) : UpdateUi

    data class Ready(val file: File) : UpdateUi
}

@Composable
fun UpdateDialog(
    state: UpdateUi,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUi.Found -> {
            val update = state.update
            CcsutDialog(
                title = "发现新版本 ${update.version}",
                onDismiss = onDismiss,
                buttons = {
                    if (update.downloads.isNotEmpty()) {
                        SecondaryButton("复制链接", onCopyLink, modifier = Modifier.weight(1f))
                    }
                    SecondaryButton("稍后", onDismiss, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        text = if (update.downloads.isNotEmpty()) "下载并安装" else "打开下载页",
                        onClick = { if (update.downloads.isNotEmpty()) onDownload() else onOpenPage() },
                        modifier = Modifier.weight(1.6f),
                    )
                },
            ) {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (update.notes.isBlank()) {
                        Text("建议更新到最新版本。", fontSize = 13.sp)
                    } else {
                        Text(update.notes, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }

        is UpdateUi.Downloading -> {
            CcsutDialog(
                title = if (state.probing) "正在测速，选最快的源" else "正在下载新版本",
                onDismiss = {},
            ) {
                Column {
                    if (state.probing) {
                        LinearProgressIndicator(modifier = Modifier.height(6.dp))
                    } else {
                        Text("${(state.progress * 100).toInt()}%", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.height(6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.probing) {
                            "GitHub 直连慢的话会自动换镜像加速"
                        } else {
                            "来源：${state.sourceLabel.ifBlank { "GitHub" }}"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is UpdateUi.Ready -> {
            CcsutDialog(
                title = "下载完成",
                onDismiss = onDismiss,
                buttons = {
                    SecondaryButton("稍后", onDismiss, modifier = Modifier.weight(1f))
                    PrimaryButton("安装", onInstall, modifier = Modifier.weight(1f))
                },
            ) {
                Text("点「安装」后，在系统界面里允许安装即可完成更新。", fontSize = 13.sp)
            }
        }

        is UpdateUi.Failed -> SimpleUpdateMessage(state.message, onDismiss)
        is UpdateUi.NoRelease -> SimpleUpdateMessage("仓库还没有发布正式版本", onDismiss)
        is UpdateUi.UpToDate -> SimpleUpdateMessage("已经是最新版本", onDismiss)
        else -> Unit
    }
}

@Composable
private fun SimpleUpdateMessage(message: String, onDismiss: () -> Unit) {
    CcsutDialog(
        title = "检查更新",
        onDismiss = onDismiss,
        buttons = {
            PrimaryButton("知道了", onDismiss, modifier = Modifier.weight(1f))
        },
    ) {
        Text(message, fontSize = 13.sp)
    }
}
