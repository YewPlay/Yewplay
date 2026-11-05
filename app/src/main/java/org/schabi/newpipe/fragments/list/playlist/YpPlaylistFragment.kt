/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.playlist

import android.app.Activity
import android.content.Intent
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
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.playlist.PlaylistActions
import io.github.yewplay.feature.playlist.PlaylistMenuEntry
import io.github.yewplay.feature.playlist.PlaylistScreen
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.yewplay.YpPlaylistUi
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: a playlist of a service (proposal, p. 14), drawn in Compose by :feature:playlist.
 * It has its own top bar, so the activity hides its toolbar while it is shown.
 */
class YpPlaylistFragment : BaseFragment() {
    private lateinit var viewModel: YpPlaylistViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpPlaylistViewModel::class.java]
        val args = arguments ?: Bundle()
        viewModel.init(
            serviceId = args.getInt(KEY_SERVICE_ID),
            url = args.getString(KEY_URL).orEmpty(),
            name = args.getString(KEY_TITLE).orEmpty(),
            frontPage = useAsFrontPage || savedInstanceState?.getBoolean(STATE_FRONT_PAGE) == true
        )
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
                    viewModel.events.collect {
                        ErrorUtil.showSnackbar(this@YpPlaylistFragment, it.errorInfo)
                    }
                }
                YpTheme(themeKey, nightThemeKey) {
                    PlaylistScreen(
                        state = state,
                        menu = listOf(
                            PlaylistMenuEntry(stringResource(R.string.share), ::share),
                            PlaylistMenuEntry(stringResource(R.string.add_to_playlist), ::addToPlaylist),
                            PlaylistMenuEntry(stringResource(R.string.open_in_browser)) {
                                ShareUtils.openUrlInBrowser(requireContext(), viewModel.url)
                            },
                            PlaylistMenuEntry(stringResource(R.string.settings)) {
                                NavigationHelper.openSettings(requireContext())
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
        onToggleBookmark = viewModel::toggleBookmark,
        onSubtitleClick = ::openUploader,
        onItemClick = ::openVideo,
        onItemMenu = ::showItemMenu,
        onRemove = { },
        onMove = { _, _ -> },
        onMoveFinished = { },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onErrorAction = ::onErrorAction,
        onErrorOpenInBrowser = {
            viewModel.lastError?.openInBrowserUrl?.let {
                ShareUtils.openUrlInBrowser(requireContext(), it)
            }
        },
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

    private fun share() {
        ShareUtils.shareText(
            requireContext(),
            viewModel.state.value.title,
            viewModel.url,
            viewModel.info?.thumbnails.orEmpty()
        )
    }

    private fun addToPlaylist() {
        val queue = viewModel.playQueue() ?: return
        val streams = queue.streams.map { StreamEntity(it) }
        PlaylistDialog.createCorrespondingDialog(requireContext(), streams) { dialog ->
            dialog.show(fm, TAG)
        }
    }

    private fun openUploader() {
        val info = viewModel.info ?: return
        val uploaderUrl = info.uploaderUrl
        if (uploaderUrl.isNullOrEmpty()) {
            return
        }
        try {
            NavigationHelper.openChannelFragment(
                fm,
                info.serviceId,
                uploaderUrl,
                info.uploaderName.orEmpty()
            )
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e)
        }
    }

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
                .setAction(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND) { _, _ ->
                    viewModel.playQueue(index)?.let {
                        NavigationHelper.playOnBackgroundPlayer(requireContext(), it, true)
                    }
                }
                .create()
                .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item)
        }
    }

    private fun onErrorAction() {
        val error = viewModel.lastError ?: return
        val recaptchaUrl = error.recaptchaUrl
        if (recaptchaUrl != null) {
            val intent = Intent(requireContext(), ReCaptchaActivity::class.java)
            intent.putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, recaptchaUrl)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, ReCaptchaActivity.RECAPTCHA_REQUEST)
        } else {
            ErrorUtil.openActivity(requireContext(), error)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST && resultCode == Activity.RESULT_OK) {
            viewModel.retry()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FRONT_PAGE, useAsFrontPage || !viewModel.state.value.showBack)
    }

    companion object {
        private const val STATE_FRONT_PAGE = "yp_playlist_front_page"

        @JvmStatic
        fun getInstance(serviceId: Int, url: String?, name: String?): YpPlaylistFragment = YpPlaylistFragment().apply {
            arguments = Bundle().apply {
                putInt(KEY_SERVICE_ID, serviceId)
                putString(KEY_URL, url)
                putString(KEY_TITLE, name.orEmpty())
            }
        }
    }
}
