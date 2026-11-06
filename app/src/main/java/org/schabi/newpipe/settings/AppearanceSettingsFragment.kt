/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.ThemeHelper

/** YewPlay: appearance, in Compose: theme, night theme, lists, captions and tabs. */
class AppearanceSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_appearance_title

    // the themes active when the screen was opened (or recreated after a theme change)
    private lateinit var startTheme: String
    private lateinit var startNightTheme: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTheme = prefs.getString(getString(R.string.theme_key), getString(R.string.default_theme_value))!!
        startNightTheme = prefs.getString(getString(R.string.night_theme_key), getString(R.string.default_night_theme_value))!!
    }

    override fun YpPrefBuilder.buildItems() {
        val autoTheme = s(R.string.auto_device_theme_key)
        choice(
            key = R.string.theme_key,
            title = R.string.theme_title,
            entries = R.array.theme_description_list,
            values = R.array.theme_values_list,
            default = R.string.default_theme_value,
            onChange = { value ->
                if (value == autoTheme) {
                    Toast.makeText(context, R.string.select_night_theme_toast, Toast.LENGTH_LONG).show()
                }
                applyThemeChange(startTheme, s(R.string.theme_key), value)
                false
            }
        )
        val nightEnabled = startTheme == autoTheme
        items += YpPref.Choice(
            key = s(R.string.night_theme_key),
            title = s(R.string.night_theme_title),
            entries = array(R.array.night_theme_description_list),
            values = array(R.array.night_theme_values_list),
            default = s(R.string.default_night_theme_value),
            summary = if (nightEnabled) {
                s(R.string.night_theme_summary)
            } else {
                getString(R.string.night_theme_available, s(R.string.auto_device_theme_title))
            },
            enabled = nightEnabled,
            onChange = { value ->
                applyThemeChange(startNightTheme, s(R.string.night_theme_key), value)
                false
            }
        )
        switch(
            key = R.string.show_hold_to_append_key,
            title = R.string.show_hold_to_append_title,
            summary = R.string.show_hold_to_append_summary,
            default = true
        )
        choice(
            key = R.string.tablet_mode_key,
            title = R.string.tablet_mode_title,
            entries = R.array.tablet_mode_description,
            values = R.array.tablet_mode_values,
            default = R.string.tablet_mode_auto_key
        )
        choice(
            key = R.string.list_view_mode_key,
            title = R.string.list_view_mode,
            entries = R.array.list_view_mode_description,
            values = R.array.list_view_mode_values,
            default = R.string.list_view_mode_value
        )
        action(
            title = R.string.caption_setting_title,
            summary = R.string.caption_setting_description,
            key = R.string.caption_settings_key
        ) {
            try {
                startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.general_error, Toast.LENGTH_SHORT).show()
            }
        }
        switch(
            key = R.string.main_tabs_position_key,
            title = R.string.main_tabs_position_title,
            summary = R.string.main_tabs_position_summary,
            default = false
        )
    }

    /** Saves the theme and recreates the screen when it is not the theme in use. */
    private fun applyThemeChange(beginningTheme: String, key: String, value: String) {
        prefs.edit {
            putBoolean(KEY_THEME_CHANGE, true)
            putString(key, value)
        }
        ThemeHelper.setDayNightMode(requireContext(), value)
        if (value != beginningTheme) {
            activity?.let { ActivityCompat.recreate(it) }
        }
    }
}
