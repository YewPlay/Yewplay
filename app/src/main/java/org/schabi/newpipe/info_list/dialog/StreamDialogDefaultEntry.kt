/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list.dialog

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils

/** The actions a stream can offer in its actions sheet, each with its label and icon. */
enum class StreamDialogDefaultEntry(
    @JvmField @param:StringRes val resource: Int,
    @JvmField @param:DrawableRes val icon: Int,
    @JvmField val action: StreamDialogEntry.StreamDialogEntryAction
) {
    SHOW_CHANNEL_DETAILS(
        R.string.show_channel_details,
        R.drawable.yp_ic_user_circle,
        { fragment, item ->
            SparseItemUtil.fetchUploaderUrlIfSparse(
                fragment.requireContext(),
                item.serviceId,
                item.url,
                item.uploaderUrl
            ) { url -> NavigationHelper.openChannelFragment(fragment, item, url) }
        }
    ),

    /** Enqueues the stream automatically to the current player type. */
    ENQUEUE(
        R.string.enqueue_stream,
        R.drawable.yp_ic_queue,
        { fragment, item ->
            val context = fragment.requireContext().applicationContext
            SparseItemUtil.fetchItemInfoIfSparse(context, item) { queue ->
                NavigationHelper.enqueueOnPlayer(context, queue)
            }
        }
    ),

    /** Enqueues the stream after the one playing now. */
    ENQUEUE_NEXT(
        R.string.enqueue_next_stream,
        R.drawable.yp_ic_play_next,
        { fragment, item ->
            val context = fragment.requireContext().applicationContext
            SparseItemUtil.fetchItemInfoIfSparse(context, item) { queue ->
                NavigationHelper.enqueueNextOnPlayer(context, queue)
            }
        }
    ),

    START_HERE_ON_BACKGROUND(
        R.string.start_here_on_background,
        R.drawable.ic_headset,
        { fragment, item ->
            val context = fragment.requireContext().applicationContext
            SparseItemUtil.fetchItemInfoIfSparse(context, item) { queue ->
                NavigationHelper.playOnBackgroundPlayer(context, queue, true)
            }
        }
    ),

    START_HERE_ON_POPUP(
        R.string.start_here_on_popup,
        R.drawable.ic_picture_in_picture,
        { fragment, item ->
            val context = fragment.requireContext().applicationContext
            SparseItemUtil.fetchItemInfoIfSparse(context, item) { queue ->
                NavigationHelper.playOnPopupPlayer(context, queue, true)
            }
        }
    ),

    SET_AS_PLAYLIST_THUMBNAIL(
        R.string.set_as_playlist_thumbnail,
        R.drawable.yp_ic_image,
        { _, _ -> throw UnsupportedOperationException(MANUAL) }
    ),

    DELETE(R.string.delete, R.drawable.ic_delete, { _, _ -> throw UnsupportedOperationException(MANUAL) }),

    /** Appends the stream to a playlist, or creates one when there is none yet. */
    APPEND_PLAYLIST(
        R.string.add_to_playlist,
        R.drawable.ic_playlist_add,
        { fragment, item ->
            PlaylistDialog.createCorrespondingDialog(
                fragment.context,
                listOf(StreamEntity(item))
            ) { dialog ->
                dialog.show(
                    fragment.parentFragmentManager,
                    "StreamDialogEntry@" +
                        (if (dialog is PlaylistAppendDialog) "append" else "create") +
                        "_playlist"
                )
            }
        }
    ),

    PLAY_WITH_KODI(
        R.string.play_with_kodi_title,
        R.drawable.ic_tv,
        { fragment, item -> KoreUtils.playWithKore(fragment.requireContext(), Uri.parse(item.url)) }
    ),

    SHARE(
        R.string.share,
        R.drawable.ic_share,
        { fragment, item ->
            ShareUtils.shareText(fragment.requireContext(), item.name, item.url, item.thumbnails)
        }
    ),

    /**
     * Opens the download dialog after fetching the stream info, unless the user has left the
     * screen in the meantime.
     */
    DOWNLOAD(
        R.string.download,
        R.drawable.yp_ic_download_simple,
        { fragment, item ->
            SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                fragment.requireContext(),
                item.serviceId,
                item.url
            ) { info ->
                if (fragment.isAdded && !fragment.isStateSaved) {
                    DownloadDialog(fragment.requireContext(), info)
                        .show(fragment.childFragmentManager, "downloadDialog")
                }
            }
        }
    ),

    OPEN_IN_BROWSER(
        R.string.open_in_browser,
        R.drawable.yp_ic_arrow_square_out,
        { fragment, item -> ShareUtils.openUrlInBrowser(fragment.requireContext(), item.url) }
    ),

    MARK_AS_WATCHED(
        R.string.mark_as_watched,
        R.drawable.yp_ic_check_circle,
        { fragment, item ->
            val context = fragment.requireContext()
            HistoryRecordManager(context)
                .markAsWatched(item)
                .doOnError { error ->
                    ErrorUtil.showSnackbar(
                        context,
                        ErrorInfo(
                            error,
                            UserAction.OPEN_INFO_ITEM_DIALOG,
                            "Got an error when trying to mark as watched"
                        )
                    )
                }
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
        }
    );

    fun toStreamDialogEntry(): StreamDialogEntry = StreamDialogEntry(resource, icon, action)
}

private const val MANUAL = "This needs to be implemented manually by using InfoItemDialog.Builder.setAction()"
