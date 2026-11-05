/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpErrorState
import io.github.yewplay.core.designsystem.YpFeaturedVideo
import io.github.yewplay.core.designsystem.YpLoadingMore
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSkeletonList
import io.github.yewplay.core.designsystem.YpTextTabs
import io.github.yewplay.core.designsystem.YpVideoCard
import io.github.yewplay.core.model.YpError
import io.github.yewplay.core.model.YpStreamItem

/* -------------------------------------------------------------------------------------------- */
/* Header                                                                                       */
/* -------------------------------------------------------------------------------------------- */

/** What the home header shows. */
data class HomeHeaderState(
    val serviceName: String,
    val region: String?,
    val title: String,
    val tabs: List<String>,
    val selectedTab: Int
)

/**
 * The top of the home (proposal, p. 13): the service (tap to change) and the region in small
 * text above a big title, the bell, and the tabs with the ruby dot.
 */
@Composable
fun HomeHeader(
    state: HomeHeaderState,
    chevron: Painter,
    bell: Painter,
    bellDescription: String,
    onServiceClick: () -> Unit,
    onBellClick: () -> Unit,
    onTabSelect: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = YpScreenMargin, end = YpScreenMargin, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onServiceClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.serviceName,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Icon(
                    modifier = Modifier.size(14.dp),
                    painter = chevron,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
            Box(modifier = Modifier.weight(1f))
            state.region?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = YpScreenMargin, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = state.title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBellClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = bell,
                    contentDescription = bellDescription,
                    tint = colors.onBackground
                )
            }
        }
        if (state.tabs.size > 1) {
            YpTextTabs(labels = state.tabs, selectedIndex = state.selectedTab, onSelect = onTabSelect)
        }
    }
}

/* -------------------------------------------------------------------------------------------- */
/* Kiosk                                                                                        */
/* -------------------------------------------------------------------------------------------- */

/** Everything a home tab (kiosk) shows. */
data class KioskUiState(
    val items: List<YpStreamItem> = emptyList(),
    /** "No. 1 · Music", over the first video. */
    val featuredLabel: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isEmpty: Boolean = false,
    val emptyText: String? = null,
    val error: YpError? = null
)

/** Texts of a home tab. */
data class KioskStrings(
    val empty: String,
    val emptyDetail: String,
    val error: YpErrorLabels
)

/** What a home tab asks the app to do. */
data class KioskActions(
    val onItemClick: (Int) -> Unit,
    val onItemMenu: (Int) -> Unit,
    val onLoadMore: () -> Unit,
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onErrorAction: () -> Unit,
    val onErrorOpenInBrowser: () -> Unit
)

/** A home tab: the first video as an edge-to-edge highlight, then large items (p. 19). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    state: KioskUiState,
    strings: KioskStrings,
    moreIcon: Painter,
    actions: KioskActions
) {
    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.items.size, state.hasMore, state.isLoadingMore) {
        if (nearEnd && state.hasMore && !state.isLoadingMore && !state.isLoading) {
            actions.onLoadMore()
        }
    }

    val error = state.error
    when {
        error != null -> YpErrorState(
            error = error,
            labels = strings.error,
            onRetry = actions.onRetry,
            onAction = actions.onErrorAction,
            onOpenInBrowser = actions.onErrorOpenInBrowser
        )

        state.isLoading -> Column {
            // blocks in the shape of the content (proposal, p. 17)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
            Spacer(Modifier.height(14.dp))
            YpSkeletonList(rows = 4)
        }

        state.isEmpty -> YpMessageState(state.emptyText ?: strings.empty, strings.emptyDetail)

        else -> PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(state.items, key = { index, item -> "$index:${item.url}" }) { index, item ->
                    if (index == 0) {
                        YpFeaturedVideo(
                            modifier = Modifier.padding(bottom = 14.dp),
                            item = item,
                            label = state.featuredLabel,
                            onClick = { actions.onItemClick(index) },
                            onLongClick = { actions.onItemMenu(index) }
                        )
                    } else {
                        YpVideoCard(
                            item = item,
                            onClick = { actions.onItemClick(index) },
                            onLongClick = { actions.onItemMenu(index) },
                            moreIcon = moreIcon
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
