package com.sabalapps.arrowescape.notification

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** What the reminder needs from the system, in one place. */
object NotificationPermission {

    /** Android 13+ asks at runtime; before that, notifications are allowed unless the player switched them off. */
    val needsRuntimeRequest: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The runtime permission's manifest name — a literal, so it also compiles against any lower `compileSdk`. */
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

    /**
     * Whether a notification posted now would be shown. The one question the reminder cares
     * about: it covers the Android 13 permission *and* the per-app switch in system settings
     * *and* a blocked channel group, which a bare permission check does not.
     */
    fun isAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The system's notification settings for this app: where "not now, ever" is undone. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
