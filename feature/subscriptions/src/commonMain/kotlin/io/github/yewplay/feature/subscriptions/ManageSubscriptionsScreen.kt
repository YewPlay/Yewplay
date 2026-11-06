/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSectionTitle
import io.github.yewplay.core.designsystem.YpSkeletonList
import io.github.yewplay.core.model.YpChannelItem

/** A group of channels as the screen shows it. */
data class ManageGroup(val id: Long, val name: String, val icon: Painter)

/** What the screen shows; [channels] is null while they load. */
data class ManageSubscriptionsState(
    val channels: List<YpChannelItem>?,
    val groups: List<ManageGroup>,
    val showBack: Boolean
)

/** Texts of the screen, given by the app so that they follow its translations. */
data class ManageSubscriptionsStrings(
    val title: String,
    val groups: String,
    val newGroup: String,
    val all: String,
    val channels: String,
    val empty: String,
    val back: String,
    val more: String,
    val reorder: String
)

/** Icons of the screen, from the app's drawables. */
data class ManageSubscriptionsIcons(
    val back: Painter,
    val more: Painter,
    val add: Painter,
    val all: Painter,
    val reorder: Painter
)

/** What the screen asks the app to do. */
data class ManageSubscriptionsActions(
    val onBack: () -> Unit,
    val onMore: () -> Unit,
    val onOpenAll: () -> Unit,
    val onOpenGroup: (ManageGroup) -> Unit,
    val onEditGroup: (ManageGroup) -> Unit,
    val onNewGroup: () -> Unit,
    val onReorderGroups: () -> Unit,
    val onOpenChannel: (YpChannelItem) -> Unit,
    val onChannelMenu: (YpChannelItem) -> Unit
)

/**
 * YewPlay: the list of subscriptions: the groups of channels on top (new group, all, each
 * group; long press to edit) and every subscribed channel below (long press for its actions).
 * Import and export are in the menu.
 */
@Composable
fun ManageSubscriptionsScreen(
    state: ManageSubscriptionsState,
    strings: ManageSubscriptionsStrings,
    icons: ManageSubscriptionsIcons,
    actions: ManageSubscriptionsActions
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.showBack) {
                    IconButton(onClick = actions.onBack) {
                        Icon(painter = icons.back, contentDescription = strings.back)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = actions.onMore) {
                    Icon(painter = icons.more, contentDescription = strings.more)
                }
            }
        }
        item {
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin),
                text = strings.title,
                style = MaterialTheme.typography.headlineLarge
            )
        }
        item {
            Row(
                modifier = Modifier.padding(top = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YpSectionTitle(strings.groups, modifier = Modifier.weight(1f))
                if (state.groups.size > 1) {
                    IconButton(onClick = actions.onReorderGroups) {
                        Icon(painter = icons.reorder, contentDescription = strings.reorder)
                    }
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = YpScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { GroupChip(icons.add, strings.newGroup, onClick = actions.onNewGroup) }
                item { GroupChip(icons.all, strings.all, onClick = actions.onOpenAll) }
                items(state.groups, key = { it.id }) { group ->
                    GroupChip(
                        icon = group.icon,
                        label = group.name,
                        onClick = { actions.onOpenGroup(group) },
                        onLongClick = { actions.onEditGroup(group) }
                    )
                }
            }
        }
        item { YpSectionTitle(strings.channels, modifier = Modifier.padding(top = 16.dp)) }
        val channels = state.channels
        when {
            channels == null -> item { YpSkeletonList() }

            channels.isEmpty() -> item {
                Text(
                    modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 8.dp),
                    text = strings.empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> items(channels, key = { it.serviceId.toString() + it.url }) { channel ->
                ChannelRow(
                    channel = channel,
                    onClick = { actions.onOpenChannel(channel) },
                    onLongClick = { actions.onChannelMenu(channel) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(icon: Painter, label: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(channel: YpChannelItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = YpScreenMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        YpAvatar(url = channel.avatarUrl, size = 44)
        Text(
            modifier = Modifier.weight(1f),
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
