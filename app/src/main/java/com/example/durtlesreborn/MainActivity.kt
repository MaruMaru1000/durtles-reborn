package com.example.durtlesreborn

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.durtlesreborn.settings.AppSettings
import com.example.durtlesreborn.ui.AppRoot
import com.example.durtlesreborn.ui.AppTheme
import com.example.durtlesreborn.ui.DurtlesTheme
import com.example.durtlesreborn.ui.LocalStrokeRepository
import com.example.durtlesreborn.ui.MainViewModel
import com.example.durtlesreborn.ui.isDarkSurface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as DurtlesApp).container)
    }

    /** After the user answers, show or hide the notification straight away. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        lifecycleScope.launch { (application as DurtlesApp).container.notifier.update() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by (application as DurtlesApp).container.settingsStore.settings.collectAsState(initial = AppSettings())
            val theme = AppTheme.fromName(settings.theme, AppTheme.DYNAMIC)

            // System bar icons must suit the theme in use, which can differ from the system's own mode.
            val systemDark = isSystemInDarkTheme()
            val dark = isDarkSurface(theme, systemDark)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            DurtlesTheme(theme) {
                CompositionLocalProvider(LocalStrokeRepository provides (application as DurtlesApp).container.strokeRepository) {
                    AppRoot(viewModel)
                }
            }
        }
        askForNotificationPermission()
    }

    /** Each time the app is opened, look for a newer release (GitHub build only; the updater limits how often). */
    override fun onStart() {
        super.onStart()
        val container = (application as DurtlesApp).container
        val updates = container.appUpdates ?: return
        lifecycleScope.launch {
            if (container.settingsStore.current().checkForUpdates) updates.check()
        }
    }

    /** Android 13 and later need the user's permission to show notifications; ask once, if they are switched on. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            val container = (application as DurtlesApp).container
            // Only ask someone who is signed in and has notifications on.
            if (container.tokenStore.current().isNullOrBlank() || !container.settingsStore.current().enableNotifications) return@launch
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
