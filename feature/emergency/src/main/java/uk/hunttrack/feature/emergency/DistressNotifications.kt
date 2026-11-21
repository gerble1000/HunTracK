package uk.hunttrack.feature.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Ongoing distress notification. Tapping it is the user bringing HunTracK back;
 * the app does not launch itself from the background.
 */
object DistressNotifications {
    private const val PREFS = "distress_mode"
    private const val KEY_ARMED = "armed"
    private const val CHANNEL_ID = "distress_mode"
    private const val NOTIFICATION_ID = 7101

    fun isArmed(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ARMED, false)

    /** Call when Emergency text is tapped and a contact number is available. */
    fun arm(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARMED, true)
            .apply()
        show(context)
    }

    fun stop(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARMED, false)
            .apply()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        DistressMode.hideBeacon()
    }

    fun show(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Distress mode",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Tap to return to the flashing beacon"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        val open = Intent().setClassName(appContext.packageName, "uk.hunttrack.MainActivity").apply {
            putExtra(DistressMode.EXTRA_SHOW_BEACON, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = appContext.getString(R.string.distress_mode_notification)
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(text)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pending)
            .setAutoCancel(false)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
