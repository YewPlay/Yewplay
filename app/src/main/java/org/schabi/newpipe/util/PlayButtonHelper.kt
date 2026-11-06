/*
 * SPDX-FileCopyrightText: 2023-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/**
 * Utility class for play buttons. YewPlay: the playlist controls are now drawn in Compose
 * (YpPlaylistControls), so only the "hold to enqueue" tip is left here.
 */
object PlayButtonHelper {
    /**
     * Check if the "hold to append" toast should be shown.
     *
     * The tip is shown if the corresponding preference is enabled.
     * This is the default behaviour.
     *
     * @param context The context to get the preference.
     * @return `true` if the tip should be shown, `false` otherwise.
     */
    @JvmStatic
    fun shouldShowHoldToAppendTip(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.show_hold_to_append_key), true)
    }
}
