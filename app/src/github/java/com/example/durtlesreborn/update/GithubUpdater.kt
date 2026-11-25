package com.example.durtlesreborn.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val TAG = "AppUpdate"

/** How long a check on opening the app is reused before GitHub is asked again. */
private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L

/** A checksum file is a few lines; anything larger is not one. */
private const val MAX_SUMS_BYTES = 64 * 1024

/**
 * Checks this repository's latest GitHub release and installs a newer APK with Android's package
 * installer. Every download is checked against the release's SHA256SUMS.txt and against the
 * installed app (same package, the promised version code, the same signing certificate) before
 * Android is asked to install it; Android refuses an APK signed with another key in any case.
 */
class GithubUpdater(
    private val context: Context,
    private val http: OkHttpClient,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) : AppUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    override val status: StateFlow<UpdateStatus> = _status.asStateFlow()
    private val _confirmation = MutableStateFlow<Intent?>(null)
    override val confirmation: StateFlow<Intent?> = _confirmation.asStateFlow()

    private val installed: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    private val installedCode = PackageInfoCompat.getLongVersionCode(installed)
    override val currentVersion: String = installed.versionName ?: installedCode.toString()

    private val downloads = File(context.cacheDir, "updates")

    @Volatile private var found: ReleaseUpdate? = null
    @Volatile private var lastCheck = 0L
    private var job: Job? = null

    override fun check(force: Boolean) {
        val now = System.currentTimeMillis()
        if (job?.isActive == true) return
        if (!force && now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        job = scope.launch {
            _status.value = UpdateStatus.Checking
            _status.value = try {
                fetchLatest(requested = force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                UpdateStatus.Failed("Could not reach GitHub to check for updates.", null)
            }
        }
    }

    private fun fetchLatest(requested: Boolean): UpdateStatus {
        val request = Request.Builder().url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DurtlesReborn-Updater")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return UpdateStatus.UpToDate(null)
            if (!response.isSuccessful) return UpdateStatus.Failed("GitHub answered with HTTP ${response.code}.", null)
            val release = parseRelease(response.body.string())
                ?: return UpdateStatus.Failed("GitHub sent a release this app could not read.", null)
            val update = newerUpdate(release, installedCode)
            found = update
            removeOldDownloads(update)
            return if (update == null) UpdateStatus.UpToDate(releaseVersionName(release)) else UpdateStatus.Available(update.update, requested)
        }
    }

    override fun install() {
        val update = found ?: return
        if (job?.isActive == true) return
        if (isDebugBuild()) {
            _status.value = UpdateStatus.Failed(
                "This is a debug build, signed with a different key from the GitHub releases, so it cannot update itself. Install the release APK once and updates will work from then on.",
                update.update,
            )
            return
        }
        if (installPermissionIntent() != null) {
            _status.value = UpdateStatus.NeedsPermission(update.update)
            return
        }
        job = scope.launch {
            try {
                val apk = download(update)
                verify(apk, update)
                _status.value = UpdateStatus.Installing(update.update)
                commit(apk)
            } catch (e: CancellationException) {
                _status.value = UpdateStatus.Available(update.update)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update failed", e)
                _status.value = UpdateStatus.Failed(e.message ?: "The update could not be installed.", update.update)
            }
        }
    }

    override fun cancel() {
        job?.cancel()
        found?.let { _status.value = UpdateStatus.Available(it.update) }
    }

    override fun installPermissionIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if (context.packageManager.canRequestPackageInstalls()) return null
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    }

    override fun confirmationShown() {
        _confirmation.value = null
    }

    /** Called by [UpdateInstallReceiver] with the package installer's answer. */
    internal fun onInstallResult(status: Int, message: String?, confirm: Intent?) {
        val update = found?.update
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> _confirmation.value = confirm
            PackageInstaller.STATUS_SUCCESS -> Unit // Android replaces the app and stops this process.
            PackageInstaller.STATUS_FAILURE_ABORTED -> _status.value = update?.let { UpdateStatus.Available(it) } ?: UpdateStatus.Idle
            else -> _status.value = UpdateStatus.Failed(installFailure(status, message), update)
        }
    }

    private fun installFailure(status: Int, message: String?) = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        -> "Android refused the update because it does not match the installed app (${message ?: "conflict"})."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough storage space to install the update."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
        else -> "The update could not be installed${message?.let { " ($it)" }.orEmpty()}."
    }

    /** Downloads the APK into the cache, reusing a finished download whose checksum still matches. */
    private suspend fun download(update: ReleaseUpdate): File {
        val expected = expectedDigest(update)
        downloads.mkdirs()
        val target = File(downloads, update.apk.name)
        if (target.isFile && sha256(target) == expected) return target
        val part = File(downloads, update.apk.name + ".part")
        val total = update.apk.size
        _status.value = UpdateStatus.Downloading(update.update, 0, total)
        get(update.apk.url).use { response ->
            if (!response.isSuccessful) throw IOException("The download failed with HTTP ${response.code}.")
            val length = response.body.contentLength().takeIf { it > 0 } ?: total
            val digest = MessageDigest.getInstance("SHA-256")
            response.body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done - shown >= 256 * 1024) {
                            shown = done
                            _status.value = UpdateStatus.Downloading(update.update, done, length)
                        }
                    }
                }
            }
            if (digest.digest().toHex() != expected) {
                part.delete()
                throw IOException("The download does not match the release's checksum, so it was not installed.")
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not save the download.")
        return target
    }

    /** The APK's SHA-256 from the release's checksum file. A release without one is not installed. */
    private fun expectedDigest(update: ReleaseUpdate): String {
        val sums = update.sums ?: throw IOException("The release has no checksum file, so the download cannot be checked.")
        val text = get(sums.url).use { response ->
            if (!response.isSuccessful) throw IOException("Could not download the checksum file (HTTP ${response.code}).")
            val bytes = response.body.byteStream().use { readAtMost(it, MAX_SUMS_BYTES + 1) }
            if (bytes.size > MAX_SUMS_BYTES) throw IOException("The checksum file is too large.")
            bytes.toString(Charsets.UTF_8)
        }
        return parseSums(text)[update.apk.name] ?: throw IOException("The checksum file does not list ${update.apk.name}.")
    }

    private fun get(url: String): Response {
        val response = http.newCall(Request.Builder().url(url).header("User-Agent", "DurtlesReborn-Updater").build()).execute()
        // Redirects are followed, but only to GitHub's own hosts over HTTPS.
        val finalUrl = response.request.url
        if (!finalUrl.isHttps || !isGithubHost(finalUrl.host)) {
            response.close()
            throw IOException("The download was redirected away from GitHub, so it was stopped.")
        }
        return response
    }

    /** The APK must be this app, at the version the release promised, signed with the same key. */
    @Suppress("DEPRECATION")
    private fun verify(apk: File, update: ReleaseUpdate) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw IOException("The download is not a valid app package.")
        if (archive.packageName != context.packageName) throw IOException("The download is a different app, so it was not installed.")
        if (PackageInfoCompat.getLongVersionCode(archive) != update.update.versionCode) {
            throw IOException("The download's version does not match the release, so it was not installed.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val theirs = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val ours = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            if (theirs != null && ours != null && theirs != ours) {
                throw IOException("The download is signed with a different key from this app, so it was not installed.")
            }
        }
    }

    /** Hands the APK to Android's package installer; the answer arrives in [UpdateInstallReceiver]. */
    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // From Android 12 an app that installed itself can update itself without a prompt.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            session.commit(result.intentSender)
        }
    }

    /** Deletes downloads for versions that are installed already or no longer offered. */
    private fun removeOldDownloads(keep: ReleaseUpdate?) {
        downloads.listFiles()?.forEach { file ->
            if (keep == null || !file.name.startsWith(keep.apk.name)) file.delete()
        }
    }

    private fun isDebugBuild() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/** Up to [limit] bytes of [input]; InputStream.readNBytes needs API 33. */
private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
