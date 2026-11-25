package com.example.durtlesreborn.update

import android.content.Context
import okhttp3.OkHttpClient

/** Store builds (F-Droid, Play) are updated by the store, so they never update themselves. */
object AppUpdatesFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, http: OkHttpClient): AppUpdates? = null
}
