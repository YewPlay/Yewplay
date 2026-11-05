/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.playlist.PlaylistActions
import io.github.yewplay.feature.playlist.PlaylistMenuEntry
import io.github.yewplay.feature.playlist.PlaylistScreen
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.local.yewplay.YpPlaylistUi
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.settings.HistorySettingsFragment
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the watch history in Compose, drawn by the playlist screen of :feature:playlist:
 * mosaic cover, Play/Shuffle/Listen, last played or most played first.
 */
class YpHistoryFragment : BaseFragment() {
    private lateinit var viewModel: YpHistoryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpHistoryViewModel::class.java]
        viewModel.init(useAsFrontPage || savedInstanceState?.getBoolean(STATE_FRONT_PAGE) == true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is HistoryEvent.ShowError ->
                                ErrorUtil.showSnackbar(this@YpHistoryFragment, event.errorInfo)

                            HistoryEvent.Deleted -> view?.let {
                                Snackbar.make(it, R.string.one_item_deleted, Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                YpTheme(themeKey, nightThemeKey) {
                    PlaylistScreen(
                        state = state,
                        menu = listOf(
                            PlaylistMenuEntry(
                                stringResource(
                                    if (viewModel.mostPlayed) R.string.title_last_played else R.string.title_most_played
                                ),
                                viewModel::toggleSort
                            ),
                            PlaylistMenuEntry(stringResource(R.string.clear_views_history_title)) {
                                HistorySettingsFragment.openDeleteWatchHistoryDialog(
                                    requireContext(),
                                    viewModel.recordManager,
                                    viewModel.disposables
                                )
                            }
                        ),
                        strings = YpPlaylistUi.strings(),
                        icons = YpPlaylistUi.icons(),
                        actions = actions()
                    )
                }
            }
        }
    }

    private fun actions() = PlaylistActions(
        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        onToggleEdit = { },
        onTitleClick = { },
        onToggleBookmark = { },
        onSubtitleClick = viewModel::toggleSort,
        onItemClick = ::openVideo,
        onItemMenu = ::showItemMenu,
        onRemove = viewModel::delete,
        onMove = { _, _ -> },
        onMoveFinished = { },
        onLoadMore = { },
        onRetry = { },
        onErrorAction = { },
        onErrorOpenInBrowser = { },
        onPlay = { YpPlaylistUi.play(activity, viewModel.playQueue()) },
        onShuffle = { YpPlaylistUi.shuffle(activity, viewModel.playQueue()) },
        onListen = { YpPlaylistUi.listen(requireContext(), viewModel.playQueue()) },
        onPlayLongClick = {
            YpPlaylistUi.enqueue(requireContext(), viewModel.playQueue(), PlayerType.MAIN)
        },
        onListenLongClick = {
            YpPlaylistUi.enqueue(requireContext(), viewModel.playQueue(), PlayerType.AUDIO)
        }
    )

    private fun openVideo(index: Int) {
        val item = viewModel.streamItemAt(index) ?: return
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            fm,
            item.serviceId,
            item.url,
            item.name,
            null,
            false
        )
    }

    private fun showItemMenu(index: Int) {
        val item = viewModel.streamItemAt(index) ?: return
        try {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, item)
                .addEntry(StreamDialogDefaultEntry.DELETE)
                .setAction(StreamDialogDefaultEntry.DELETE) { _, _ -> viewModel.delete(index) }
                .create()
                .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FRONT_PAGE, useAsFrontPage || !viewModel.state.value.showBack)
    }

    private companion object {
        const val STATE_FRONT_PAGE = "yp_history_front_page"
    }
}
