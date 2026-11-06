/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTheme
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the replies of a comment, in Compose: the comment on top (whole) and its replies
 * below, loaded page by page.
 */
class CommentRepliesFragment() : BaseFragment() {
    private lateinit var comment: CommentsInfoItem
    private val listState = LazyListState()

    private val pager = YpCommentsPager(
        userAction = UserAction.REQUESTED_COMMENT_REPLIES,
        firstPage = { Single.just(emptyList<CommentsInfoItem>() to comment.replies) },
        // the url of the comment is the one of its list (the video)
        nextPage = { page -> ExtractorHelper.getMoreCommentItems(comment.serviceId, comment.url, page) }
    )

    constructor(comment: CommentsInfoItem) : this() {
        arguments = bundleOf(KEY_COMMENT to comment)
    }

    /** The comment whose replies are shown. */
    val commentsInfoItem: CommentsInfoItem get() = comment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        comment = BundleCompat.getSerializable(requireArguments(), KEY_COMMENT, CommentsInfoItem::class.java)!!
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description)
                                )
                            }
                            Text(
                                text = Localization.replyCount(context, comment.replyCount),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        YpCommentList(
                            pager = pager,
                            listState = listState,
                            emptyText = stringResource(R.string.no_comments),
                            onAuthor = { NavigationHelper.openCommentAuthorIfPresent(requireActivity(), it) },
                            onReplies = null,
                            onCopy = { YpCommentActions.copy(requireContext(), it) },
                            onErrorAction = { YpCommentActions.errorAction(this@CommentRepliesFragment, it) },
                            onOpenInBrowser = { YpCommentActions.openInBrowser(requireContext(), it) },
                            header = {
                                item {
                                    YpCommentRow(
                                        comment = comment,
                                        onAuthor = { NavigationHelper.openCommentAuthorIfPresent(requireActivity(), comment) },
                                        onReplies = null,
                                        onCopy = { YpCommentActions.copy(requireContext(), comment) },
                                        fullText = true
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = YpScreenMargin),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        @JvmField
        val TAG: String = CommentRepliesFragment::class.java.simpleName
        private const val KEY_COMMENT = "comment"
    }
}
