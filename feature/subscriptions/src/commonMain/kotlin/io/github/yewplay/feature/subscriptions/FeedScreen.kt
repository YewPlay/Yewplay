/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpEngraving
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpErrorState
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSkeletonRow
import io.github.yewplay.core.designsystem.YpVideoRow
import io.github.yewplay.core.model.YpChannel
import io.github.yewplay.core.model.YpError
import io.github.yewplay.core.model.YpGroup
import io.github.yewplay.core.model.YpStreamItem
import net.newpipe.app.yewplay.engraving.YpEngravings

/** A video of the feed. */
data class FeedEntry(val key: String, val item: YpStreamItem)

/** "Today", "Yesterday", ... with its videos; the first one also shows the counts. */
data class FeedSection(val title: String, val trailing: String?, val entries: List<FeedEntry>)

/** An entry of the ⋮ menu of the top bar. */
data class FeedMenuEntry(val label: String, val onClick: () -> Unit)

/** Everything the "Subscriptions" screen shows. */
data class FeedUiState(
    val channels: List<YpChannel> = emptyList(),
    val groups: List<YpGroup> = emptyList(),
    val selectedGroupId: Long? = null,
    val sections: List<FeedSection> = emptyList(),
    /** Nothing to show yet (first load). */
    val isLoading: Boolean = true,
    /** Fetching new videos from the channels. */
    val isRefreshing: Boolean = false,
    /** "12/40" while refreshing; null when unknown. */
    val progressText: String? = null,
    /** From 0 to 1 while refreshing; null when unknown. */
    val progress: Float? = null,
    /** "Not loaded: 2", when some channels could not be updated. */
    val notice: String? = null,
    val isEmpty: Boolean = false,
    val error: YpError? = null,
    /** False once it is known that there is no subscription: the "grow here" state. */
    val hasSubscriptions: Boolean? = null
)

/** Texts of the screen, from the app's strings. */
data class FeedStrings(
    val title: String,
    val header: SubscriptionsStrings,
    val filter: String,
    val empty: String,
    val emptyDetail: String,
    val refresh: String,
    val error: YpErrorLabels,
    val noSubscriptionsTitle: String,
    val noSubscriptionsText: String,
    val findChannels: String,
    val importSubscriptions: String
)

/** Icons of the screen, from the app's drawables. */
data class FeedIcons(
    val filter: Painter,
    val menu: Painter,
    val itemMenu: Painter
)

/** What the screen asks the app to do. */
data class FeedActions(
    val header: SubscriptionsActions,
    val onFilter: () -> Unit,
    val onItemClick: (FeedEntry) -> Unit,
    val onItemMenu: (FeedEntry) -> Unit,
    val onRefresh: () -> Unit,
    val onErrorAction: () -> Unit,
    val onFindChannels: () -> Unit,
    val onImportSubscriptions: () -> Unit
)

/**
 * "Subscriptions" (proposal, p. 14): channel avatars with the ruby dot, groups as tabs and
 * the feed by day ("Today", "Yesterday"), with the count of new videos; pull to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    menu: List<FeedMenuEntry>,
    strings: FeedStrings,
    icons: FeedIcons,
    actions: FeedActions
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(menu, strings, icons, actions)
        if (state.hasSubscriptions == false) {
            NoSubscriptions(strings, icons, actions)
            return@Column
        }
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = state.isRefreshing && !state.isLoading,
            onRefresh = actions.onRefresh
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "header") {
                    SubscriptionsHeader(
                        channels = state.channels,
                        groups = state.groups,
                        selectedGroupId = state.selectedGroupId,
                        strings = strings.header,
                        actions = actions.header
                    )
                }
                if (state.isRefreshing) {
                    item(key = "progress") { Progress(state) }
                }
                state.notice?.let { notice ->
                    item(key = "notice") {
                        Text(
                            modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 4.dp),
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val error = state.error
                when {
                    error != null -> item(key = "error") {
                        YpErrorState(
                            modifier = Modifier.height(320.dp),
                            error = error,
                            labels = strings.error,
                            onRetry = actions.onRefresh,
                            onAction = actions.onErrorAction,
                            onOpenInBrowser = { }
                        )
                    }

                    state.isLoading -> items(6) { YpSkeletonRow() }

                    state.isEmpty -> item(key = "empty") {
                        YpMessageState(
                            modifier = Modifier.height(320.dp),
                            title = strings.empty,
                            detail = strings.emptyDetail
                        ) {
                            YpPillButton(strings.refresh, primary = true, onClick = actions.onRefresh)
                        }
                    }

                    else -> state.sections.forEach { section ->
                        item(key = "section:${section.title}") { SectionHeader(section) }
                        items(section.entries, key = { it.key }) { entry ->
                            YpVideoRow(
                                item = entry.item,
                                onClick = { actions.onItemClick(entry) },
                                onLongClick = { actions.onItemMenu(entry) },
                                moreIcon = icons.itemMenu
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    menu: List<FeedMenuEntry>,
    strings: FeedStrings,
    icons: FeedIcons,
    actions: FeedActions
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = YpScreenMargin, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = strings.title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BarIcon(icons.filter, strings.filter, actions.onFilter)
        Box {
            BarIcon(icons.menu, null) { menuOpen = true }
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
private fun Progress(state: FeedUiState) {
    Column(modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 8.dp)) {
        val progress = state.progress
        val color = MaterialTheme.colorScheme.tertiary
        val track = MaterialTheme.colorScheme.surfaceContainer
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = color,
                trackColor = track
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = color,
                trackColor = track
            )
        }
        state.progressText?.let {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(section: FeedSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = YpScreenMargin, end = YpScreenMargin, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = section.title, style = MaterialTheme.typography.titleMedium)
        section.trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** No subscription yet (proposal, p. 9 and 17): the yew engraving and the two first steps. */
@Composable
private fun NoSubscriptions(strings: FeedStrings, icons: FeedIcons, actions: FeedActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YpScreenMargin)
    ) {
        YpEngraving(
            modifier = Modifier
                .padding(top = 24.dp, bottom = 28.dp)
                .fillMaxWidth(0.82f)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(YpEngravings.subscriptions.aspectRatio),
            art = YpEngravings.subscriptions
        )
        Text(text = strings.noSubscriptionsTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            text = strings.noSubscriptionsText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        YpPillButton(
            modifier = Modifier.fillMaxWidth(),
            label = strings.findChannels,
            primary = true,
            onClick = actions.onFindChannels
        )
        YpPillButton(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 24.dp)
                .fillMaxWidth(),
            label = strings.importSubscriptions,
            primary = false,
            onClick = actions.onImportSubscriptions
        )
    }
}
