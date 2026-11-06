/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.feed.notifications.ScheduleOptions
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.settings.notifications.NotificationModeConfigFragment

/**
 * YewPlay: notifications, in Compose: the player notification, the notifications of new videos
 * of the subscriptions (how often, on which network, which channels). Warns when the device
 * has turned the notifications of the app off.
 */
class NotificationsSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.notifications

    private var loader: Disposable? = null
    private var channelsSummary: String? = null
    private var deviceEnabled = true

    private val scheduler = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val context = context ?: return@OnSharedPreferenceChangeListener
        if (key == getString(R.string.streams_notifications_interval_key) ||
            key == getString(R.string.streams_notifications_network_key)
        ) {
            // apply the new configuration
            NotificationWorker.schedule(context, ScheduleOptions.from(context), true)
        } else if (key == getString(R.string.enable_streams_notifications)) {
            if (NotificationHelper.areNewStreamsNotificationsEnabled(context)) {
                NotificationWorker.schedule(context)
            } else {
                // turned off: cancel the worker to save energy; a new one starts when turned on
                NotificationWorker.cancel(context)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.registerOnSharedPreferenceChangeListener(scheduler)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(scheduler)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        deviceEnabled = NotificationHelper.areNotificationsEnabledOnDevice(requireContext())
        loader?.dispose()
        loader = SubscriptionManager(requireContext())
            .subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { subscriptions ->
                    val notified = subscriptions.count { it.notificationMode != NotificationMode.DISABLED }
                    channelsSummary = "$notified/${subscriptions.size}"
                    refresh()
                },
                { ErrorUtil.showSnackbar(this, ErrorInfo(it, UserAction.SUBSCRIPTION_GET, "Get subscriptions list")) }
            )
        refresh()
    }

    override fun onPause() {
        loader?.dispose()
        loader = null
        super.onPause()
    }

    override fun YpPrefBuilder.buildItems() {
        // on Android 13 the player notification is exempt from the notification settings
        val allEnabled = deviceEnabled || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (!deviceEnabled) {
            action(title = R.string.notifications_disabled, summary = R.string.settings) {
                NotificationHelper.openNewPipeSystemNotificationSettings(requireContext())
            }
        }
        action(
            title = R.string.settings_category_player_notification_title,
            summary = R.string.settings_category_player_notification_summary,
            key = R.string.player_notification_screen_key,
            enabled = allEnabled
        ) { openScreen(PlayerNotificationSettingsFragment::class.java) }
        switch(
            key = R.string.enable_streams_notifications,
            title = R.string.enable_streams_notifications_title,
            summary = R.string.enable_streams_notifications_summary,
            default = false,
            enabled = deviceEnabled
        )
        durationChoice(
            key = R.string.streams_notifications_interval_key,
            title = R.string.streams_notifications_interval_title,
            entries = R.array.streams_notifications_interval_description,
            values = R.array.streams_notifications_interval_values,
            default = R.string.streams_notifications_interval_default,
            dependency = R.string.enable_streams_notifications,
            enabled = deviceEnabled
        )
        choice(
            key = R.string.streams_notifications_network_key,
            title = R.string.streams_notifications_network_title,
            entries = R.array.streams_notifications_network_description,
            values = R.array.streams_notifications_network_values,
            default = R.string.streams_notifications_network_default,
            dependency = R.string.enable_streams_notifications,
            enabled = deviceEnabled
        )
        action(
            title = R.string.channels,
            key = R.string.streams_notifications_channels_key,
            summaryText = channelsSummary,
            dependency = R.string.enable_streams_notifications,
            enabled = deviceEnabled
        ) { openScreen(NotificationModeConfigFragment::class.java) }
    }
}
