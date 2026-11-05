/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpErrorState
import io.github.yewplay.core.designsystem.YpLoadingMore
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpMosaicCover
import io.github.yewplay.core.designsystem.YpPlaylistControlIcons
import io.github.yewplay.core.designsystem.YpPlaylistControlLabels
import io.github.yewplay.core.designsystem.YpPlaylistControls
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSkeletonRow
import io.github.yewplay.core.designsystem.YpThumbnail
import io.github.yewplay.core.designsystem.YpVideoRow
import io.github.yewplay.core.model.YpError
import io.github.yewplay.core.model.YpStreamItem

/** A video of the playlist; [key] stays the same while the video is moved. */
data class PlaylistEntry(val key: String, val item: YpStreamItem)

/** An entry of the ⋮ menu of the top bar. */
data class PlaylistMenuEntry(val label: String, val onClick: () -> Unit)

/** Everything the playlist screen shows. */
data class PlaylistUiState(
    val title: String = "",
    val coverUrls: List<String?> = emptyList(),
    /** "Local playlist" or the uploader's name. */
    val subtitle: String? = null,
    val subtitleAvatarUrl: String? = null,
    val subtitleClickable: Boolean = false,
    /** "4 videos • 1:12:00". */
    val summary: String = "",
    val description: String? = null,
    /** Local playlists can be edited (pencil in the top bar). */
    val canEdit: Boolean = false,
    val editing: Boolean = false,
    /** Remote playlists: saved in the library or not; null when not applicable. */
    val bookmarked: Boolean? = null,
    val entries: List<PlaylistEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isEmpty: Boolean = false,
    val error: YpError? = null,
    /** False when the playlist is a tab of the home page, which has its own bar. */
    val showBack: Boolean = true,
    /** Numbers before the items (playlists); off for lists like the history. */
    val numbered: Boolean = true
)

/** Texts of the screen, from the app's strings. */
data class PlaylistStrings(
    val back: String,
    val edit: String,
    val done: String,
    val bookmark: String,
    val unbookmark: String,
    val showMore: String,
    val showLess: String,
    val remove: String,
    val empty: String,
    val emptyDetail: String,
    val error: YpErrorLabels,
    val controls: YpPlaylistControlLabels
)

/** Icons of the screen, from the app's drawables. */
data class PlaylistIcons(
    val back: Painter,
    val more: Painter,
    val edit: Painter,
    val done: Painter,
    val bookmark: Painter,
    val bookmarked: Painter,
    val drag: Painter,
    val remove: Painter,
    val controls: YpPlaylistControlIcons
)

/** What the screen asks the app to do. */
data class PlaylistActions(
    val onBack: () -> Unit,
    val onToggleEdit: () -> Unit,
    val onTitleClick: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onSubtitleClick: () -> Unit,
    val onItemClick: (Int) -> Unit,
    val onItemMenu: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onMoveFinished: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onErrorAction: () -> Unit,
    val onErrorOpenInBrowser: () -> Unit,
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
    val onListen: () -> Unit,
    val onPlayLongClick: () -> Unit,
    val onListenLongClick: () -> Unit
)

private const val ENTRY_KEY = "entry:"

/** A playlist (proposal, p. 14): mosaic cover, Play/Shuffle/Listen and numbered items. */
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    menu: List<PlaylistMenuEntry>,
    strings: PlaylistStrings,
    icons: PlaylistIcons,
    actions: PlaylistActions
) {
    val listState = rememberLazyListState()
    val scrolled by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.entries.size, state.hasMore, state.isLoadingMore) {
        if (nearEnd && state.hasMore && !state.isLoadingMore && !state.isLoading) {
            actions.onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(state, scrolled, menu, strings, icons, actions)
        val error = state.error
        when {
            error != null -> YpErrorState(
                error = error,
                labels = strings.error,
                onRetry = actions.onRetry,
                onAction = actions.onErrorAction,
                onOpenInBrowser = actions.onErrorOpenInBrowser
            )

            else -> Entries(state, listState, strings, icons, actions)
        }
    }
}

