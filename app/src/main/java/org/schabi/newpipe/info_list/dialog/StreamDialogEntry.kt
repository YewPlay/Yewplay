/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list.dialog

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** One entry of the actions sheet of a stream: its label, its icon and what it does. */
class StreamDialogEntry(
    @JvmField @param:StringRes val resource: Int,
    @JvmField @param:DrawableRes val icon: Int,
    @JvmField val action: StreamDialogEntryAction
) {
    fun getString(context: Context): String = context.getString(resource)

    fun interface StreamDialogEntryAction {
        fun onClick(fragment: Fragment, infoItem: StreamInfoItem)
    }
}
