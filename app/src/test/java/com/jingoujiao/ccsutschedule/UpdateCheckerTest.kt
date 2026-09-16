package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.UpdateChecker
import com.jingoujiao.ccsutschedule.data.UpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun detectsNewerVersions() {
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.2.0", "1.1.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.10.0", "1.9.0"))
    }

    @Test
    fun ignoresPrefixesAndSuffixes() {
        assertTrue(UpdateChecker.isNewerVersion("v1.1.0", "1.0.0-debug"))
        assertFalse(UpdateChecker.isNewerVersion("1.1.0-debug", "1.1.0"))
        // 段数不足按 0 补齐，1.0 与 1.0.0 视为同一版本
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.0", "1.0"))
    }

    @Test
    fun doesNotFlagSameOrOlderVersions() {
        assertFalse(UpdateChecker.isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun linksPointAtTheProjectRepository() {
        assertTrue(UpdateChecker.REPO_URL.endsWith("jingoujiao/ccsutSchedule"))
        assertTrue(UpdateChecker.ISSUES_URL.endsWith("/issues"))
        assertTrue(UpdateChecker.ISSUES_URL.startsWith(UpdateChecker.REPO_URL))
        assertTrue(UpdateChecker.GITEE_REPO_URL.endsWith("jingoujiao/ccsut-schedule"))
        assertEquals("jingoujiao/ccsut-schedule", UpdateChecker.GITEE_REPO)
    }

    @Test
    fun parsesARealReleasePayloadWithApkAsset() {
        val update = UpdateChecker.parseRelease(RELEASE_JSON, "1.1.0")

        assertEquals("1.2.0", update?.version)
        assertEquals("修了几个导入问题", update?.notes)
        assertEquals(
            "https://github.com/jingoujiao/ccsutSchedule/releases/download/v1.2.0/ccsutSchedule-1.2.0.apk",
            update?.downloadUrl,
        )
        assertEquals("https://github.com/jingoujiao/ccsutSchedule/releases/tag/v1.2.0", update?.pageUrl)
    }

    @Test
    fun ignoresOlderAndNonStableReleases() {
        assertEquals(null, UpdateChecker.parseRelease(RELEASE_JSON.replace("v1.2.0", "v1.0.0"), "1.1.0"))
        assertEquals(null, UpdateChecker.parseRelease(RELEASE_JSON, "1.2.0"))
        assertEquals(null, UpdateChecker.parseRelease(RELEASE_JSON.replace("\"draft\": false", "\"draft\": true"), "1.1.0"))
        assertEquals(
            null,
            UpdateChecker.parseRelease(RELEASE_JSON.replace("\"prerelease\": false", "\"prerelease\": true"), "1.1.0"),
        )
    }

    @Test
    fun releaseWithoutApkAssetFallsBackToTheReleasePage() {
        val withoutAsset = RELEASE_JSON.replace(
            Regex("""(?s)"assets": \[.*?\]"""),
            """"assets": []""",
        )

        val update = UpdateChecker.parseRelease(withoutAsset, "1.1.0")

        assertEquals("1.2.0", update?.version)
        assertEquals(null, update?.downloadUrl)
        assertEquals("https://github.com/jingoujiao/ccsutSchedule/releases/tag/v1.2.0", update?.pageUrl)
    }

    @Test
    fun buildsDownloadSourcesPerSetting() {
        val githubUrl = "https://github.com/jingoujiao/ccsutSchedule/releases/download/v1.2.0/app.apk"
        val giteeUrl = "https://gitee.com/jingoujiao/ccsutSchedule/attach_files/12345/download/app.apk"
        val downloads = listOf(
            UpdateChecker.DownloadSource("Gitee 直连", giteeUrl),
            UpdateChecker.DownloadSource("GitHub 直连", githubUrl),
        )

        // 自动：Gitee 排第一，GitHub 与镜像在后
        val auto = UpdateChecker.buildSources(downloads, UpdateSource.AUTO, "")
        assertEquals("Gitee 直连", auto.first().label)
        assertEquals(giteeUrl, auto.first().url)
        assertTrue(auto.any { it.label == "GitHub 直连" })
        assertTrue(auto.any { it.url.endsWith(githubUrl) && it.url != githubUrl })

        // 只用 GitHub：GitHub 排第一，Gitee 兜底
        val githubOnly = UpdateChecker.buildSources(downloads, UpdateSource.GITHUB, "")
        assertEquals("GitHub 直连", githubOnly.first().label)
        assertEquals("Gitee 直连", githubOnly.last().label)

        // 镜像加速：镜像排第一
        val mirrored = UpdateChecker.buildSources(downloads, UpdateSource.MIRROR, "")
        assertTrue(mirrored.first().label !in setOf("GitHub 直连", "Gitee 直连"))

        // 自定义前缀会自动补上结尾斜杠
        val custom = UpdateChecker.buildSources(downloads, UpdateSource.CUSTOM, "https://my.mirror")
        assertEquals("自定义加速", custom.first().label)
        assertEquals("https://my.mirror/$githubUrl", custom.first().url)
    }

    @Test
    fun emptyCustomMirrorFallsBackToGiteeFirst() {
        val downloads = listOf(UpdateChecker.DownloadSource("Gitee 直连", "https://gitee.com/a/b"))

        val custom = UpdateChecker.buildSources(downloads, UpdateSource.CUSTOM, "   ")

        assertEquals(listOf("Gitee 直连"), custom.map { it.label })
    }

    @Test
    fun parsesGiteeReleasePayload() {
        val update = UpdateChecker.parseRelease(GITEE_RELEASE_JSON, "1.1.1")

        assertEquals("1.2.0", update?.version)
        assertEquals(1, update?.downloads?.size)
        assertEquals(
            "https://gitee.com/jingoujiao/ccsutSchedule/attach_files/1234/download/ccsutSchedule-1.2.0.apk",
            update?.downloads?.first()?.url,
        )
    }

    @Test
    fun giteeNewerUpdateIsLabeledAsGitee() {
        // 回归：曾经把 buildUpdate 的参数顺序写反，导致 Gitee 的地址被标成「GitHub 直连」
        val gitee = found("v1.2.2", "gitee", GITEE_APK, "Gitee 直连")

        val update = UpdateChecker.buildUpdate(github = null, gitee = gitee, currentVersion = "1.2.1")

        assertEquals("1.2.2", update?.version)
        assertEquals(listOf("Gitee 直连|$GITEE_APK"), update?.downloads?.map { "${it.label}|${it.url}" })
    }

    @Test
    fun githubNewerUpdateKeepsBothSourcesWhenSameTag() {
        val gitee = found("v1.2.2", "gitee", GITEE_APK, "Gitee 直连")
        val github = found("v1.2.2", "github", GITHUB_APK, "GitHub 直连")

        val update = UpdateChecker.buildUpdate(github = github, gitee = gitee, currentVersion = "1.2.1")

        assertEquals("1.2.2", update?.version)
        assertEquals(listOf("Gitee 直连", "GitHub 直连"), update?.downloads?.map { it.label })
        assertEquals(listOf(GITEE_APK, GITHUB_APK), update?.downloads?.map { it.url })
    }

    @Test
    fun newerGithubWinsWhenGiteeIsBehind() {
        val gitee = found("v1.2.1", "gitee", GITEE_APK, "Gitee 直连")
        val github = found("v1.2.2", "github", GITHUB_APK, "GitHub 直连")

        val update = UpdateChecker.buildUpdate(github = github, gitee = gitee, currentVersion = "1.2.1")

        assertEquals("1.2.2", update?.version)
        // Gitee 只有旧版本，不能混进来当下载源
        assertEquals(listOf("GitHub 直连|$GITHUB_APK"), update?.downloads?.map { "${it.label}|${it.url}" })
    }

    @Test
    fun noUpdateWhenBothAreOlderOrEqual() {
        val gitee = found("v1.2.1", "gitee", GITEE_APK, "Gitee 直连")
        val github = found("v1.1.0", "github", GITHUB_APK, "GitHub 直连")

        assertEquals(null, UpdateChecker.buildUpdate(github = github, gitee = gitee, currentVersion = "1.2.1"))
    }

    private fun found(tag: String, notes: String, apkUrl: String, label: String) = UpdateChecker.Found(
        tag = tag,
        notes = notes,
        pageUrl = "https://example.com/$tag",
        apkUrls = listOf(apkUrl),
        label = label,
    )

    private companion object {
        const val GITEE_APK = "https://gitee.com/jingoujiao/ccsut-schedule/releases/download/v1.2.2/app.apk"
        const val GITHUB_APK = "https://github.com/jingoujiao/ccsutSchedule/releases/download/v1.2.2/app.apk"

        val RELEASE_JSON = """
            {
              "tag_name": "v1.2.0",
              "name": "1.2.0",
              "body": "修了几个导入问题",
              "html_url": "https://github.com/jingoujiao/ccsutSchedule/releases/tag/v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "ccsutSchedule-1.2.0.apk",
                  "browser_download_url": "https://github.com/jingoujiao/ccsutSchedule/releases/download/v1.2.0/ccsutSchedule-1.2.0.apk",
                  "size": 12345678
                }
              ]
            }
        """.trimIndent()

        /** Gitee 的 Release 结构与 GitHub 基本一致。 */
        val GITEE_RELEASE_JSON = """
            {
              "id": 389969442,
              "tag_name": "v1.2.0",
              "target_commitish": "master",
              "prerelease": false,
              "name": "长工课程表 1.2.0",
              "body": "Gitee 上的说明",
              "html_url": "https://gitee.com/jingoujiao/ccsutSchedule/releases/v1.2.0",
              "assets": [
                {
                  "browser_download_url": "https://gitee.com/jingoujiao/ccsutSchedule/attach_files/1234/download/ccsutSchedule-1.2.0.apk",
                  "name": "ccsutSchedule-1.2.0.apk"
                }
              ]
            }
        """.trimIndent()
    }
}
