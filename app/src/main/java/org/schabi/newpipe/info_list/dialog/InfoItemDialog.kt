/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetHeader
import io.github.yewplay.core.designsystem.YpSheetItem
import io.github.yewplay.core.designsystem.YpThumbnail
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * YewPlay: the actions of a stream (long press or "more" on a video), as a Compose bottom
 * sheet: the video on top (thumbnail, title, channel) and one row per action, with its icon.
 */
class InfoItemDialog private constructor(
    activity: Activity,
    private val fragment: Fragment,
    private val info: StreamInfoItem,
    private val entries: List<StreamDialogEntry>
) {
    private val dialog: Dialog = YpDialogs.sheet(activity) { dismiss ->
        YpSheet {
            YpSheetHeader(
                title = info.name,
                subtitle = info.uploaderName,
                leading = {
                    YpThumbnail(
                        url = ImageStrategy.choosePreferredImage(info.thumbnails),
                        durationText = null,
                        isLive = StreamTypeUtil.isLiveStream(info.streamType),
                        progress = 0f,
                        modifier = Modifier.width(96.dp)
                    )
                }
            )
            LazyColumn {
                items(entries, key = { it.resource }) { entry ->
                    YpSheetItem(
                        label = entry.getString(activity),
                        icon = painterResource(entry.icon),
                        destructive = entry.resource == R.string.delete,
                        onClick = {
                            dismiss()
                            entry.action.onClick(fragment, info)
                        }
                    )
                }
            }
        }
    }

    fun show() {
        dialog.show()
    }

    /**
     * Builds the actions sheet of a [StreamInfoItem]. With [addDefaultEntriesAutomatically],
     * the usual entries are added at the top (enqueue, start here) and at the bottom (download,
     * playlist, share, browser, Kodi, mark as watched, channel); the entries added with
     * [addEntry] and [addAllEntries] go in between. Some entries only show when they apply
     * (user preferences, stream type and player state).
     */
    class Builder @JvmOverloads constructor(
        activity: Activity?,
        context: Context?,
        private val fragment: Fragment,
        private val infoItem: StreamInfoItem,
        private val addDefaultEntriesAutomatically: Boolean = true
    ) {
        private val activity: Activity
        private val context: Context
        private val entries = mutableListOf<StreamDialogEntry>()

        init {
            if (activity == null || context == null) {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "activity or context is null: activity = $activity, context = $context")
                }
                throw IllegalArgumentException("activity, context or resources is null")
            }
            this.activity = activity
            this.context = context
            if (addDefaultEntriesAutomatically) {
                addDefaultBeginningEntries()
            }
        }

        /** Appends an entry. */
        fun addEntry(entry: StreamDialogDefaultEntry): Builder {
            entries.add(entry.toStreamDialogEntry())
            return this
        }

        /** Appends the entries, in order. */
        fun addAllEntries(vararg newEntries: StreamDialogDefaultEntry): Builder {
            newEntries.forEach { addEntry(it) }
            return this
        }

        /**
         * Changes what an entry already added does. Has no effect for entries not added yet.
         */
        fun setAction(
            entry: StreamDialogDefaultEntry,
            action: StreamDialogEntry.StreamDialogEntryAction
        ): Builder {
            val index = entries.indexOfFirst { it.resource == entry.resource }
            if (index >= 0) {
                entries[index] = StreamDialogEntry(entry.resource, entry.icon, action)
            }
            return this
        }

        /** "Enqueue" when the player is open, and "Enqueue next" when more streams follow. */
        fun addEnqueueEntriesIfNeeded(): Builder {
            val holder = PlayerHolder.getInstance()
            if (holder.isPlayQueueReady) {
                addEntry(StreamDialogDefaultEntry.ENQUEUE)
                if (holder.queuePosition < holder.queueSize - 1) {
                    addEntry(StreamDialogDefaultEntry.ENQUEUE_NEXT)
                }
            }
            return this
        }

        /** "Start here in the background", and in a popup unless the stream is only audio. */
        fun addStartHereEntries(): Builder {
            addEntry(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND)
            if (!StreamTypeUtil.isAudio(infoItem.streamType)) {
                addEntry(StreamDialogDefaultEntry.START_HERE_ON_POPUP)
            }
            return this
        }

        /** "Mark as watched" when the watch history is on and the stream is not live. */
        fun addMarkAsWatchedEntryIfNeeded(): Builder {
            val historyEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_watch_history_key), false)
            if (historyEnabled && !StreamTypeUtil.isLiveStream(infoItem.streamType)) {
                addEntry(StreamDialogDefaultEntry.MARK_AS_WATCHED)
            }
            return this
        }

        /** "Play with Kodi" when the user asked for it in the settings. */
        fun addPlayWithKodiEntryIfNeeded(): Builder {
            if (KoreUtils.shouldShowPlayWithKodi(context, infoItem.serviceId)) {
                addEntry(StreamDialogDefaultEntry.PLAY_WITH_KODI)
            }
            return this
        }

        /** The entries usually at the top: enqueue and start here. */
        fun addDefaultBeginningEntries(): Builder {
            addEnqueueEntriesIfNeeded()
            addStartHereEntries()
            return this
        }

        /** The entries usually at the bottom. */
        fun addDefaultEndEntries(): Builder {
            addAllEntries(
                StreamDialogDefaultEntry.DOWNLOAD,
                StreamDialogDefaultEntry.APPEND_PLAYLIST,
                StreamDialogDefaultEntry.SHARE,
                StreamDialogDefaultEntry.OPEN_IN_BROWSER
            )
            addPlayWithKodiEntryIfNeeded()
            addMarkAsWatchedEntryIfNeeded()
            addEntry(StreamDialogDefaultEntry.SHOW_CHANNEL_DETAILS)
            return this
        }

        /** Creates the sheet. */
        fun create(): InfoItemDialog {
            if (addDefaultEntriesAutomatically) {
                addDefaultEndEntries()
            }
            return InfoItemDialog(activity, fragment, infoItem, entries.toList())
        }

        companion object {
            @JvmStatic
            fun reportErrorDuringInitialization(throwable: Throwable, item: InfoItem) {
                ErrorUtil.showSnackbar(
                    App.instance.baseContext,
                    ErrorInfo(throwable, UserAction.OPEN_INFO_ITEM_DIALOG, "none", item.serviceId)
                )
            }
        }
    }

    private companion object {
        const val TAG = "InfoItemDialog"
    }
}
