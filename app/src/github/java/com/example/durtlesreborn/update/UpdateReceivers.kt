package com.example.durtlesreborn.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import com.example.durtlesreborn.MainActivity
import com.example.durtlesreborn.R
import com.example.durtlesreborn.notify.AndroidNotificationSink

/** Receives the package installer's answer to an update and passes it to the updater. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        val updater = AppUpdatesFactory.active
        if (updater != null) {
            updater.onInstallResult(status, message, confirm)
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && confirm != null) {
            // The screen that asked is gone; show Android's confirmation directly.
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** After an update the app is not running; a notification says it worked and reopens it. */
class UpdateCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!AndroidNotificationSink.canPost(context)) return
        val channel = "app_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channel, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Says when the app has updated itself"
                },
            )
        }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Updated${version?.let { " to $it" }.orEmpty()}")
            .setContentText("Tap to open the app.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATED_NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val UPDATED_NOTIFICATION_ID = 2
    }
}
