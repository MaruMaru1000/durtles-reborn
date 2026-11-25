package com.example.durtlesreborn.update

import android.content.Context
import okhttp3.OkHttpClient

/** The GitHub build updates itself from the repository's releases. */
object AppUpdatesFactory {
    /** The process's updater, so the install receiver can report back to it. */
    @Volatile
    internal var active: GithubUpdater? = null
        private set

    fun create(context: Context, http: OkHttpClient): AppUpdates? =
        GithubUpdater(context.applicationContext, http).also { active = it }
}
