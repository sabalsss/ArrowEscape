package com.sabalapps.arrowescape.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sabalapps.arrowescape.MainActivity
import com.sabalapps.arrowescape.R
import com.sabalapps.arrowescape.startup.LaunchIntents
import com.sabalapps.arrowescape.startup.StartupDestination

/**
 * The reminder as the system sees it: one channel, one notification id, one tap target.
 *
 * It never names the shape. "Today's mystery shape is ready" is the whole message — the
 * mystery is what the game is about, and the notification shade is the last place to spoil it.
 */
object ReminderNotifier {

    /** One stable channel: the player can silence the reminder from system settings without losing the game's other behaviour. */
    const val CHANNEL_ID = "daily_mystery"

    /**
     * One stable id. A second notification would *replace* the first rather than stack beside
     * it, so a missed day never leaves two reminders in the shade.
     */
    const val NOTIFICATION_ID = 1001

    /** Creates the channel (API 26+). Idempotent: safe at every app start and before every post. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                // Default, not high: a daily nudge is not an interruption.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            }
        )
    }

    /** Tapping it opens the game on the Daily Challenge. */
    fun openDailyIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(LaunchIntents.EXTRA_DESTINATION, StartupDestination.DAILY_EXTRA_VALUE)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

    /**
     * Posts the reminder. Returns whether it was handed to the system — false when
     * notifications are not allowed, which is the player's call and not an error.
     */
    fun post(context: Context): Boolean {
        if (!NotificationPermission.isAllowed(context)) return false
        ensureChannel(context)

        val tap = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openDailyIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_arrow)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the call.
            false
        }
    }

    private const val REQUEST_CODE = 7001
}
