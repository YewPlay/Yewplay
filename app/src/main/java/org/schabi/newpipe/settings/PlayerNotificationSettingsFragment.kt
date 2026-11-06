/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.github.yewplay.core.designsystem.YpCheckRow
import io.github.yewplay.core.designsystem.YpPreferenceCategory
import io.github.yewplay.core.designsystem.YpPreferenceRow
import io.github.yewplay.core.designsystem.YpRadioRow
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetTitle
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.player.notification.NotificationConstants
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment

/**
 * YewPlay: the notification of the player, in Compose: the image and colors, and the five
 * action buttons (which action each one does, and up to three of them in the compact
 * notification, before Android 13).
 */
class PlayerNotificationSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_player_notification_title

    private val actions = mutableStateListOf<Int>()
    private val compact = mutableStateListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        actions.addAll(
            (0 until SLOTS).map {
                prefs.getInt(context.getString(NotificationConstants.SLOT_PREF_KEYS[it]), NotificationConstants.SLOT_DEFAULTS[it])
            }
        )
        compact.addAll(NotificationConstants.getCompactSlotsFromPreferences(context, prefs))
    }

    override fun onPause() {
        super.onPause()
        // only this app may see the intent
        requireContext().sendBroadcast(
            Intent(NotificationConstants.ACTION_RECREATE_NOTIFICATION).setPackage(App.PACKAGE_NAME)
        )
    }

    override fun YpPrefBuilder.buildItems() {
        switch(
            key = R.string.scale_to_square_image_in_notifications_key,
            title = R.string.notification_scale_to_square_image_title,
            summary = R.string.notification_scale_to_square_image_summary,
            default = false
        )
        switch(
            key = R.string.notification_colorize_key,
            title = R.string.notification_colorize_title,
            summary = R.string.notification_colorize_summary,
            default = true
        )
        switch(key = R.string.show_thumbnail_key, title = R.string.show_thumbnail_title, summary = R.string.show_thumbnail_summary, default = true)
        items += YpPref.Custom(key = null) { ActionSlots() }
    }

    @Composable
    private fun ActionSlots() {
        val android13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val context = requireContext()
        Column {
            YpPreferenceCategory(stringResource(R.string.settings_category_player_notification_title))
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 4.dp),
                text = stringResource(
                    if (android13) R.string.notification_actions_summary_android13 else R.string.notification_actions_summary
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // on Android 13+ the first three buttons are decided by the system
            (if (android13) 3 until SLOTS else 0 until SLOTS).forEach { slot ->
                val action = actions[slot]
                val icon = NotificationConstants.ACTION_ICONS[action]
                YpPreferenceRow(
                    title = stringResource(SLOT_TITLES[slot]),
                    summary = NotificationConstants.getActionName(context, action),
                    onClick = { chooseAction(slot) },
                    trailing = {
                        if (icon != 0) {
                            Icon(modifier = Modifier.size(22.dp), painter = painterResource(icon), contentDescription = null)
                        }
                    }
                )
                if (!android13) {
                    YpCheckRow(
                        modifier = Modifier.padding(start = 24.dp),
                        label = stringResource(R.string.yp_notification_compact_slot),
                        checked = slot in compact,
                        onClick = { toggleCompact(slot) }
                    )
                }
            }
        }
    }

    private fun chooseAction(slot: Int) {
        val context = requireContext()
        YpDialogs.sheet(context) { dismiss ->
            YpSheet {
                YpSheetTitle(stringResource(SLOT_TITLES[slot]))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    NotificationConstants.ALL_ACTIONS.forEach { action ->
                        YpRadioRow(
                            label = NotificationConstants.getActionName(context, action),
                            selected = action == actions[slot],
                            onClick = {
                                dismiss()
                                actions[slot] = action
                                prefs.edit { putInt(context.getString(NotificationConstants.SLOT_PREF_KEYS[slot]), action) }
                            }
                        )
                    }
                }
            }
        }.show()
    }

    private fun toggleCompact(slot: Int) {
        if (slot in compact) {
            compact.remove(slot)
        } else if (compact.size < MAX_COMPACT) {
            compact.add(slot)
        } else {
            Toast.makeText(context, R.string.notification_actions_at_most_three, Toast.LENGTH_SHORT).show()
            return
        }
        val context = requireContext()
        prefs.edit {
            for (i in 0 until MAX_COMPACT) {
                putInt(context.getString(NotificationConstants.SLOT_COMPACT_PREF_KEYS[i]), compact.getOrElse(i) { -1 })
            }
        }
    }

    private companion object {
        const val SLOTS = 5
        const val MAX_COMPACT = 3
        val SLOT_TITLES = intArrayOf(
            R.string.notification_action_0_title,
            R.string.notification_action_1_title,
            R.string.notification_action_2_title,
            R.string.notification_action_3_title,
            R.string.notification_action_4_title
        )
    }
}
