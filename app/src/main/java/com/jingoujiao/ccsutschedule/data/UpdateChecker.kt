package com.jingoujiao.ccsutschedule.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub Releases 更新检查。只访问本仓库的 Releases 接口，不碰任何其它地址。
 */
object UpdateChecker {

    const val AUTHOR = "jingoujiao"
    const val REPO_URL = "https://github.com/jingoujiao/ccsutSchedule"
    const val ISSUES_URL = "$REPO_URL/issues"
    const val GITEE_REPO = "jingoujiao/ccsut-schedule"
    const val GITEE_REPO_URL = "https://gitee.com/jingoujiao/ccsut-schedule"
    private const val GITHUB_API_URL = "https://api.github.com/repos/jingoujiao/ccsutSchedule/releases/latest"

    private fun giteeApiUrl(repo: String) = "https://gitee.com/api/v5/repos/${repo.trim().trim('/')}/releases/latest"

    private const val GITEE_LABEL = "Gitee 直连"
    private const val GITHUB_LABEL = "GitHub 直连"

    private val json = Json { ignoreUnknownKeys = true }

    /** 仓库还没发过 Release（私有仓库同样返回 404）。 */
    class NoReleasePublished : Exception("仓库还没有发布正式版本")

    data class Update(
        val version: String,
        val notes: String,
        val pageUrl: String,
        /** 可用的下载地址，按优先级排列（Gitee 在前）。 */
        val downloads: List<DownloadSource> = emptyList(),
    ) {
        val downloadUrl: String? get() = downloads.firstOrNull()?.url
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    )

    private data class Found(
        val tag: String,
        val notes: String,
        val pageUrl: String,
        val apkUrls: List<String>,
        val label: String,
    )

    /**
     * 检查是否有新版本：返回 null 表示已是最新。
     *
     * Gitee 与 GitHub 并行查：Gitee 国内快，只要它已经有更新的版本就直接用它、不再等 GitHub
     * （GitHub 的 API 在国内经常要等到超时）；否则以 GitHub 的结果为准。
     * 下载地址也把两边的都带上（Gitee 优先）。
     */
    suspend fun checkLatest(currentVersion: String, giteeRepo: String = GITEE_REPO): Update? =
        coroutineScope {
            val repo = giteeRepo.trim().trim('/').takeIf { it.isNotEmpty() }
            val githubDeferred = async {
                runCatching { fetch(GITHUB_API_URL, GITHUB_LABEL, REPO_URL) }.getOrNull()
            }
            val giteeDeferred = repo?.let {
                async {
                    runCatching { fetch(giteeApiUrl(it), GITEE_LABEL, "https://gitee.com/$it") }.getOrNull()
                }
            }

            val gitee = giteeDeferred?.await()
            if (gitee != null && isNewerVersion(gitee.tag, currentVersion) && gitee.apkUrls.isNotEmpty()) {
                // Gitee 上就有可下载的新版本，不用再等 GitHub
                githubDeferred.cancel()
                return@coroutineScope buildUpdate(gitee, null, currentVersion)
            }

            val github = githubDeferred.await()
            if (github == null && gitee == null) throw NoReleasePublished()
            buildUpdate(github, gitee, currentVersion)
        }

    private fun buildUpdate(github: Found?, gitee: Found?, currentVersion: String): Update? {
        val githubNewer = github != null && isNewerVersion(github.tag, currentVersion)
        val giteeNewer = gitee != null && isNewerVersion(gitee.tag, currentVersion)
        if (!githubNewer && !giteeNewer) return null

        val chosen = when {
            giteeNewer && !githubNewer -> gitee!!
            githubNewer && !giteeNewer -> github!!
            isNewerVersion(gitee!!.tag, github!!.tag) -> gitee
            else -> github
        }

        // 下载候选：优先 Gitee；两边 tag 相同时互为备份
        val downloads = buildList {
            if (chosen === gitee) gitee.apkUrls.forEach { add(DownloadSource(GITEE_LABEL, it)) }
            if (chosen === github) github.apkUrls.forEach { add(DownloadSource(GITHUB_LABEL, it)) }
            val other = if (chosen === gitee) github else gitee
            if (other != null && other.tag == chosen.tag) {
                other.apkUrls.forEach { add(DownloadSource(other.label, it)) }
            }
        }

        return Update(
            version = chosen.tag.removePrefix("v"),
            notes = chosen.notes,
            pageUrl = chosen.pageUrl,
            downloads = downloads,
        )
    }

