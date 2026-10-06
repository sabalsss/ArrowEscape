package com.sabalapps.arrowescape.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sabalapps.arrowescape.notification.NotificationPermission
import com.sabalapps.arrowescape.notification.ReminderServices
import com.sabalapps.arrowescape.retention.RetentionCoordinator
import com.sabalapps.arrowescape.retention.StoreActions

/**
 * Everything the screens need to ask the player for something and to act on the answer:
 * the prompt decisions ([coordinator]), the daily reminder switch, Rate and Share.
 *
 * It is the one place that knows how to turn the reminder on *properly* — permission asked
 * at the player's request, and only when the system will actually show the dialog — so the
 * Settings switch and the "Want a daily mystery?" offer behave identically.
 */
@Stable
class RetentionUi internal constructor(
    val coordinator: RetentionCoordinator,
    private val reminders: ReminderServices,
    private val context: Context
) {
    /** The player's Daily Reminder choice, for the Settings row. */
    val reminderPreference get() = reminders.preferences.preference

    /** Whether a notification would be shown right now. Refreshed whenever the game returns to the foreground. */
    var notificationsAllowed by mutableStateOf(NotificationPermission.isAllowed(context))
        private set

    internal var launchPermissionRequest: (() -> Unit)? = null

    /** The player asked for the reminder and the system had to be asked (or visited) first. */
    private var enableWhenAllowed = false

    fun refresh() {
        notificationsAllowed = NotificationPermission.isAllowed(context)
        // Back from system settings with notifications now on, having asked for the reminder.
        if (enableWhenAllowed && notificationsAllowed) {
            enableWhenAllowed = false
            reminders.controller.enable()
        }
    }

    internal fun onPermissionResult(granted: Boolean) {
        refresh()
        if (granted && enableWhenAllowed) {
            enableWhenAllowed = false
            reminders.controller.enable()
        }
        // Denied: nothing else happens. No second dialog, no nagging — the Settings switch is
        // where it can be tried again.
        if (!granted) enableWhenAllowed = false
    }

    /**
     * The Daily Reminder switch. Off is immediate. On is immediate if notifications are
     * allowed; otherwise it asks the system once, in the way Android allows — the runtime
     * dialog while it will still show, the app's notification settings after that.
     */
    fun setReminderEnabled(on: Boolean) {
        if (!on) {
            enableWhenAllowed = false
            reminders.controller.disable()
            return
        }
        refresh()
        if (notificationsAllowed) {
            reminders.controller.enable()
            return
        }
        enableWhenAllowed = true
        val asked = reminders.preferences.preference.value.permissionAsked
        val activity = context.findActivity()
        val systemWillAsk = NotificationPermission.needsRuntimeRequest &&
            launchPermissionRequest != null &&
            (!asked || (activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, NotificationPermission.PERMISSION)))
        if (systemWillAsk) {
            reminders.preferences.markPermissionAsked()
            launchPermissionRequest?.invoke()
        } else {
            runCatching { context.startActivity(NotificationPermission.settingsIntent(context)) }
        }
    }

    /** Rate: Google's review flow, the listing as the fallback. Recorded as an action tapped — not as a rating. */
    fun rate() {
        coordinator.onRateActionTapped()
        val activity = context.findActivity()
        if (activity != null) StoreActions.rate(activity) else StoreActions.openListing(context)
    }

    /** Share: always the system chooser. */
    fun share() {
        coordinator.onShareActionTapped()
        StoreActions.share(context)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Builds the [RetentionUi] for the composition and keeps it in step with the lifecycle and the system permission dialog. */
@Composable
fun rememberRetentionUi(
    coordinator: RetentionCoordinator,
    reminders: ReminderServices
): RetentionUi {
    val context = LocalContext.current
    val ui = remember(coordinator, reminders, context) { RetentionUi(coordinator, reminders, context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui.onPermissionResult(granted)
    }
    SideEffect { ui.launchPermissionRequest = { launcher.launch(NotificationPermission.PERMISSION) } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ui) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ui.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return ui
}

/** The screens' handle on retention, or null in previews and tests that have none. */
val LocalRetentionUi = compositionLocalOf<RetentionUi?> { null }
