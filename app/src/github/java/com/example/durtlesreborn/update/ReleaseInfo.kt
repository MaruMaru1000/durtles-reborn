package com.example.durtlesreborn.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The GitHub repository whose releases carry the signed APK. */
internal const val RELEASE_REPO = "MaruMaru1000/durtles-reborn"
internal const val LATEST_RELEASE_URL = "https://api.github.com/repos/$RELEASE_REPO/releases/latest"
private const val DOWNLOAD_PREFIX = "https://github.com/$RELEASE_REPO/releases/download/"

/** Release APKs are named `durtles-reborn-<versionName>-<versionCode>.apk`. */
private val APK_NAME = Regex("""^durtles-reborn-(.+)-(\d+)\.apk$""")
internal const val SUMS_NAME = "SHA256SUMS.txt"

internal data class ReleaseAsset(val name: String, val url: String, val size: Long)

internal data class Release(val tag: String, val notes: String, val assets: List<ReleaseAsset>)

/** A release that is newer than the installed app, with the files needed to install it. */
internal data class ReleaseUpdate(val update: AvailableUpdate, val apk: ReleaseAsset, val sums: ReleaseAsset?)

private val json = Json { ignoreUnknownKeys = true }

/** Reads GitHub's "latest release" answer; null if it is not one. */
internal fun parseRelease(body: String): Release? {
    val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
    val tag = root.str("tag_name") ?: return null
    val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
        val asset = element as? JsonObject ?: return@mapNotNull null
        val name = asset.str("name") ?: return@mapNotNull null
        val url = asset.str("browser_download_url") ?: return@mapNotNull null
        ReleaseAsset(name, url, (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0)
    }
    return Release(tag, root.str("body").orEmpty(), assets)
}

/** Version name and code from a release APK's file name, or null if it is not one. */
internal fun apkVersion(name: String): Pair<String, Long>? =
    APK_NAME.matchEntire(name)?.let { it.groupValues[1] to (it.groupValues[2].toLongOrNull() ?: return null) }

/** Only files from this repository's own releases are downloaded. */
internal fun isReleaseDownload(url: String) = url.startsWith(DOWNLOAD_PREFIX) && url.toHttpUrlOrNull() != null

/** GitHub serves release files from github.com, redirecting to its own asset host. */
internal fun isGithubHost(host: String) = host == "github.com" || host.endsWith(".githubusercontent.com")

/** The update in [release] if its APK is newer than [installedCode]. */
internal fun newerUpdate(release: Release, installedCode: Long): ReleaseUpdate? {
    val apk = release.assets
        .filter { isReleaseDownload(it.url) }
        .mapNotNull { asset -> apkVersion(asset.name)?.let { asset to it } }
        .maxByOrNull { (_, version) -> version.second }
        ?: return null
    val (asset, version) = apk
    if (version.second <= installedCode) return null
    val sums = release.assets.firstOrNull { it.name == SUMS_NAME && isReleaseDownload(it.url) }
    return ReleaseUpdate(AvailableUpdate(version.first, version.second, release.notes, asset.size), asset, sums)
}

/** The version a release offers, for "up to date" messages. */
internal fun releaseVersionName(release: Release): String =
    release.assets.firstNotNullOfOrNull { apkVersion(it.name)?.first } ?: release.tag.removePrefix("v")

/** `SHA256SUMS.txt` lines (`<hex>  <file>`, as written by sha256sum) as file name to lower-case digest. */
internal fun parseSums(text: String): Map<String, String> =
    text.removePrefix("﻿").lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("""\s+"""), limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val digest = parts[0].lowercase()
        if (digest.length != 64 || digest.any { it !in "0123456789abcdef" }) return@mapNotNull null
        parts[1].removePrefix("*").trim() to digest
    }.toMap()
