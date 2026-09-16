package com.jingoujiao.ccsutschedule.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
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
    private const val API_URL = "https://api.github.com/repos/jingoujiao/ccsutSchedule/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    /** 仓库还没发过 Release（私有仓库同样返回 404）。 */
    class NoReleasePublished : Exception("仓库还没有发布正式版本")

    data class Update(
        val version: String,
        val notes: String,
        val downloadUrl: String?,
        val pageUrl: String,
    )

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

    /** 检查是否有新版本：返回 null 表示已是最新。 */
    suspend fun checkLatest(currentVersion: String): Update? = withContext(Dispatchers.IO) {
        val response = httpGet(API_URL)
        when (response.code) {
            200 -> parseRelease(response.body, currentVersion)
            404 -> throw NoReleasePublished()
            else -> throw IllegalStateException("GitHub 返回 ${response.code}")
        }
    }

    /**
     * 解析 GitHub Releases 的 JSON。抽出来是为了能在没有真实 Release 的情况下单测
     * 「发现新版本」这条路径（draft / prerelease / 无 apk 资产 等情况）。
     */
    fun parseRelease(body: String, currentVersion: String): Update? {
        val release = json.decodeFromString(Release.serializer(), body)
        if (release.draft || release.prerelease) return null
        val remote = release.tagName.ifBlank { release.name }
        if (!isNewerVersion(remote, currentVersion)) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return Update(
            version = remote.removePrefix("v"),
            notes = release.body.trim(),
            downloadUrl = apk?.downloadUrl,
            pageUrl = release.htmlUrl.ifBlank { REPO_URL },
        )
    }

    /** 下载 APK 到 cache/update/。 */
    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "ccsut-$version.apk")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ccsutSchedule")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("下载失败：HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            target
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
