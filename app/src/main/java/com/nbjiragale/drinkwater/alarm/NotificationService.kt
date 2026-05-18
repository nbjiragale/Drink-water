package com.nbjiragale.drinkwater.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nbjiragale.drinkwater.DrinkReminderFullScreenActivity
import com.nbjiragale.drinkwater.MainActivity
import com.nbjiragale.drinkwater.R

object NotificationService {

    private const val TAG = "NotificationService"
    const val CHANNEL_ID = "water_reminders_channel"
    const val NOTIFICATION_ID = 9002

    /** Broadcast action: user tapped "I drank" on the reminder notification. */
    const val ACTION_DRINK_LOGGED = "com.nbjiragale.drinkwater.action.DRINK_LOGGED"

    /** Broadcast action: user tapped "Snooze 10 min" on the reminder notification. */
    const val ACTION_SNOOZE = "com.nbjiragale.drinkwater.action.SNOOZE"

    /** Snooze postpones the next reminder by this many ms past `now`. */
    const val SNOOZE_DURATION_MS = 10 * 60 * 1000L

    private const val REQUEST_CODE_CONTENT = 0
    private const val REQUEST_CODE_FSI = 1
    private const val REQUEST_CODE_DRINK_ACTION = 100
    private const val REQUEST_CODE_SNOOZE_ACTION = 101

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(context: Context) {
        createNotificationChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent =
            PendingIntent.getActivity(context, REQUEST_CODE_CONTENT, launchIntent, piFlags)

        val fsiIntent = Intent(context, DrinkReminderFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fsiPendingIntent =
            PendingIntent.getActivity(context, REQUEST_CODE_FSI, fsiIntent, piFlags)

        val drinkPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DRINK_ACTION,
            Intent(context, DrinkActionReceiver::class.java).setAction(ACTION_DRINK_LOGGED),
            piFlags
        )
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SNOOZE_ACTION,
            Intent(context, DrinkActionReceiver::class.java).setAction(ACTION_SNOOZE),
            piFlags
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_water_drop,
                context.getString(R.string.notification_action_drank),
                drinkPendingIntent
            )
            .addAction(
                R.drawable.ic_water_drop,
                context.getString(R.string.notification_action_snooze),
                snoozePendingIntent
            )

        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fsiPendingIntent, true)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Reminder notification displayed.")
        } catch (e: SecurityException) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted.", e)
        }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } else true
    }
}
