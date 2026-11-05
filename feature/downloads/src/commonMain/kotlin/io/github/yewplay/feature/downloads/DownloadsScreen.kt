/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpEngraving
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTextTabs
import net.newpipe.app.yewplay.engraving.YpEngravings

/** What a download is: drives the placeholder icon and the type filter. */
enum class DownloadKind { VIDEO, AUDIO, OTHER }

/** A download, in progress or finished. Texts arrive already localized. */
data class DownloadItem(
    val key: String,
    val title: String,
    val kind: DownloadKind,
    /** A frame of the file or its cover, when known (any model Coil can load). */
    val thumbnail: Any? = null,
    val durationText: String? = null,
    /** From 0 to 1; null while the size is unknown. */
    val progress: Float? = null,
    val percentText: String? = null,
    /** "82 of 180 MB, 2.1 MB/s", "Video MP4 1080p, 175.7 MB", ... */
    val detail: String,
    /** "27 Sep" for finished downloads. */
    val date: String? = null,
    val running: Boolean = false,
    val hasError: Boolean = false,
    /** Whether the play/pause button can act on it. */
    val canToggle: Boolean = false
)

/** An entry of the ⋮ menu of the top bar. */
data class DownloadsMenuEntry(val label: String, val onClick: () -> Unit)

/** Everything the downloads screen shows. */
data class DownloadsUiState(
    val usedText: String = "",
    val freeText: String = "",
    val usedFraction: Float = 0f,
    val selectedTab: Int = 0,
    val pending: List<DownloadItem> = emptyList(),
    val finished: List<DownloadItem> = emptyList(),
    /** "Pause all" when something runs, "Resume all" when everything is paused. */
    val canPauseAll: Boolean = false,
    val canResumeAll: Boolean = false,
    val isLoading: Boolean = true
)

/** Texts of the screen, from the app's strings. */
data class DownloadsStrings(
    val title: String,
    val back: String,
    val tabs: List<String>,
    val downloading: String,
    val finished: String,
    val pauseAll: String,
    val resumeAll: String,
    val itemCount: (Int) -> String,
    val empty: String,
    val emptyDetail: String,
    val howToDownload: String
)

/** Icons of the screen, from the app's drawables. */
data class DownloadsIcons(
    val back: Painter,
    val more: Painter,
    val pause: Painter,
    val play: Painter,
    val video: Painter,
    val audio: Painter,
    val other: Painter,
    val download: Painter
)

/** What the screen asks the app to do. */
data class DownloadsActions(
    val onBack: () -> Unit,
    val onTabSelect: (Int) -> Unit,
    val onPauseAll: () -> Unit,
    val onResumeAll: () -> Unit,
    val onItemClick: (DownloadItem) -> Unit,
    val onItemToggle: (DownloadItem) -> Unit,
    val onItemMenu: (DownloadItem) -> Unit,
    val onHowToDownload: () -> Unit
)

/**
 * Downloads (proposal, p. 15 and 19): space used on top, tabs All/Videos/Audio, downloads in
 * progress with progress, size and speed and pause on the item, finished ones with format,
 * quality, size and date.
 */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    menu: List<DownloadsMenuEntry>,
    strings: DownloadsStrings,
    icons: DownloadsIcons,
    actions: DownloadsActions
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(menu, strings, icons, actions)
        val empty = !state.isLoading && state.pending.isEmpty() && state.finished.isEmpty()
        if (empty && state.selectedTab == 0) {
            NothingDownloaded(strings, icons, actions)
            return@Column
        }
        Storage(state)
        YpTextTabs(labels = strings.tabs, selectedIndex = state.selectedTab, onSelect = actions.onTabSelect)
        if (empty) {
            YpMessageState(title = strings.empty, detail = strings.emptyDetail)
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (state.pending.isNotEmpty()) {
                item(key = "pending-header") {
                    SectionHeader(
                        title = strings.downloading,
                        action = when {
                            state.canPauseAll -> strings.pauseAll to actions.onPauseAll
                            state.canResumeAll -> strings.resumeAll to actions.onResumeAll
                            else -> null
                        },
                        trailing = null
                    )
                }
                items(state.pending, key = { "p:" + it.key }) { item ->
                    DownloadRow(item, icons, actions)
                }
            }
            if (state.finished.isNotEmpty()) {
                item(key = "finished-header") {
                    SectionHeader(
                        title = strings.finished,
                        action = null,
                        trailing = strings.itemCount(state.finished.size)
                    )
                }
                items(state.finished, key = { "f:" + it.key }) { item ->
                    DownloadRow(item, icons, actions)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    menu: List<DownloadsMenuEntry>,
    strings: DownloadsStrings,
    icons: DownloadsIcons,
    actions: DownloadsActions
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(icons.back, strings.back, actions.onBack)
            Spacer(Modifier.weight(1f))
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
        Text(
            modifier = Modifier.padding(horizontal = YpScreenMargin),
            text = strings.title,
            style = MaterialTheme.typography.headlineLarge
        )
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
private fun Storage(state: DownloadsUiState) {
    if (state.usedText.isEmpty()) {
        return
    }
    Column(modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.weight(1f),
                text = state.usedText,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = state.freeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.onBackground,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
private fun SectionHeader(title: String, action: Pair<String, () -> Unit>?, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = YpScreenMargin, end = 8.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        if (action != null) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = action.second)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                text = action.first,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (trailing != null) {
            Text(
                modifier = Modifier.padding(end = 12.dp),
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(item: DownloadItem, icons: DownloadsIcons, actions: DownloadsActions) {
    val colors = MaterialTheme.colorScheme
    val finished = item.date != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { actions.onItemClick(item) },
                onLongClick = { actions.onItemMenu(item) }
            )
            .padding(start = YpScreenMargin, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnail != null) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = when (item.kind) {
                        DownloadKind.VIDEO -> icons.video
                        DownloadKind.AUDIO -> icons.audio
                        DownloadKind.OTHER -> icons.other
                    },
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
            item.durationText?.let {
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC18141A))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF3EEE9)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!finished) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val progress = item.progress
                    val barColor = when {
                        item.hasError -> colors.error
                        item.running -> colors.tertiary
                        else -> colors.onSurfaceVariant
                    }
                    val barModifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                    if (progress == null) {
                        LinearProgressIndicator(
                            modifier = barModifier,
                            color = barColor,
                            trackColor = colors.surfaceContainerHighest
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = barModifier,
                            color = barColor,
                            trackColor = colors.surfaceContainerHighest
                        )
                    }
                    item.percentText?.let {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.hasError) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.date?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (!finished && item.canToggle) {
            BarIcon(
                icon = if (item.running) icons.pause else icons.play,
                description = null,
                onClick = { actions.onItemToggle(item) }
            )
        } else {
            BarIcon(icon = icons.more, description = null, onClick = { actions.onItemMenu(item) })
        }
    }
}

/** Nothing downloaded yet (proposal, p. 9): the standing yew sprig and how to download. */
@Composable
private fun NothingDownloaded(strings: DownloadsStrings, icons: DownloadsIcons, actions: DownloadsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        YpEngraving(
            modifier = Modifier
                .padding(top = 40.dp, bottom = 28.dp)
                .height(200.dp)
                .aspectRatio(YpEngravings.downloads.aspectRatio),
            art = YpEngravings.downloads
        )
        Text(
            text = strings.empty,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            text = strings.emptyDetail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = actions.onHowToDownload)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                modifier = Modifier.size(19.dp),
                painter = icons.download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(text = strings.howToDownload, style = MaterialTheme.typography.labelLarge)
        }
    }
}
