/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.yewplay.core.designsystem.YpTheme
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the comments of a video (a tab of the video screen), in Compose. Tap a comment to
 * read it whole, a long press copies it, and "N replies" opens its replies.
 */
class CommentsFragment : Fragment() {
    private var serviceId = 0
    private var url = ""
    private var info: CommentsInfo? = null
    private var commentsDisabled by mutableStateOf(false)
    private val listState = LazyListState()

    private val pager = YpCommentsPager(
        userAction = UserAction.REQUESTED_COMMENTS,
        firstPage = {
            ExtractorHelper.getCommentsInfo(serviceId, url, false).map { result ->
                info = result
                commentsDisabled = result.isCommentsDisabled
                result.relatedItems to result.nextPage
            }
        },
        nextPage = { page ->
            info?.let { ExtractorHelper.getMoreCommentItems(serviceId, it, page) }
                ?: Single.error(IllegalStateException("Comments not loaded"))
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceId = requireArguments().getInt(KEY_SERVICE_ID)
        url = requireArguments().getString(KEY_URL).orEmpty()
        pager.load()
    }

    override fun onDestroy() {
        pager.dispose()
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) {
                    YpCommentList(
                        pager = pager,
                        listState = listState,
                        emptyText = stringResource(if (commentsDisabled) R.string.comments_are_disabled else R.string.no_comments),
                        onAuthor = { NavigationHelper.openCommentAuthorIfPresent(requireActivity(), it) },
                        onReplies = { NavigationHelper.openCommentRepliesFragment(requireActivity(), it) },
                        onCopy = { YpCommentActions.copy(requireContext(), it) },
                        onErrorAction = { YpCommentActions.errorAction(this@CommentsFragment, it) },
                        onOpenInBrowser = { YpCommentActions.openInBrowser(requireContext(), it) }
                    )
                }
            }
        }
    }

    /** Scrolls to [comment] when it is in the list (back from its replies). */
    fun scrollToComment(comment: CommentsInfoItem): Boolean {
        val index = pager.items.indexOf(comment)
        if (index < 0) {
            return false
        }
        lifecycleScope.launch { listState.scrollToItem(index) }
        return true
    }

    companion object {
        private const val KEY_SERVICE_ID = "service_id"
        private const val KEY_URL = "url"
        private const val KEY_NAME = "name"

        @JvmStatic
        fun getInstance(serviceId: Int, url: String?, name: String?): CommentsFragment = CommentsFragment().apply {
            arguments = bundleOf(KEY_SERVICE_ID to serviceId, KEY_URL to url, KEY_NAME to name)
        }
    }
}