@Composable
private fun TopBar(
    state: PlaylistUiState,
    scrolled: Boolean,
    menu: List<PlaylistMenuEntry>,
    strings: PlaylistStrings,
    icons: PlaylistIcons,
    actions: PlaylistActions
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.showBack) {
            BarIcon(icons.back, strings.back, actions.onBack)
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            text = if (scrolled) state.title else "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (state.canEdit) {
            if (state.editing) {
                BarIcon(icons.done, strings.done, actions.onToggleEdit)
            } else {
                BarIcon(icons.edit, strings.edit, actions.onToggleEdit)
            }
        }
        state.bookmarked?.let { bookmarked ->
            BarIcon(
                icon = if (bookmarked) icons.bookmarked else icons.bookmark,
                description = if (bookmarked) strings.unbookmark else strings.bookmark,
                onClick = actions.onToggleBookmark
            )
        }
        if (menu.isNotEmpty()) {
            Box {
                BarIcon(icons.more, null) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label) },
                            onClick = {
                                menuOpen = false
                                entry.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIcon(icon: Painter, description: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun Entries(
    state: PlaylistUiState,
    listState: LazyListState,
    strings: PlaylistStrings,
    icons: PlaylistIcons,
    actions: PlaylistActions
) {
    val entries = rememberUpdatedState(state.entries)
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "header") { Header(state, strings, actions) }
        when {
            state.isLoading -> items(6) { YpSkeletonRow() }

            state.isEmpty -> item(key = "empty") {
                YpMessageState(
                    modifier = Modifier.height(240.dp),
                    title = strings.empty,
                    detail = strings.emptyDetail
                )
            }

            else -> {
                if (!state.editing) {
                    item(key = "controls") {
                        YpPlaylistControls(
                            icons = icons.controls,
                            labels = strings.controls,
                            onPlay = actions.onPlay,
                            onShuffle = actions.onShuffle,
                            onListen = actions.onListen,
                            onPlayLongClick = actions.onPlayLongClick,
                            onListenLongClick = actions.onListenLongClick
                        )
                    }
                }
                itemsIndexed(state.entries, key = { _, entry -> ENTRY_KEY + entry.key }) { index, entry ->
                    if (state.editing) {
                        val dragging = draggingKey == entry.key
                        EditRow(
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                            entry = entry,
                            dragging = dragging,
                            strings = strings,
                            icons = icons,
                            onRemove = { actions.onRemove(index) },
                            dragModifier = Modifier.pointerInput(entry.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = entry.key
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        actions.onMoveFinished()
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        actions.onMoveFinished()
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        dragOffset = moveDragged(
                                            listState,
                                            ENTRY_KEY,
                                            entries.value.map { it.key },
                                            entry.key,
                                            dragOffset,
                                            actions.onMove
                                        )
                                    }
                                )
                            }
                        )
                    } else {
                        YpVideoRow(
                            item = entry.item,
                            onClick = { actions.onItemClick(index) },
                            onLongClick = { actions.onItemMenu(index) },
                            moreIcon = icons.more,
                            number = if (state.numbered) index + 1 else null
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item(key = "more") { YpLoadingMore() }
                }
            }
        }
    }
}

/**
 * Swaps the dragged item with the one under its center, if any.
 *
 * @param prefix the prefix of the keys of the draggable items in the list
 * @param keys the keys of the items, in their current order, without the prefix
 * @return the drag offset, corrected for the new position of the item
 */
internal fun moveDragged(
    listState: LazyListState,
    prefix: String,
    keys: List<String>,
    key: String,
    offset: Float,
    onMove: (Int, Int) -> Unit
): Float {
    val visible = listState.layoutInfo.visibleItemsInfo
    val dragged = visible.firstOrNull { it.key == prefix + key } ?: return offset
    val center = dragged.offset + offset + dragged.size / 2f
    val target = visible.firstOrNull {
        val itemKey = it.key as? String ?: return@firstOrNull false
        itemKey.startsWith(prefix) && itemKey != prefix + key &&
            center > it.offset && center < it.offset + it.size
    } ?: return offset
    val from = keys.indexOf(key)
    val to = keys.indexOf((target.key as String).removePrefix(prefix))
    if (from < 0 || to < 0) {
        return offset
    }
    onMove(from, to)
    // after the move the item is laid out where the target was
    return offset - (target.offset - dragged.offset)
}

@Composable
private fun EditRow(
    entry: PlaylistEntry,
    dragging: Boolean,
    strings: PlaylistStrings,
    icons: PlaylistIcons,
    onRemove: () -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (dragging) colors.surfaceContainer else Color.Transparent)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = dragModifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = icons.drag,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
        YpThumbnail(
            modifier = Modifier.width(112.dp),
            url = entry.item.thumbnailUrl,
            durationText = entry.item.durationText,
            isLive = entry.item.isLive,
            progress = entry.item.progress
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = entry.item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.item.uploaderName.isNotEmpty()) {
                Text(
                    text = entry.item.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = icons.remove,
                contentDescription = strings.remove,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Header(state: PlaylistUiState, strings: PlaylistStrings, actions: PlaylistActions) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = YpScreenMargin)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            YpMosaicCover(thumbnailUrls = state.coverUrls, modifier = Modifier.size(120.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.clickable(enabled = state.canEdit, onClick = actions.onTitleClick),
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                state.subtitle?.let { subtitle ->
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(enabled = state.subtitleClickable, onClick = actions.onSubtitleClick),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (state.subtitleAvatarUrl != null) {
                            YpAvatar(url = state.subtitleAvatarUrl, size = 20)
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (state.summary.isNotEmpty()) {
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
        state.description?.let { description ->
            Text(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { expanded = !expanded },
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded },
                text = if (expanded) strings.showLess else strings.showMore,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
