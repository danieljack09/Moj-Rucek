package com.mojrucek.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.mojrucek.app.MainActivity
import com.mojrucek.app.R
import android.app.PendingIntent
import android.content.Intent

object NotificationHelper {

    const val CHANNEL_ID = "sunday_reminder"
    const val EXPIRY_CHANNEL_ID = "expiry_reminder"
    private const val NOTIFICATION_ID = 1001
    private const val EXPIRY_NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_sunday),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_sunday_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                EXPIRY_CHANNEL_ID,
                context.getString(R.string.notification_channel_expiry),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_expiry_desc)
            }
        )
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showSundayReminder(context: Context) {
        if (!hasPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(context.getString(R.string.notification_sunday_title))
            .setContentText(context.getString(R.string.notification_sunday_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_sunday_text)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Shows one notification naming ingredients whose expiration date is coming up soon. */
    fun showExpiringIngredientsReminder(context: Context, ingredientNames: List<String>) {
        if (!hasPermission(context) || ingredientNames.isEmpty()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shown = ingredientNames.take(3)
        val remaining = ingredientNames.size - shown.size
        val text = if (remaining > 0) {
            "${shown.joinToString(", ")} и уште $remaining наближуваат кон рокот на траење."
        } else if (shown.size == 1) {
            "${shown.first()} наближува кон рокот на траење."
        } else {
            "${shown.joinToString(", ")} наближуваат кон рокот на траење."
        }

        val notification = NotificationCompat.Builder(context, EXPIRY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(context.getString(R.string.notification_expiry_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        androidx.core.app.NotificationManagerCompat.from(context).notify(EXPIRY_NOTIFICATION_ID, notification)
    }
}
