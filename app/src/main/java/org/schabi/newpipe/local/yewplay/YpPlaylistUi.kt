/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpPlaylistControlIcons
import io.github.yewplay.core.designsystem.YpPlaylistControlLabels
import io.github.yewplay.feature.playlist.PlaylistIcons
import io.github.yewplay.feature.playlist.PlaylistStrings
import org.schabi.newpipe.R
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PlayButtonHelper

/** YewPlay: texts, icons and play buttons shared by the playlist screens. */
object YpPlaylistUi {

    @Composable
    fun strings() = PlaylistStrings(
        back = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
        edit = stringResource(R.string.yp_edit),
        done = stringResource(R.string.done),
        bookmark = stringResource(R.string.bookmark_playlist),
        unbookmark = stringResource(R.string.unbookmark_playlist),
        showMore = stringResource(R.string.show_more),
        showLess = stringResource(R.string.show_less),
        remove = stringResource(R.string.delete),
        empty = stringResource(R.string.no_videos),
        emptyDetail = stringResource(R.string.empty_list_subtitle),
        error = YpErrorLabels(
            retry = stringResource(R.string.retry),
            openInBrowser = stringResource(R.string.open_in_browser)
        ),
        controls = YpPlaylistControlLabels(
            play = stringResource(R.string.yp_play),
            shuffle = stringResource(R.string.yp_shuffle),
            listen = stringResource(R.string.controls_background_title)
        )
    )

    @Composable
    fun icons() = PlaylistIcons(
        back = painterResource(R.drawable.ic_arrow_back),
        more = painterResource(R.drawable.ic_more_vert),
        edit = painterResource(R.drawable.yp_ic_pencil),
        done = painterResource(R.drawable.ic_done),
        bookmark = painterResource(R.drawable.ic_playlist_add),
        bookmarked = painterResource(R.drawable.ic_playlist_add_check),
        drag = painterResource(R.drawable.ic_drag_handle),
        remove = painterResource(R.drawable.ic_close),
        controls = YpPlaylistControlIcons(
            play = painterResource(R.drawable.ic_play_arrow),
            shuffle = painterResource(R.drawable.ic_shuffle),
            listen = painterResource(R.drawable.ic_headset)
        )
    )

    /** Play in the main player, with the "hold to enqueue" tip as before. */
    fun play(activity: AppCompatActivity, queue: PlayQueue?) {
        queue ?: return
        NavigationHelper.playOnMainPlayer(activity, queue)
        showHoldTip(activity)
    }

    /** Play everything in random order. */
    fun shuffle(activity: AppCompatActivity, queue: PlayQueue?) {
        queue ?: return
        queue.shuffle()
        NavigationHelper.playOnMainPlayer(activity, queue)
    }

    /** Play as audio in the background, with the "hold to enqueue" tip as before. */
    fun listen(context: Context, queue: PlayQueue?) {
        queue ?: return
        NavigationHelper.playOnBackgroundPlayer(context, queue, false)
        showHoldTip(context)
    }

    /** Add to the queue of the given player (long press on the buttons). */
    fun enqueue(context: Context, queue: PlayQueue?, type: PlayerType) {
        queue ?: return
        NavigationHelper.enqueueOnPlayer(context, queue, type)
    }

    private fun showHoldTip(context: Context) {
        if (PlayButtonHelper.shouldShowHoldToAppendTip(context)) {
            Toast.makeText(context, R.string.hold_to_append, Toast.LENGTH_SHORT).show()
        }
    }
}
