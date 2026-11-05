/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpScreenMargin

/** A playlist of the list; [key] stays the same while it is moved. */
data class PlaylistsItem(
    val key: String,
    val name: String,
    val thumbnailUrl: String?,
    /** "12 videos · Local playlist", or the uploader of a saved one. */
    val detail: String
)

/** Everything the list of playlists shows. */
data class PlaylistsUiState(
    val title: String = "",
    val items: List<PlaylistsItem> = emptyList(),
    val isLoading: Boolean = true,
    val editing: Boolean = false,
    val showBack: Boolean = true
)

/** Texts of the list, from the app's strings. */
data class PlaylistsStrings(
    val back: String,
    val edit: String,
    val done: String,
    val remove: String,
    val empty: String,
    val emptyDetail: String
)

/** Icons of the list, from the app's drawables. */
data class PlaylistsIcons(
    val back: Painter,
    val edit: Painter,
    val done: Painter,
    val more: Painter,
    val drag: Painter,
    val remove: Painter
)

/** What the list asks the app to do. */
data class PlaylistsActions(
    val onBack: () -> Unit,
    val onToggleEdit: () -> Unit,
    val onItemClick: (Int) -> Unit,
    val onItemMenu: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onMoveFinished: () -> Unit
)

private const val ITEM_KEY = "playlist:"

/**
 * Saved playlists: your playlists and the ones bookmarked from a service, with square covers;
 * the order and removal are edited only when asked (pencil).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    strings: PlaylistsStrings,
    icons: PlaylistsIcons,
    actions: PlaylistsActions
) {
    val listState = rememberLazyListState()
    val keys = rememberUpdatedState(state.items.map { it.key })
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.showBack) {
                BarButton(icons.back, strings.back, actions.onBack)
            }
            Spacer(Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                if (state.editing) {
                    BarButton(icons.done, strings.done, actions.onToggleEdit)
                } else {
                    BarButton(icons.edit, strings.edit, actions.onToggleEdit)
                }
            }
        }
        Text(
            modifier = Modifier.padding(horizontal = YpScreenMargin),
            text = state.title,
            style = MaterialTheme.typography.headlineLarge
        )
        if (!state.isLoading && state.items.isEmpty()) {
            YpMessageState(title = strings.empty, detail = strings.emptyDetail)
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            itemsIndexed(state.items, key = { _, item -> ITEM_KEY + item.key }) { index, item ->
                val dragging = draggingKey == item.key
                Row(
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .fillMaxWidth()
                        .background(if (dragging) colors.surfaceContainer else Color.Transparent)
                        .combinedClickable(
                            enabled = !state.editing,
                            onClick = { actions.onItemClick(index) },
                            onLongClick = { actions.onItemMenu(index) }
                        )
                        .padding(start = if (state.editing) 4.dp else YpScreenMargin, end = 4.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.editing) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .pointerInput(item.key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingKey = item.key
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
                                            dragOffset = moveDragged(
                                                listState,
                                                ITEM_KEY,
                                                keys.value,
                                                item.key,
                                                dragOffset + amount.y,
                                                actions.onMove
                                            )
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = icons.drag,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }
                    AsyncImage(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceContainer),
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.editing) {
                        BarButton(icons.remove, strings.remove) { actions.onRemove(index) }
                    } else {
                        BarButton(icons.more, null) { actions.onItemMenu(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarButton(icon: Painter, description: String?, onClick: () -> Unit) {
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
