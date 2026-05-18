package com.nbjiragale.drinkwater.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nbjiragale.drinkwater.DrinkReminderFullScreenActivity
import com.nbjiragale.drinkwater.MainActivity
import com.nbjiragale.drinkwater.R
import com.nbjiragale.drinkwater.util.PreferencesManager

object NotificationService {

    private const val TAG = "NotificationService"
    private const val CHANNEL_ID_BASE = "water_reminders_channel"
    const val NOTIFICATION_ID = 9002

    /**
     * Channel ID for the currently-configured sound. Encodes the sound version so picking a new
     * tone produces a brand-new channel — Android does not allow editing a channel's sound after
     * creation.
     */
    fun channelId(context: Context): String {
        val version = PreferencesManager(context).notificationChannelVersion
        return "${CHANNEL_ID_BASE}_v$version"
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = PreferencesManager(context)
        val activeId = channelId(context)

        // Drop any stale channels (older versions + the legacy un-versioned channel from earlier
        // releases) so the user does not see orphaned entries in system settings.
        manager.notificationChannels.forEach { existing ->
            if (existing.id != activeId && existing.id.startsWith(CHANNEL_ID_BASE)) {
                manager.deleteNotificationChannel(existing.id)
            }
        }

        if (manager.getNotificationChannel(activeId) != null) return

        val soundUri = resolveSoundUri(prefs.reminderSoundUri)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            activeId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Persists [soundUri] and recreates the notification channel so the new tone takes effect
     * immediately. Pass `null` for the system-default sound, or [PreferencesManager.SOUND_URI_SILENT]
     * for a silent channel.
     */
    fun applyReminderSoundChange(context: Context, soundUri: String?) {
        val prefs = PreferencesManager(context)
        prefs.reminderSoundUri = soundUri
        prefs.notificationChannelVersion = prefs.notificationChannelVersion + 1
        createNotificationChannel(context)
    }

    /** Resolves the stored preference string into the URI used by the channel and pre-O notifications. */
    fun resolveSoundUri(stored: String?): Uri? = when (stored) {
        null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        PreferencesManager.SOUND_URI_SILENT -> null
        else -> Uri.parse(stored)
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
        val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, piFlags)

        val fsiIntent = Intent(context, DrinkReminderFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fsiPendingIntent = PendingIntent.getActivity(context, 1, fsiIntent, piFlags)

        val builder = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Pre-O devices ignore channel-level sound, so apply the chosen tone directly on the builder.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val prefs = PreferencesManager(context)
            builder.setSound(resolveSoundUri(prefs.reminderSoundUri))
        }

        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fsiPendingIntent, true)
        }

        val notification = builder.build()
        // FLAG_INSISTENT makes the notification's sound (channel-level on O+, builder-level pre-O)
        // loop until the user interacts with the notification. Opt-in via the reminder sound card.
        if (PreferencesManager(context).reminderSoundLoop) {
            notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
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
