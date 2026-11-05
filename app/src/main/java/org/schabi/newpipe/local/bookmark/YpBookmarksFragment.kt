/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.bookmark

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.playlist.PlaylistsActions
import io.github.yewplay.feature.playlist.PlaylistsIcons
import io.github.yewplay.feature.playlist.PlaylistsScreen
import io.github.yewplay.feature.playlist.PlaylistsStrings
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the saved playlists (yours and the ones bookmarked from a service) in Compose:
 * square covers, the menu of each playlist, and the order edited only when asked (pencil).
 */
class YpBookmarksFragment : BaseFragment() {
    private lateinit var viewModel: YpBookmarksViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpBookmarksViewModel::class.java]
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
                    viewModel.errors.collect { ErrorUtil.showSnackbar(this@YpBookmarksFragment, it) }
                }
                YpTheme(themeKey, nightThemeKey) {
                    PlaylistsScreen(
                        state = state,
                        strings = PlaylistsStrings(
                            back = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            edit = stringResource(R.string.yp_edit),
                            done = stringResource(R.string.done),
                            remove = stringResource(R.string.delete),
                            empty = stringResource(R.string.no_playlist_bookmarked_yet),
                            emptyDetail = stringResource(R.string.empty_list_subtitle)
                        ),
                        icons = PlaylistsIcons(
                            back = painterResource(R.drawable.ic_arrow_back),
                            edit = painterResource(R.drawable.yp_ic_pencil),
                            done = painterResource(R.drawable.ic_done),
                            more = painterResource(R.drawable.ic_more_vert),
                            drag = painterResource(R.drawable.ic_drag_handle),
                            remove = painterResource(R.drawable.ic_close)
                        ),
                        actions = PlaylistsActions(
                            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                            onToggleEdit = viewModel::toggleEditing,
                            onItemClick = ::openPlaylist,
                            onItemMenu = ::showItemMenu,
                            onRemove = { index -> viewModel.itemAt(index)?.let { confirmDelete(it) } },
                            onMove = viewModel::move,
                            onMoveFinished = viewModel::saveImmediate
                        )
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveImmediate()
    }

    private fun openPlaylist(index: Int) {
        when (val item = viewModel.itemAt(index)) {
            is PlaylistMetadataEntry ->
                NavigationHelper.openLocalPlaylistFragment(fm, item.uid, item.orderingName)

            is PlaylistRemoteEntity -> NavigationHelper.openPlaylistFragment(
                fm,
                item.serviceId,
                item.url,
                item.orderingName.orEmpty()
            )

            else -> Unit
        }
    }

    private fun showItemMenu(index: Int) {
        when (val item = viewModel.itemAt(index)) {
            is PlaylistMetadataEntry -> showLocalMenu(item)
            is PlaylistRemoteEntity -> confirmDelete(item)
            else -> Unit
        }
    }

    private fun showLocalMenu(item: PlaylistMetadataEntry) {
        val context = requireContext()
        // read on a background thread: the database must not be queried on the main thread
        Single.fromCallable { viewModel.localManager.getIsPlaylistThumbnailPermanent(item.uid) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ permanent ->
                if (!isAdded) {
                    return@subscribe
                }
                val entries = mutableListOf(
                    getString(R.string.rename) to { showRename(item) },
                    getString(R.string.delete) to { confirmDelete(item) }
                )
                if (permanent) {
                    entries.add(getString(R.string.unset_playlist_thumbnail) to { viewModel.unsetThumbnail(item) })
                }
                AlertDialog.Builder(context)
                    .setTitle(item.orderingName)
                    .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
                    .show()
            }, { })
    }

    private fun showRename(item: PlaylistMetadataEntry) {
        val binding = DialogEditTextBinding.inflate(layoutInflater)
        binding.dialogEditText.setHint(R.string.name)
        binding.dialogEditText.inputType = InputType.TYPE_CLASS_TEXT
        binding.dialogEditText.setText(item.orderingName)
        AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.rename_playlist) { _, _ ->
                viewModel.rename(item, binding.dialogEditText.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(item: PlaylistLocalItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(item.orderingName)
            .setMessage(R.string.delete_playlist_prompt)
            .setCancelable(true)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.remove(viewModel.indexOf(item))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FRONT_PAGE, useAsFrontPage || !viewModel.state.value.showBack)
    }

    private companion object {
        const val STATE_FRONT_PAGE = "yp_bookmarks_front_page"
    }
}
