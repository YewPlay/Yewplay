/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import androidx.core.content.edit
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment

/** YewPlay: the advanced options of the player (ExoPlayer), in Compose. */
class ExoPlayerSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_exoplayer_title

    override fun YpPrefBuilder.buildItems() {
        choice(
            key = R.string.progressive_load_interval_key,
            title = R.string.progressive_load_interval_title,
            entries = R.array.progressive_load_interval_descriptions,
            values = R.array.progressive_load_interval_values,
            default = R.string.progressive_load_interval_default_value,
            summary = R.string.progressive_load_interval_summary
        )
        switch(
            key = R.string.use_exoplayer_decoder_fallback_key,
            title = R.string.use_exoplayer_decoder_fallback_title,
            summary = R.string.use_exoplayer_decoder_fallback_summary,
            default = false
        )
        // tells when the app has turned media tunneling off by itself
        val automaticKey = s(R.string.disabled_media_tunneling_automatically_key)
        val automatic = prefs.getInt(automaticKey, -1) == 1
        items += YpPref.Switch(
            key = s(R.string.disable_media_tunneling_key),
            title = s(R.string.disable_media_tunneling_title),
            summary = s(R.string.disable_media_tunneling_summary) +
                if (automatic) " " + s(R.string.disable_media_tunneling_automatic_info) else "",
            default = false,
            onChange = { enabled ->
                if (!enabled) {
                    prefs.edit { putInt(automaticKey, 0) }
                }
                true
            }
        )
        switch(
            key = R.string.always_use_exoplayer_set_output_surface_workaround_key,
            title = R.string.always_use_exoplayer_set_output_surface_workaround_title,
            summary = R.string.always_use_exoplayer_set_output_surface_workaround_summary,
            default = false
        )
    }
}
