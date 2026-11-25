package com.example.durtlesreborn.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseInfoTest {
    private val base = "https://github.com/$RELEASE_REPO/releases/download/v1.0.2"

    private fun releaseJson(vararg assets: Pair<String, String>) = """
        {
          "tag_name": "v1.0.2",
          "body": "## Fixes\n- Something",
          "draft": false,
          "assets": [
            ${assets.joinToString(",") { (name, url) -> """{"name": "$name", "browser_download_url": "$url", "size": 1234}""" }}
          ]
        }
    """.trimIndent()

    @Test
    fun readsTheReleaseAndItsFiles() {
        val release = parseRelease(
            releaseJson(
                "durtles-reborn-1.0.2-3.apk" to "$base/durtles-reborn-1.0.2-3.apk",
                "SHA256SUMS.txt" to "$base/SHA256SUMS.txt",
            ),
        )!!
        assertEquals("v1.0.2", release.tag)
        assertEquals("## Fixes\n- Something", release.notes)
        assertEquals(2, release.assets.size)
        assertEquals(1234L, release.assets[0].size)
    }

    @Test
    fun notAReleaseIsNull() {
        assertNull(parseRelease("""{"message": "Not Found"}"""))
        assertNull(parseRelease("<html>"))
    }

    @Test
    fun theApkNameCarriesTheVersion() {
        assertEquals("1.0.1" to 2L, apkVersion("durtles-reborn-1.0.1-2.apk"))
        assertEquals("1.2.0-beta-1" to 7L, apkVersion("durtles-reborn-1.2.0-beta-1-7.apk"))
        assertNull(apkVersion("durtles-reborn-1.0.1-2.aab"))
        assertNull(apkVersion("other-app-1.0-2.apk"))
    }

    @Test
    fun aNewerVersionCodeIsAnUpdate() {
        val release = parseRelease(
            releaseJson(
                "durtles-reborn-1.0.2-3.aab" to "$base/durtles-reborn-1.0.2-3.aab",
                "durtles-reborn-1.0.2-3.apk" to "$base/durtles-reborn-1.0.2-3.apk",
                "SHA256SUMS.txt" to "$base/SHA256SUMS.txt",
            ),
        )!!
        val update = newerUpdate(release, installedCode = 2)
        assertNotNull(update)
        assertEquals("1.0.2", update!!.update.versionName)
        assertEquals(3L, update.update.versionCode)
        assertEquals("durtles-reborn-1.0.2-3.apk", update.apk.name)
        assertEquals("SHA256SUMS.txt", update.sums?.name)
    }

    @Test
    fun theSameOrAnOlderVersionIsNoUpdate() {
        val release = parseRelease(releaseJson("durtles-reborn-1.0.2-3.apk" to "$base/durtles-reborn-1.0.2-3.apk"))!!
        assertNull(newerUpdate(release, installedCode = 3))
        assertNull(newerUpdate(release, installedCode = 4))
        assertEquals("1.0.2", releaseVersionName(release))
    }

    @Test
    fun filesHostedElsewhereAreIgnored() {
        val release = parseRelease(
            releaseJson(
                "durtles-reborn-9.0-99.apk" to "https://example.com/durtles-reborn-9.0-99.apk",
                "durtles-reborn-1.0.2-3.apk" to "https://github.com/someone-else/durtles-reborn/releases/download/v1/durtles-reborn-1.0.2-3.apk",
            ),
        )!!
        assertNull(newerUpdate(release, installedCode = 2))
    }

    @Test
    fun aReleaseWithoutChecksumsStillOffersTheUpdate() {
        val release = parseRelease(releaseJson("durtles-reborn-1.0.2-3.apk" to "$base/durtles-reborn-1.0.2-3.apk"))!!
        val update = newerUpdate(release, installedCode = 2)!!
        assertNull(update.sums)
    }

    @Test
    fun onlyGithubHostsServeDownloads() {
        assertTrue(isGithubHost("github.com"))
        assertTrue(isGithubHost("objects.githubusercontent.com"))
        assertTrue(isGithubHost("release-assets.githubusercontent.com"))
        assertFalse(isGithubHost("evilgithub.com"))
        assertFalse(isGithubHost("githubusercontent.com.example.com"))
    }

    @Test
    fun readsTheChecksumFile() {
        val text = "﻿E63BF174C08EE8034B19A73E812F55EAF21656C7C6EBC9C987CD0F49BCC2CE95  durtles-reborn-1.0.1-2.apk\r\n" +
            "80030acca651cffeae2791cc5e670c92c1320484e5e34beeb e95580f765ad2ad  broken.aab\n" +
            "80030ACCA651CFFEAE2791CC5E670C92C1320484E5E34BEEBE95580F765AD2AD *durtles-reborn-1.0.1-2.aab\n"
        val sums = parseSums(text)
        assertEquals("e63bf174c08ee8034b19a73e812f55eaf21656c7c6ebc9c987cd0f49bcc2ce95", sums["durtles-reborn-1.0.1-2.apk"])
        assertEquals("80030acca651cffeae2791cc5e670c92c1320484e5e34beebe95580f765ad2ad", sums["durtles-reborn-1.0.1-2.aab"])
        assertNull(sums["broken.aab"])
    }
}
