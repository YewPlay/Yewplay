/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.playlist.PlaylistActions
import io.github.yewplay.feature.playlist.PlaylistMenuEntry
import io.github.yewplay.feature.playlist.PlaylistScreen
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.local.yewplay.YpPlaylistUi
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: a local playlist (proposal, p. 14), drawn in Compose by :feature:playlist. The
 * pencil turns on editing (drag to reorder, remove); everything else is in the menus.
 */
class YpLocalPlaylistFragment : BaseFragment() {
    private lateinit var viewModel: YpLocalPlaylistViewModel
    private lateinit var operations: LocalPlaylistViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpLocalPlaylistViewModel::class.java]
        operations = ViewModelProvider(this)[LocalPlaylistViewModel::class.java]
        val args = arguments ?: Bundle()
        viewModel.init(
            playlistId = args.getLong(KEY_PLAYLIST_ID),
            name = args.getString(KEY_TITLE).orEmpty(),
            frontPage = useAsFrontPage || savedInstanceState?.getBoolean(STATE_FRONT_PAGE) == true
        )
        // "Remove watched" and "Remove duplicates" keep running when the screen closes
        operations.workState.observe(this) { state ->
            if (state is LocalPlaylistViewModel.WorkState.Error) {
                ErrorUtil.showSnackbar(
                    this,
                    ErrorInfo(state.throwable, UserAction.REQUESTED_BOOKMARK, "Playlist operation")
                )
            }
        }
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
                            is LocalPlaylistEvent.ShowError ->
                                ErrorUtil.showSnackbar(this@YpLocalPlaylistFragment, event.errorInfo)

                            LocalPlaylistEvent.ThumbnailChanged -> Toast.makeText(
                                requireContext(),
                                R.string.playlist_thumbnail_change_success,
                                Toast.LENGTH_SHORT
                            ).show()

                            is LocalPlaylistEvent.Share ->
                                ShareUtils.shareText(requireContext(), viewModel.name, event.content)
                        }
                    }
                }
                YpTheme(themeKey, nightThemeKey) {
                    PlaylistScreen(
                        state = state,
                        menu = listOf(
                            PlaylistMenuEntry(stringResource(R.string.share), ::confirmShare),
                            PlaylistMenuEntry(stringResource(R.string.rename_playlist), ::rename),
                            PlaylistMenuEntry(stringResource(R.string.remove_watched), ::confirmRemoveWatched),
                            PlaylistMenuEntry(
                                stringResource(R.string.remove_duplicates),
                                ::confirmRemoveDuplicates
                            )
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
        onToggleEdit = viewModel::toggleEditing,
        onTitleClick = ::rename,
        onToggleBookmark = { },
        onSubtitleClick = { },
        onItemClick = ::openVideo,
        onItemMenu = ::showItemMenu,
        onRemove = viewModel::remove,
        onMove = viewModel::move,
        onMoveFinished = viewModel::saveImmediate,
        onLoadMore = { },
        onRetry = viewModel::retry,
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
                .addAllEntries(
                    StreamDialogDefaultEntry.SET_AS_PLAYLIST_THUMBNAIL,
                    StreamDialogDefaultEntry.DELETE
                )
                .setAction(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND) { _, _ ->
                    viewModel.playQueue(index)?.let {
                        NavigationHelper.playOnBackgroundPlayer(requireContext(), it, true)
                    }
                }
                .setAction(StreamDialogDefaultEntry.SET_AS_PLAYLIST_THUMBNAIL) { _, _ ->
                    viewModel.setThumbnail(index)
                }
                .setAction(StreamDialogDefaultEntry.DELETE) { _, _ -> viewModel.remove(index) }
                .create()
                .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item)
        }
    }

    private fun rename() {
        val binding = DialogEditTextBinding.inflate(layoutInflater)
        binding.dialogEditText.setHint(R.string.name)
        binding.dialogEditText.inputType = InputType.TYPE_CLASS_TEXT
        binding.dialogEditText.setText(viewModel.name)
        binding.dialogEditText.setSelection(binding.dialogEditText.text?.length ?: 0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_playlist)
            .setView(binding.root)
            .setCancelable(true)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.rename) { _, _ ->
                viewModel.rename(binding.dialogEditText.text.toString())
            }
            .show()
    }

    private fun confirmShare() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.share_playlist)
            .setCancelable(true)
            .setPositiveButton(R.string.share_playlist_with_titles) { _, _ ->
                viewModel.share(PlayListShareMode.WITH_TITLES)
            }
            .setNeutralButton(R.string.share_playlist_as_youtube_temporary_playlist) { _, _ ->
                viewModel.share(PlayListShareMode.YOUTUBE_TEMP_PLAYLIST)
            }
            .setNegativeButton(R.string.share_playlist_with_list) { _, _ ->
                viewModel.share(PlayListShareMode.JUST_URLS)
            }
            .show()
    }

    private fun confirmRemoveWatched() {
        val context = requireContext()
        val partially = CheckBox(context)
        partially.setText(R.string.remove_watched_popup_partially_watched_streams)
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        val padding = DeviceUtils.dpToPx(20, context)
        container.setPadding(padding, padding, padding, 0)
        container.addView(partially)
        AlertDialog.Builder(context)
            .setMessage(R.string.remove_watched_popup_warning)
            .setTitle(R.string.remove_watched_popup_title)
            .setView(container)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.saveImmediate()
                operations.removeWatchedStreams(viewModel.playlistId, partially.isChecked)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmRemoveDuplicates() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_duplicates_title)
            .setMessage(R.string.remove_duplicates_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.saveImmediate()
                operations.removeDuplicates(viewModel.playlistId)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    /** Saves edits right away (called when the player opens or the home tab changes). */
    fun saveImmediate() = viewModel.saveImmediate()

    override fun onPause() {
        super.onPause()
        saveImmediate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FRONT_PAGE, useAsFrontPage || !viewModel.state.value.showBack)
    }

    companion object {
        private const val KEY_PLAYLIST_ID = "yp_playlist_id"
        private const val STATE_FRONT_PAGE = "yp_playlist_front_page"

        @JvmStatic
        fun getInstance(playlistId: Long, name: String?): YpLocalPlaylistFragment = YpLocalPlaylistFragment().apply {
            arguments = Bundle().apply {
                putLong(KEY_PLAYLIST_ID, playlistId)
                putString(KEY_TITLE, name.orEmpty())
            }
        }
    }
}
