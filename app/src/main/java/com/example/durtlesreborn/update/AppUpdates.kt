package com.example.durtlesreborn.update

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** A newer release found on GitHub. */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Long,
    /** The release notes, as written on GitHub (Markdown). */
    val notes: String,
    val sizeBytes: Long,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val latestName: String?) : UpdateStatus
    /** [requested] is true when the user asked for this check, so it is offered even with automatic checks off. */
    data class Available(val update: AvailableUpdate, val requested: Boolean = false) : UpdateStatus
    data class Downloading(val update: AvailableUpdate, val done: Long, val total: Long) : UpdateStatus

    /** The app must be allowed to install apps (Android's "Install unknown apps") before it can update itself. */
    data class NeedsPermission(val update: AvailableUpdate) : UpdateStatus
    data class Installing(val update: AvailableUpdate) : UpdateStatus
    data class Failed(val message: String, val update: AvailableUpdate?) : UpdateStatus
}

/**
 * Updating the app from its GitHub releases. Only the "github" build has an implementation; the
 * store build gets null from [AppUpdatesFactory] and shows nothing.
 */
interface AppUpdates {
    val status: StateFlow<UpdateStatus>

    /** The installed version, as users see it. */
    val currentVersion: String

    /** Set when Android asks the user to confirm the install; the screen shows it and calls [confirmationShown]. */
    val confirmation: StateFlow<Intent?>

    /** Looks for a newer release. Without [force], does nothing if the last check was recent. */
    fun check(force: Boolean = false)

    /** Downloads, verifies and installs the update found by the last check. */
    fun install()

    /** Stops a download in progress. */
    fun cancel()

    /** The system page where this app is allowed to install apps, or null when no permission is needed. */
    fun installPermissionIntent(): Intent?

    fun confirmationShown()
}
