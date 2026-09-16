package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.UpdateChecker
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

    private companion object {
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
    }
}
