/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import io.github.yewplay.core.designsystem.YpDialogCard
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.PermissionHelper

/**
 * YewPlay: video and audio, in Compose: resolutions and formats, the advanced player options,
 * external players and how the player behaves.
 */
class VideoAudioSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_video_audio_title

    override fun YpPrefBuilder.buildItems() {
        val higher = prefs.getBoolean(s(R.string.show_higher_resolutions_key), false)
        val resolutionNames = resolutions(R.array.resolution_list_description, R.array.high_resolution_list_descriptions, higher)
        val resolutionValues = resolutions(R.array.resolution_list_values, R.array.high_resolution_list_values, higher)
        items += YpPref.Choice(
            key = s(R.string.default_resolution_key),
            title = s(R.string.default_resolution_title),
            entries = resolutionNames,
            values = resolutionValues,
            default = s(R.string.default_resolution_value)
        )
        items += YpPref.Choice(
            key = s(R.string.default_popup_resolution_key),
            title = s(R.string.default_popup_resolution_title),
            entries = resolutionNames,
            values = resolutionValues,
            default = s(R.string.default_popup_resolution_value)
        )
        items += YpPref.Choice(
            key = s(R.string.limit_mobile_data_usage_key),
            title = s(R.string.limit_mobile_data_usage_title),
            entries = resolutions(R.array.limit_data_usage_description_list, R.array.high_resolution_list_descriptions, higher),
            values = resolutions(R.array.limit_data_usage_values_list, R.array.high_resolution_limit_data_usage_values_list, higher),
            default = s(R.string.limit_mobile_data_usage_value)
        )
        switch(
            key = R.string.show_higher_resolutions_key,
            title = R.string.show_higher_resolutions_title,
            summary = R.string.show_higher_resolutions_summary,
            default = false,
            onChange = { show ->
                if (!show) {
                    resetHighResolutions()
                }
                true
            }
        )
        choice(
            key = R.string.default_video_format_key,
            title = R.string.default_video_format_title,
            entries = R.array.video_format_description_list,
            values = R.array.video_format_values_list,
            default = R.string.default_video_format_value
        )
        choice(
            key = R.string.default_audio_format_key,
            title = R.string.default_audio_format_title,
            entries = R.array.audio_format_description_list,
            values = R.array.audio_format_values_list,
            default = R.string.default_audio_format_value
        )
        switch(
            key = R.string.prefer_original_audio_key,
            title = R.string.prefer_original_audio_title,
            summary = R.string.prefer_original_audio_summary,
            default = true
        )
        switch(
            key = R.string.prefer_descriptive_audio_key,
            title = R.string.prefer_descriptive_audio_title,
            summary = R.string.prefer_descriptive_audio_summary,
            default = false
        )
        action(
            title = R.string.settings_category_exoplayer_title,
            summary = R.string.settings_category_exoplayer_summary,
            key = R.string.exoplayer_settings_key
        ) { openScreen(ExoPlayerSettingsFragment::class.java) }

        category(R.string.settings_category_player_title)
        switch(
            key = R.string.use_external_video_player_key,
            title = R.string.use_external_video_player_title,
            summary = R.string.use_external_video_player_summary,
            default = false
        )
        switch(key = R.string.use_external_audio_player_key, title = R.string.use_external_audio_player_title, default = false)
        switch(
            key = R.string.show_play_with_kodi_key,
            title = R.string.show_play_with_kodi_title,
            summary = R.string.show_play_with_kodi_summary,
            default = false
        )
        items += YpPref.Choice(
            key = s(R.string.seekbar_preview_thumbnail_key),
            title = s(R.string.seekbar_preview_thumbnail_title),
            entries = array(R.array.seekbar_preview_thumbnail_type_description),
            values = array(R.array.seekbar_preview_thumbnail_type_key),
            default = "seekbar_preview_thumbnail_high_quality"
        )

        category(R.string.settings_category_player_behavior_title)
        choice(
            key = R.string.preferred_open_action_key,
            title = R.string.preferred_open_action_settings_title,
            entries = R.array.preferred_open_action_description_list,
            values = R.array.preferred_open_action_values_list,
            default = R.string.preferred_open_action_default,
            summary = R.string.preferred_open_action_settings_summary
        )
        choice(
            key = R.string.minimize_on_exit_key,
            title = R.string.minimize_on_exit_title,
            entries = R.array.minimize_on_exit_action_description,
            values = R.array.minimize_on_exit_action_key,
            default = R.string.minimize_on_exit_value,
            summary = R.string.minimize_on_exit_summary,
            onChange = { value ->
                if (value == s(R.string.minimize_on_exit_popup_key) && !Settings.canDrawOverlays(context)) {
                    askOverlayPermission()
                }
                true
            }
        )
        switch(
            key = R.string.start_main_player_fullscreen_key,
            title = R.string.start_main_player_fullscreen_title,
            summary = R.string.start_main_player_fullscreen_summary,
            default = false
        )
        choice(
            key = R.string.autoplay_key,
            title = R.string.autoplay_title,
            entries = R.array.autoplay_type_description,
            values = R.array.autoplay_type_key,
            default = R.string.autoplay_value,
            summary = R.string.autoplay_summary
        )
        switch(key = R.string.auto_queue_key, title = R.string.auto_queue_title, summary = R.string.auto_queue_summary, default = false)
        switch(
            key = R.string.resume_on_audio_focus_gain_key,
            title = R.string.resume_on_audio_focus_gain_title,
            summary = R.string.resume_on_audio_focus_gain_summary,
            default = false
        )
        choice(
            key = R.string.left_gesture_control_key,
            title = R.string.left_gesture_control_title,
            entries = R.array.left_gesture_control_description,
            values = R.array.left_gesture_control_values,
            default = R.string.default_left_gesture_control_value,
            summary = R.string.left_gesture_control_summary
        )
        choice(
            key = R.string.right_gesture_control_key,
            title = R.string.right_gesture_control_title,
            entries = R.array.right_gesture_control_description,
            values = R.array.right_gesture_control_values,
            default = R.string.default_right_gesture_control_value,
            summary = R.string.right_gesture_control_summary
        )
        switch(
            key = R.string.popup_remember_size_pos_key,
            title = R.string.popup_remember_size_pos_title,
            summary = R.string.popup_remember_size_pos_summary,
            default = true
        )
        switch(
            key = R.string.use_inexact_seek_key,
            title = R.string.use_inexact_seek_title,
            summary = R.string.use_inexact_seek_summary,
            default = false,
            onChange = { inexact ->
                if (inexact) {
                    adjustSeekDurationForInexactSeek()
                }
                true
            }
        )
        val inexact = prefs.getBoolean(s(R.string.use_inexact_seek_key), false)
        val seekValues = array(R.array.seek_duration_value).filter { !inexact || seconds(it) % 10 != 5 }
        items += YpPref.Choice(
            key = s(R.string.seek_duration_key),
            title = s(R.string.seek_duration_title),
            entries = seekValues.map { resources.getQuantityString(R.plurals.seconds, seconds(it), seconds(it)) },
            values = seekValues,
            default = s(R.string.seek_duration_default_value)
        )
        switch(
            key = R.string.clear_queue_confirmation_key,
            title = R.string.clear_queue_confirmation_title,
            summary = R.string.clear_queue_confirmation_summary,
            default = false
        )
        switch(
            key = R.string.ignore_hardware_media_buttons_key,
            title = R.string.ignore_hardware_media_buttons_title,
            summary = R.string.ignore_hardware_media_buttons_summary,
            default = false
        )
    }

    private fun resolutions(@ArrayRes base: Int, @ArrayRes higher: Int, showHigher: Boolean): List<String> = ListHelper.getSortedResolutionList(resources, base, higher, showHigher)

    private fun seconds(millis: String): Int = (millis.toIntOrNull() ?: 0) / DateUtils.SECOND_IN_MILLIS.toInt()

    /** Without the higher resolutions, a chosen one goes back to the first option. */
    private fun resetHighResolutions() {
        fun reset(@StringRes key: Int, @ArrayRes highValues: Int, @ArrayRes values: Int) {
            val value = prefs.getString(getString(key), null)
            if (ListHelper.isHighResolutionSelected(value, highValues, resources)) {
                prefs.edit { putString(getString(key), resources.getStringArray(values)[0]) }
            }
        }
        reset(R.string.default_resolution_key, R.array.high_resolution_list_values, R.array.resolution_list_values)
        reset(R.string.default_popup_resolution_key, R.array.high_resolution_list_values, R.array.resolution_list_values)
        reset(
            R.string.limit_mobile_data_usage_key,
            R.array.high_resolution_limit_data_usage_values_list,
            R.array.limit_data_usage_values_list
        )
    }

    /** The inexact seek only knows durations in steps of 10 seconds: round 5, 15... up. */
    private fun adjustSeekDurationForInexactSeek() {
        val key = getString(R.string.seek_duration_key)
        val selected = seconds(prefs.getString(key, getString(R.string.seek_duration_default_value)).orEmpty())
        if (selected % 10 == 5) {
            val newSeconds = selected + 5
            prefs.edit { putString(key, (newSeconds * DateUtils.SECOND_IN_MILLIS).toString()) }
            Toast.makeText(context, getString(R.string.new_seek_duration_toast, newSeconds), Toast.LENGTH_LONG).show()
        }
    }

    /** Minimizing to the popup player needs the permission to draw over other apps. */
    private fun askOverlayPermission() {
        YpDialogs.card(requireContext()) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.minimize_on_exit_title),
                message = stringResource(R.string.permission_display_over_apps),
                confirmLabel = stringResource(R.string.settings),
                onConfirm = {
                    dismiss()
                    PermissionHelper.checkSystemAlertWindowPermission(requireContext())
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            )
        }.show()
    }
}