    private fun fetch(apiUrl: String, label: String, fallbackPageUrl: String): Found? {
        val response = httpGet(apiUrl)
        if (response.code == 404) return null
        if (response.code !in 200..299) throw IllegalStateException("$label 返回 ${response.code}")
        val release = json.decodeFromString(Release.serializer(), response.body)
        if (release.draft || release.prerelease) return null
        val tag = release.tagName.ifBlank { release.name }
        if (tag.isBlank()) return null
        return Found(
            tag = tag,
            notes = release.body.trim(),
            pageUrl = release.htmlUrl.ifBlank { fallbackPageUrl },
            apkUrls = release.assets
                .filter { it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank() }
                .map { it.downloadUrl },
            label = label,
        )
    }

    /**
     * 解析一份 Release JSON（GitHub / Gitee 结构一致），保留给单测用。
     */
    fun parseRelease(body: String, currentVersion: String): Update? {
        val release = json.decodeFromString(Release.serializer(), body)
        if (release.draft || release.prerelease) return null
        val remote = release.tagName.ifBlank { release.name }
        if (!isNewerVersion(remote, currentVersion)) return null
        val apkUrls = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank() }
            .map { it.downloadUrl }
        return Update(
            version = remote.removePrefix("v"),
            notes = release.body.trim(),
            pageUrl = release.htmlUrl.ifBlank { REPO_URL },
            downloads = apkUrls.map { DownloadSource(GITHUB_LABEL, it) },
        )
    }

    /** 一个可用的下载地址（直连或某个加速站）。 */
    data class DownloadSource(val label: String, val url: String)

    /** 测速时认为「够快、不用再找镜像」的下限。 */
    private const val FAST_ENOUGH_BYTES_PER_SECOND = 200 * 1024

    /** 下载过程中低于这个速度就判定这个源不行，自动换下一个。 */
    private const val MIN_ACCEPTABLE_BYTES_PER_SECOND = 40 * 1024

    /** 下载开始后多少毫秒开始判定速度。 */
    private const val SLOW_CHECK_AFTER_MS = 10_000L

    /**
     * 按用户的更新源设置，把候选下载地址排好序。
     *
     * Gitee 直连本身就是国内速度，不参与镜像加速；镜像只作用于 GitHub 的地址。
     */
    fun buildSources(
        downloads: List<DownloadSource>,
        source: String,
        customMirror: String,
    ): List<DownloadSource> {
        val gitee = downloads.filter { it.label == GITEE_LABEL }
        val github = downloads.filter { it.label == GITHUB_LABEL }
        val others = downloads.filter { it.label != GITEE_LABEL && it.label != GITHUB_LABEL }
        val mirrors = github.flatMap { item ->
            UpdateSource.mirrors.map { (name, prefix) -> DownloadSource(name, prefix + item.url) }
        }
        val custom = customMirror.trim().takeIf { it.isNotEmpty() }?.let { prefix ->
            val normalized = if (prefix.endsWith("/") || prefix.endsWith("=")) prefix else "$prefix/"
            github.firstOrNull()?.let { DownloadSource("自定义加速", normalized + it.url) }
        }
        val ordered = when (source) {
            UpdateSource.GITEE -> gitee + github + listOfNotNull(custom) + mirrors
            UpdateSource.GITHUB -> github + mirrors + listOfNotNull(custom) + gitee
            UpdateSource.MIRROR -> mirrors + listOfNotNull(custom) + gitee + github
            UpdateSource.CUSTOM -> listOfNotNull(custom) + gitee + github + mirrors
            else -> gitee + github + listOfNotNull(custom) + mirrors
        }
        return (ordered + others).distinctBy { it.url }
    }

    /** 兼容旧签名：只有一个下载地址。 */
    fun buildSources(originalUrl: String, source: String, customMirror: String): List<DownloadSource> =
        buildSources(listOf(DownloadSource(GITHUB_LABEL, originalUrl)), source, customMirror)

    /**
     * 依次测速，挑一个最快的源。直连已经够快就直接用直连。
     *
     * 返回 null 表示都没测通（调用方会退回第一个候选硬试一次）。
     */
    suspend fun pickFastest(sources: List<DownloadSource>): DownloadSource? = withContext(Dispatchers.IO) {
        var best: DownloadSource? = null
        var bestSpeed = 0L
        for (source in sources) {
            val speed = probeSpeed(source.url)
            if (speed > bestSpeed) {
                best = source
                bestSpeed = speed
            }
            if (bestSpeed >= FAST_ENOUGH_BYTES_PER_SECOND) break
        }
        best
    }

    /** 读一小段测下载速度（字节/秒），失败返回 0。 */
    private fun probeSpeed(url: String): Long {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return 0
        return try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "ccsutSchedule")
            connection.setRequestProperty("Range", "bytes=0-524287")
            if (connection.responseCode !in 200..299) return 0
            val started = System.currentTimeMillis()
            var read = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (read < 512 * 1024) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    read += count
                    if (System.currentTimeMillis() - started > 4_000) break
                }
            }
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1)
            read * 1000 / elapsed
        } catch (_: Throwable) {
            0
        } finally {
            connection.disconnect()
        }
    }

    /** 下载 APK 到 cache/update/。 */
    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit = {},
    ): File = downloadApk(context, listOf(DownloadSource("GitHub 直连", url)), version, {}, onProgress)

    /**
     * 从 [sources] 里挑最快的下载；某个源中途失败或**太慢**会自动换下一个。
     * [onSourceLabel] 会报出当前用的是哪个源，方便在界面上显示。
     */
    suspend fun downloadApk(
        context: Context,
        sources: List<DownloadSource>,
        version: String,
        onSourceLabel: (String) -> Unit,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "ccsut-$version.apk")

        val ordered = buildList {
            pickFastest(sources)?.let { add(it) }
            addAll(sources)
        }.distinctBy { it.url }

        var lastError: Throwable? = null
        for (source in ordered) {
            onSourceLabel(source.label)
            onProgress(0f)
            try {
                streamToFile(source.url, target, onProgress)
                return@withContext target
            } catch (error: Throwable) {
                lastError = error
                runCatching { target.delete() }
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    /** 下载太慢时抛这个，调用方会换下一个源。 */
    class SlowSourceException(message: String) : Exception(message)

    private fun streamToFile(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ccsutSchedule")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var copied = 0L
            var lastReport = 0L
            val started = System.currentTimeMillis()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - lastReport > 256 * 1024) {
                            lastReport = copied
                            if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                            // 看门狗：开头一段时间内速度太低就换源，别让用户干等
                            val elapsed = System.currentTimeMillis() - started
                            if (elapsed > SLOW_CHECK_AFTER_MS &&
                                copied * 1000 / elapsed < MIN_ACCEPTABLE_BYTES_PER_SECOND
                            ) {
                                throw SlowSourceException("下载太慢，换源")
                            }
                        }
                    }
                }
            }
            if (total > 0 && copied < total) throw IllegalStateException("下载不完整")
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    /** 交给系统安装器安装。 */
    fun installApk(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 复制到剪贴板，方便用浏览器/下载工具自己下。 */
    fun copyToClipboard(context: Context, label: String, text: String) {
        runCatching {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        }
    }

    /** 当前安装版本的版本名（去掉 `-debug` 这类后缀）。 */
    fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().substringBefore('-')
    }.getOrDefault("1.0.0")

    /**
     * 版本号比较：`1.2.0` > `1.1.9`；`v` 前缀和 `-debug` 这类后缀会被忽略；
     * 段数不足按 0 补齐（`1.0` 与 `1.0.0` 视为同一版本）。
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = versionParts(remote)
        val localParts = versionParts(local)
        for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(index) { 0 }
            val l = localParts.getOrElse(index) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun versionParts(version: String): List<Int> = version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

    private data class Response(val code: Int, val body: String)

    private fun httpGet(url: String): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "ccsutSchedule")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
