/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.comments

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpErrorState
import io.github.yewplay.core.designsystem.YpLoadingMore
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSkeletonList
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.local.yewplay.YpRichText
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * YewPlay: the comments of a list (of a video, or the replies of a comment), loaded page by page.
 * [firstPage] gives the first page (and its next page); [nextPage] loads the following ones.
 */
class YpCommentsPager(
    private val userAction: UserAction,
    private val firstPage: () -> Single<Pair<List<CommentsInfoItem>, Page?>>,
    private val nextPage: (Page) -> Single<InfoItemsPage<CommentsInfoItem>>
) {
    val items = mutableStateListOf<CommentsInfoItem>()
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<ErrorInfo?>(null)
        private set
    private var next: Page? = null
    private val disposables = CompositeDisposable()

    val hasMore: Boolean get() = Page.isValid(next)

    fun load(force: Boolean = false) {
        if (loading || (loaded && !force)) {
            return
        }
        loading = true
        error = null
        disposables.add(
            firstPage()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (list, page) ->
                        items.clear()
                        items.addAll(list)
                        next = page
                        loading = false
                        loaded = true
                        if (list.isEmpty() && Page.isValid(page)) {
                            // the replies of a comment start on the next page
                            loadMore()
                        }
                    },
                    {
                        error = ErrorInfo(it, userAction, "Loading comments")
                        loading = false
                    }
                )
        )
    }

    fun loadMore() {
        val page = next
        if (loading || !Page.isValid(page)) {
            return
        }
        loading = true
        disposables.add(
            nextPage(page!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        items.addAll(result.items)
                        next = result.nextPage
                        loading = false
                    },
                    {
                        error = ErrorInfo(it, userAction, "Loading more comments")
                        loading = false
                    }
                )
        )
    }

    fun dispose() = disposables.dispose()
}

/** The labels of the error state of the comment lists. */
@Composable
fun commentErrorLabels() = YpErrorLabels(
    retry = stringResource(R.string.retry),
    openInBrowser = stringResource(R.string.open_in_browser)
)

/**
 * The comments of [pager] in a list, after an optional [header]; more are loaded when the end
 * gets near.
 */
@Composable
fun YpCommentList(
    pager: YpCommentsPager,
    listState: LazyListState,
    emptyText: String,
    onAuthor: (CommentsInfoItem) -> Unit,
    onReplies: ((CommentsInfoItem) -> Unit)?,
    onCopy: (CommentsInfoItem) -> Unit,
    onErrorAction: (ErrorInfo) -> Unit,
    onOpenInBrowser: (ErrorInfo) -> Unit,
    header: (LazyListScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, pager.items.size) {
        if (nearEnd && pager.loaded) {
            pager.loadMore()
        }
    }
    val error = pager.error
    LazyColumn(
        // the list scrolls inside the video screen, whose header collapses with it
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection()),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        header?.invoke(this)
        when {
            error != null && pager.items.isEmpty() -> item {
                YpErrorState(
                    error = YpItems.errorUi(context, error),
                    labels = commentErrorLabels(),
                    onRetry = { pager.load(force = true) },
                    onAction = { onErrorAction(error) },
                    onOpenInBrowser = { onOpenInBrowser(error) }
                )
            }

            !pager.loaded || (pager.items.isEmpty() && pager.loading) -> item { YpSkeletonList() }

            pager.items.isEmpty() -> item { YpMessageState(title = emptyText, detail = null) }

            else -> itemsIndexed(pager.items) { index, comment ->
                YpCommentRow(
                    comment = comment,
                    onAuthor = { onAuthor(comment) },
                    onReplies = onReplies?.let { { it(comment) } },
                    onCopy = { onCopy(comment) }
                )
                if (index < pager.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = YpScreenMargin + 50.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
        if (pager.loading && pager.items.isNotEmpty()) {
            item { YpLoadingMore() }
        }
    }
}

/**
 * One comment: avatar, author and date, the text (a few lines until it is opened), likes, the
 * heart of the uploader, pinned, and the replies.
 *
 * @param fullText to show the whole text from the start (the comment above its replies)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpCommentRow(
    comment: CommentsInfoItem,
    onAuthor: () -> Unit,
    onReplies: (() -> Unit)?,
    onCopy: () -> Unit,
    fullText: Boolean = false
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable(comment.url, comment.commentId) { mutableStateOf(fullText) }
    val long = comment.commentText.content().length > LONG_COMMENT_CHARS ||
        comment.commentText.content().count { it == '\n' } >= COLLAPSED_LINES
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = onCopy)
            .padding(horizontal = YpScreenMargin, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (ImageStrategy.shouldLoadImages()) {
            YpAvatar(
                url = ImageStrategy.choosePreferredImage(comment.uploaderAvatars),
                size = 36,
                modifier = Modifier.clickable(onClick = onAuthor)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.isPinned) {
                    Icon(
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp),
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(R.string.yp_comment_pinned),
                        tint = colors.onSurfaceVariant
                    )
                }
                Text(
                    modifier = Modifier.clickable(onClick = onAuthor),
                    text = authorAndDate(context, comment),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            YpRichText(
                modifier = Modifier.padding(top = 4.dp),
                description = comment.commentText,
                serviceId = comment.serviceId,
                streamUrl = comment.url,
                maxLines = if (expanded || !long) Int.MAX_VALUE else COLLAPSED_LINES
            )
            if (long && !fullText) {
                Text(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { expanded = !expanded },
                    text = stringResource(if (expanded) R.string.show_less else R.string.show_more),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_thumb_up),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant
                    )
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = Localization.likeCount(context, comment.likeCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                if (comment.isHeartedByUploader) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = stringResource(R.string.yp_comment_hearted),
                        tint = colors.tertiary
                    )
                }
                if (onReplies != null && comment.replies != null) {
                    Text(
                        modifier = Modifier.clickable(onClick = onReplies),
                        text = Localization.replyCount(context, comment.replyCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}

private fun authorAndDate(context: Context, comment: CommentsInfoItem): String = Localization.concatenateStrings(
    Localization.localizeUserName(comment.uploaderName),
    Localization.relativeTimeOrTextual(context, comment.uploadDate, comment.textualUploadDate)
)

private const val COLLAPSED_LINES = 4
private const val LONG_COMMENT_CHARS = 220
private const val LOAD_MORE_THRESHOLD = 4
