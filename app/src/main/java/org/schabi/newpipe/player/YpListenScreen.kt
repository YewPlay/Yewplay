/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.schabi.newpipe.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpThumbnail
import io.github.yewplay.core.designsystem.ypAnimateItem
import io.github.yewplay.core.designsystem.ypMoveDragged
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.schabi.newpipe.util.Localization

/** One row of the play queue, as the "Ouvir" screen shows it. */
data class YpQueueEntry(
    /** Stable identity of the underlying PlayQueueItem, for the list keys. */
    val key: String,
    val serviceId: Int,
    val url: String,
    val title: String,
    /** "Uploader • Service", already localized. */
    val details: String,
    val thumbnailUrl: String?,
    val durationText: String?,
    val isLive: Boolean
)

/** What the big center button shows. */
enum class YpListenPlayState { PLAYING, PAUSED, COMPLETED, BUFFERING }

enum class YpListenRepeatMode { OFF, ONE, ALL }

/** The actions of the menu of a queue row. */
enum class YpQueueItemAction { REMOVE, DETAILS, APPEND_PLAYLIST, CHANNEL_DETAILS, SHARE, DOWNLOAD }

/** Everything the "Ouvir" screen draws, fed by the player callbacks. */
data class YpListenState(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    /** Average cover color with full alpha, or null for a plain background. */
    val coverColor: Int? = null,
    val isLive: Boolean = false,
    val liveSyncEnabled: Boolean = false,
    val progressMillis: Int = 0,
    val durationMillis: Int = 0,
    val bufferPercent: Int = 0,
    val playState: YpListenPlayState = YpListenPlayState.BUFFERING,
    val repeatMode: YpListenRepeatMode = YpListenRepeatMode.OFF,
    val shuffled: Boolean = false,
    val speedText: String = "1x",
    val muted: Boolean = false,
    val sleepTimerActive: Boolean = false,
    val queueItems: List<YpQueueEntry> = emptyList(),
    val queueIndex: Int = -1,
    val queueComplete: Boolean = true,
    /** "16:20 / 48:30", or null when the queue has no known durations. */
    val queueTimeText: String? = null,
    /** Portrait only: the queue replaces the cover while true. */
    val showQueue: Boolean = false,
    /** Bumped to ask the queue list to scroll the playing item into view. */
    val queueScrollSignal: Int = 0,
    val showPopupSwitch: Boolean = true,
    val showBackgroundSwitch: Boolean = true,
    val audioTracks: List<String> = emptyList(),
    val selectedAudioTrack: Int = -1,
    /** Short language code for the audio track toolbar action. */
    val audioTrackCondensed: String = ""
)

data class YpListenLabels(
    val back: String,
    val play: String,
    val pause: String,
    val replay: String,
    val previousStream: String,
    val nextStream: String,
    val rewind: String,
    val forward: String,
    val repeat: String,
    val shuffle: String,
    val live: String,
    val playbackSpeed: String,
    val sleepTimer: String,
    val sleepTimerTitle: String,
    val sleepTimerOff: String,
    val sleepTimerMinutes: (Int) -> String,
    val share: String,
    val queue: String,
    val addToPlaylist: String,
    val mute: String,
    val unmute: String,
    val settings: String,
    val systemAudio: String,
    val switchToMain: String,
    val switchToPopup: String,
    val switchToBackground: String,
    val audioTrack: String,
    val remove: String,
    val streamDetails: String,
    val channelDetails: String,
    val download: String
)

data class YpListenIcons(
    val back: Painter,
    val play: Painter,
    val pause: Painter,
    val replay: Painter,
    val previous: Painter,
    val next: Painter,
    val rewind: Painter,
    val forward: Painter,
    val repeatOff: Painter,
    val repeatOne: Painter,
    val repeatAll: Painter,
    val shuffle: Painter,
    val timer: Painter,
    val share: Painter,
    val queue: Painter,
    val playlistAdd: Painter,
    val volumeOn: Painter,
    val volumeOff: Painter,
    val dragHandle: Painter,
    val moreVert: Painter
)

data class YpListenActions(
    val onBack: () -> Unit,
    val onAppendPlayQueueToPlaylist: () -> Unit,
    val onOpenPlaybackParameters: () -> Unit,
    val onSelectAudioTrack: (Int) -> Unit,
    val onToggleMute: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenSystemAudio: () -> Unit,
    val onSwitchToMain: () -> Unit,
    val onSwitchToPopup: () -> Unit,
    val onSwitchToBackground: () -> Unit,
    val onRepeat: () -> Unit,
    val onPrevious: () -> Unit,
    val onFastRewind: () -> Unit,
    val onPlayPause: () -> Unit,
    val onFastForward: () -> Unit,
    val onNext: () -> Unit,
    val onShuffle: () -> Unit,
    /** Seek to a fraction of the duration, when the user releases the seek bar. */
    val onSeek: (Float) -> Unit,
    val onLiveSync: () -> Unit,
    /** Start the sleep timer, or cancel it with null. */
    val onSleepTimer: (Int?) -> Unit,
    val onShare: () -> Unit,
    val onToggleQueue: () -> Unit,
    val onMetadataClick: () -> Unit,
    val onQueueItemClick: (Int) -> Unit,
    val onQueueItemAction: (Int, YpQueueItemAction) -> Unit,
    val onQueueMove: (Int, Int) -> Unit,
    val onQueueMoveFinished: () -> Unit,
    val onQueueRemove: (Int) -> Unit,
    val onQueueEndReached: () -> Unit
)

private val CoverShape = RoundedCornerShape(22.dp)
private val SleepTimerMinutes = intArrayOf(15, 30, 45, 60, 90)

/**
 * YewPlay: the "Ouvir" screen, in Compose. The gradient tints the top with the average color of
 * the cover, as the legacy screen did with a GradientDrawable.
 */
@Composable
fun YpListenScreen(
    state: YpListenState,
    labels: YpListenLabels,
    icons: YpListenIcons,
    actions: YpListenActions
) {
    val background = MaterialTheme.colorScheme.background
    val topColor = state.coverColor?.let { blend(Color(it), background, 0.55f) } ?: background
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var sleepDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, background)))
    ) {
        YpListenTopBar(
            state = state,
            labels = labels,
            icons = icons,
            actions = actions
        )
        if (landscape) {
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f)) {
                    YpListenQueueList(
                        state = state,
                        labels = labels,
                        icons = icons,
                        actions = actions
                    )
                }
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .padding(end = 8.dp)
                ) {
                    YpListenMetadata(
                        state = state,
                        queueTimeVisible = false,
                        marquee = false,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = actions.onMetadataClick)
                    )
                    YpListenControlsRow(
                        state = state,
                        labels = labels,
                        icons = icons,
                        actions = actions,
                        fastSeekButtons = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                if (state.showQueue) {
                    YpListenQueueList(
                        state = state,
                        labels = labels,
                        icons = icons,
                        actions = actions
                    )
                } else {
                    YpListenCover(coverUrl = state.coverUrl)
                }
            }
            YpListenMetadata(
                state = state,
                queueTimeVisible = true,
                modifier = Modifier.clickable(onClick = actions.onMetadataClick)
            )
            YpListenControlsRow(
                state = state,
                labels = labels,
                icons = icons,
                actions = actions,
                fastSeekButtons = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp)
            )
            YpListenActionsRow(
                state = state,
                labels = labels,
                icons = icons,
                actions = actions,
                onSleepTimerClick = { sleepDialogOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YpScreenMargin)
                    .padding(bottom = 8.dp)
            )
        }
        YpListenSeekRow(
            state = state,
            labels = labels,
            actions = actions,
            showQueueTime = landscape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(8.dp))
    }

    if (sleepDialogOpen) {
        Dialog(onDismissRequest = { sleepDialogOpen = false }) {
            YpSleepTimerContent(
                labels = labels,
                timerActive = state.sleepTimerActive,
                onPick = { minutes ->
                    sleepDialogOpen = false
                    actions.onSleepTimer(minutes)
                }
            )
        }
    }
}

/** [androidx.core.graphics.ColorUtils.blendARGB]: linear interpolation per channel. */
private fun blend(fg: Color, bg: Color, ratio: Float): Color = Color(
    red = fg.red * ratio + bg.red * (1 - ratio),
    green = fg.green * ratio + bg.green * (1 - ratio),
    blue = fg.blue * ratio + bg.blue * (1 - ratio),
    alpha = 1f
)

// Toolbar: back, title and the playback actions that were the options menu.
@Composable
private fun YpListenTopBar(
    state: YpListenState,
    labels: YpListenLabels,
    icons: YpListenIcons,
    actions: YpListenActions
) {
    var overflowOpen by remember { mutableStateOf(false) }
    var audioMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YpListenBarIcon(
            icon = icons.back,
            description = labels.back,
            onClick = actions.onBack
        )
        Text(
            modifier = Modifier.weight(1f),
            text = labels.queue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        YpListenBarIcon(
            icon = icons.playlistAdd,
            description = labels.addToPlaylist,
            onClick = actions.onAppendPlayQueueToPlaylist
        )
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(CircleShape)
                .clickable(onClick = actions.onOpenPlaybackParameters)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.speedText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (state.audioTracks.size >= 2) {
            Box {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .clickable { audioMenuOpen = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.audioTrackCondensed,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = audioMenuOpen,
                    onDismissRequest = { audioMenuOpen = false }
                ) {
                    state.audioTracks.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                audioMenuOpen = false
                                actions.onSelectAudioTrack(index)
                            }
                        )
                    }
                }
            }
        }
        YpListenBarIcon(
            icon = if (state.muted) icons.volumeOff else icons.volumeOn,
            description = if (state.muted) labels.unmute else labels.mute,
            onClick = actions.onToggleMute
        )
        Box {
            YpListenBarIcon(
                icon = icons.moreVert,
                description = null,
                onClick = { overflowOpen = true }
            )
            DropdownMenu(
                expanded = overflowOpen,
                onDismissRequest = { overflowOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(labels.settings) },
                    onClick = {
                        overflowOpen = false
                        actions.onOpenSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text(labels.systemAudio) },
                    onClick = {
                        overflowOpen = false
                        actions.onOpenSystemAudio()
                    }
                )
                DropdownMenuItem(
                    text = { Text(labels.switchToMain) },
                    onClick = {
                        overflowOpen = false
                        actions.onSwitchToMain()
                    }
                )
                if (state.showPopupSwitch) {
                    DropdownMenuItem(
                        text = { Text(labels.switchToPopup) },
                        onClick = {
                            overflowOpen = false
                            actions.onSwitchToPopup()
                        }
                    )
                }
                if (state.showBackgroundSwitch) {
                    DropdownMenuItem(
                        text = { Text(labels.switchToBackground) },
                        onClick = {
                            overflowOpen = false
                            actions.onSwitchToBackground()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun YpListenBarIcon(icon: Painter, description: String?, onClick: () -> Unit) {
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

// The large 1:1 cover of what is playing (portrait).
@Composable
private fun YpListenCover(coverUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CoverShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    }
}

// Song name, artist and the queue progress ("16:20 / 48:30").
@Composable
private fun YpListenMetadata(
    state: YpListenState,
    queueTimeVisible: Boolean,
    modifier: Modifier = Modifier,
    marquee: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YpScreenMargin, vertical = 8.dp)
    ) {
        Text(
            modifier = if (marquee) Modifier.basicMarquee() else Modifier,
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = if (marquee) 1 else 4,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .then(if (marquee) Modifier.basicMarquee() else Modifier),
                text = state.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (marquee) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (queueTimeVisible && state.queueTimeText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.queueTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// Current time, seek bar (with the buffered part), end time or the LIVE button.
@Composable
private fun YpListenSeekRow(
    state: YpListenState,
    labels: YpListenLabels,
    actions: YpListenActions,
    showQueueTime: Boolean,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMillis.coerceAtLeast(0)
    val playedFraction = if (duration > 0) {
        (state.progressMillis / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = dragFraction ?: playedFraction
    val shownMillis = (shownFraction * duration).toInt()

    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(48.dp),
            text = Localization.getDurationString((shownMillis / 1000).toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        YpListenSeekBar(
            fraction = shownFraction,
            bufferFraction = (state.bufferPercent / 100f).coerceIn(0f, 1f),
            onSeekStart = { dragFraction = playedFraction },
            onSeek = { dragFraction = it },
            onSeekEnd = {
                dragFraction = null
                actions.onSeek(it)
            },
            onSeekCancel = { dragFraction = null },
            modifier = Modifier.weight(1f)
        )
        if (showQueueTime && state.queueTimeText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.queueTimeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (state.isLive) {
            Text(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = state.liveSyncEnabled,
                        onClick = actions.onLiveSync
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                text = labels.live,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1
            )
        } else {
            Spacer(Modifier.width(4.dp))
            Text(
                modifier = Modifier.width(48.dp),
                text = Localization.getDurationString((duration / 1000).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * The seek bar: base track, buffered part, played part and thumb. The value only reaches the
 * player when the gesture ends, as the legacy SeekBar did in onStopTrackingTouch.
 */
@Composable
private fun YpListenSeekBar(
    fraction: Float,
    bufferFraction: Float,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    onSeekCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var widthPx by remember { mutableIntStateOf(1) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)
    val currentOnSeekCancel by rememberUpdatedState(onSeekCancel)
    var dragValue by remember { mutableFloatStateOf(0f) }

    fun valueAt(x: Float): Float = (x / widthPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures {
                    onSeekStart()
                    currentOnSeekEnd(valueAt(it.x))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        onSeekStart()
                        dragValue = valueAt(it.x)
                        currentOnSeek(dragValue)
                    },
                    onDragEnd = { currentOnSeekEnd(dragValue) },
                    onDragCancel = { currentOnSeekCancel() }
                ) { change, _ ->
                    change.consume()
                    dragValue = valueAt(change.position.x)
                    currentOnSeek(dragValue)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val thumb = 20.dp
        val thumbPx = with(LocalDensity.current) { thumb.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferFraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.tertiary)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(colors.onSurface)
        )
    }
}

// Repeat, previous, play/pause, next, shuffle; in landscape also rewind and forward.
@Composable
private fun YpListenControlsRow(
    state: YpListenState,
    labels: YpListenLabels,
    icons: YpListenIcons,
    actions: YpListenActions,
    fastSeekButtons: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        YpListenControlIcon(
            icon = when (state.repeatMode) {
                YpListenRepeatMode.OFF -> icons.repeatOff
                YpListenRepeatMode.ONE -> icons.repeatOne
                YpListenRepeatMode.ALL -> icons.repeatAll
            },
            description = labels.repeat,
            size = 30,
            onClick = actions.onRepeat
        )
        YpListenControlIcon(
            icon = icons.previous,
            description = labels.previousStream,
            size = 40,
            onClick = actions.onPrevious
        )
        if (fastSeekButtons) {
            YpListenControlIcon(
                icon = icons.rewind,
                description = labels.rewind,
                size = 40,
                onClick = actions.onFastRewind
            )
        }
        YpListenPlayButton(
            state = state,
            labels = labels,
            icons = icons,
            onClick = actions.onPlayPause
        )
        if (fastSeekButtons) {
            YpListenControlIcon(
                icon = icons.forward,
                description = labels.forward,
                size = 40,
                onClick = actions.onFastForward
            )
        }
        YpListenControlIcon(
            icon = icons.next,
            description = labels.nextStream,
            size = 40,
            onClick = actions.onNext
        )
        YpListenControlIcon(
            icon = icons.shuffle,
            description = labels.shuffle,
            size = 30,
            alpha = if (state.shuffled) 1f else 0.3f,
            onClick = actions.onShuffle
        )
    }
}

/** The big round button: ink circle with the state icon, or a spinner while buffering. */
@Composable
private fun YpListenPlayButton(
    state: YpListenState,
    labels: YpListenLabels,
    icons: YpListenIcons,
    onClick: () -> Unit
) {
    val enabled = state.playState != YpListenPlayState.BUFFERING
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (state.playState == YpListenPlayState.BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.background
            )
        } else {
            Icon(
                modifier = Modifier.size(28.dp),
                painter = when (state.playState) {
                    YpListenPlayState.PLAYING -> icons.pause
                    YpListenPlayState.PAUSED -> icons.play
                    YpListenPlayState.COMPLETED -> icons.replay
                    YpListenPlayState.BUFFERING -> icons.pause
                },
                contentDescription = when (state.playState) {
                    YpListenPlayState.PLAYING -> labels.pause
                    YpListenPlayState.PAUSED -> labels.play
                    YpListenPlayState.COMPLETED -> labels.replay
                    YpListenPlayState.BUFFERING -> null
                },
                tint = MaterialTheme.colorScheme.background
            )
        }
    }
}

@Composable
private fun YpListenControlIcon(
    icon: Painter,
    description: String?,
    size: Int,
    onClick: () -> Unit,
    alpha: Float = 1f
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier
                .size(size.dp)
                .graphicsLayer { this.alpha = alpha },
            painter = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}

// Portrait: speed, sleep timer, share and the queue toggle, under the controls.
@Composable
private fun YpListenActionsRow(
    state: YpListenState,
    labels: YpListenLabels,
    icons: YpListenIcons,
    actions: YpListenActions,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(CircleShape)
                .clickable(onClick = actions.onOpenPlaybackParameters)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.speedText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        YpListenActionIcon(
            icon = icons.timer,
            description = labels.sleepTimer,
            tint = if (state.sleepTimerActive) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            onClick = onSleepTimerClick
        )
        YpListenActionIcon(
            icon = icons.share,
            description = labels.share,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = actions.onShare
        )
        YpListenActionIcon(
            icon = icons.queue,
            description = labels.queue,
            tint = if (state.showQueue) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            onClick = actions.onToggleQueue
        )
    }
}

@Composable
private fun YpListenActionIcon(
    icon: Painter,
    description: String?,
    tint: Color,
    onClick: () -> Unit
) {
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
            tint = tint
        )
    }
}

private const val QUEUE_KEY_PREFIX = "queue:"
private const val SMOOTH_SCROLL_MAXIMUM_DISTANCE = 80

// The queue: selectable rows, reordered from the handle, removed with a right swipe.
@Composable
private fun YpListenQueueList(
    state: YpListenState,
    labels: YpListenLabels,
    icons: YpListenIcons,
    actions: YpListenActions
) {
    val listState = rememberLazyListState()
    val items = rememberUpdatedState(state.queueItems)
    val queueComplete = rememberUpdatedState(state.queueComplete)
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentDragOffset by rememberUpdatedState(dragOffset)
    val currentDragKey by rememberUpdatedState(draggingKey)

    // scroll the playing item into view when asked (metadata click, queue shown, track change)
    LaunchedEffect(state.queueScrollSignal) {
        if (state.queueScrollSignal <= 0 || state.queueIndex < 0) {
            return@LaunchedEffect
        }
        val firstVisible = listState.firstVisibleItemIndex
        if (kotlin.math.abs(state.queueIndex - firstVisible) < SMOOTH_SCROLL_MAXIMUM_DISTANCE) {
            listState.animateScrollToItem(state.queueIndex)
        } else {
            listState.scrollToItem(state.queueIndex)
        }
    }

    // more items when the end is reached and the queue is not complete
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val size = items.value.size
                if (last != null && size > 0 && last >= size - 1 && !queueComplete.value) {
                    actions.onQueueEndReached()
                }
            }
    }

    // keep the dragged row reachable by scrolling near the edges, as ItemTouchHelper did
    LaunchedEffect(draggingKey != null) {
        while (currentDragKey != null) {
            val info = listState.layoutInfo
            val dragged = info.visibleItemsInfo.firstOrNull {
                it.key == QUEUE_KEY_PREFIX + currentDragKey
            }
            if (dragged != null) {
                val center = dragged.offset + currentDragOffset + dragged.size / 2f
                when {
                    center > info.viewportEndOffset - 60 -> listState.scrollBy(14f)
                    center < info.viewportStartOffset + 60 -> listState.scrollBy(-14f)
                }
            }
            delay(16)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(
            items = state.queueItems,
            key = { _, entry -> QUEUE_KEY_PREFIX + entry.key }
        ) { index, entry ->
            val dragging = draggingKey == entry.key
            YpListenQueueRow(
                entry = entry,
                selected = index == state.queueIndex,
                labels = labels,
                dragHandle = icons.dragHandle,
                modifier = Modifier
                    .ypAnimateItem(this)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                onClick = { actions.onQueueItemClick(index) },
                onAction = { action -> actions.onQueueItemAction(index, action) },
                onRemove = { actions.onQueueRemove(index) },
                dragModifier = Modifier.pointerInput(entry.key) {
                    detectDragGestures(
                        onDragStart = {
                            draggingKey = entry.key
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            draggingKey = null
                            dragOffset = 0f
                            actions.onQueueMoveFinished()
                        },
                        onDragCancel = {
                            draggingKey = null
                            dragOffset = 0f
                            actions.onQueueMoveFinished()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            dragOffset = ypMoveDragged(
                                listState,
                                QUEUE_KEY_PREFIX,
                                items.value.map { it.key },
                                entry.key,
                                dragOffset,
                                actions.onQueueMove
                            )
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun YpListenQueueRow(
    entry: YpQueueEntry,
    selected: Boolean,
    labels: YpListenLabels,
    dragHandle: Painter,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAction: (YpQueueItemAction) -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var swipeOffset by remember(entry.key) { mutableFloatStateOf(0f) }
    var rowWidth by remember(entry.key) { mutableIntStateOf(1) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidth = it.width.coerceAtLeast(1) }
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
                .pointerInput(entry.key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffset > rowWidth / 2f) {
                                onRemove()
                            }
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f }
                    ) { change, amount ->
                        change.consume()
                        // a right swipe removes the row, as the legacy ItemTouchHelper did
                        swipeOffset = (swipeOffset + amount).coerceIn(0f, rowWidth.toFloat())
                    }
                }
                .padding(horizontal = YpScreenMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YpThumbnail(
                url = entry.thumbnailUrl,
                durationText = entry.durationText,
                isLive = entry.isLive,
                progress = 0f
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                modifier = dragModifier
                    .size(36.dp)
                    .padding(8.dp),
                painter = dragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(labels.remove) },
                onClick = {
                    menuOpen = false
                    onAction(YpQueueItemAction.REMOVE)
                }
            )
            DropdownMenuItem(
                text = { Text(labels.streamDetails) },
                onClick = {
                    menuOpen = false
                    onAction(YpQueueItemAction.DETAILS)
                }
            )
            DropdownMenuItem(
                text = { Text(labels.addToPlaylist) },
                onClick = {
                    menuOpen = false
                    onAction(YpQueueItemAction.APPEND_PLAYLIST)
                }
            )
            DropdownMenuItem(
                text = { Text(labels.channelDetails) },
                onClick = {
                    menuOpen = false
                    onAction(YpQueueItemAction.CHANNEL_DETAILS)
                }
            )
            DropdownMenuItem(
                text = { Text(labels.share) },
                onClick = {
                    menuOpen = false
                    onAction(YpQueueItemAction.SHARE)
                }
            )
            DropdownMenuItem(
                text = { Text(labels.download) },
                onClick = {
                    menuOpen = false
                    onAction(YpQueueItemAction.DOWNLOAD)
                }
            )
        }
    }
}

// The sleep timer options of the legacy dialog, as a YpDialogCard with rows.
@Composable
private fun YpSleepTimerContent(
    labels: YpListenLabels,
    timerActive: Boolean,
    onPick: (Int?) -> Unit
) {
    YpDialogCard(title = labels.sleepTimerTitle) {
        Column {
            SleepTimerMinutes.forEach { minutes ->
                YpSleepTimerOption(
                    label = labels.sleepTimerMinutes(minutes),
                    onClick = { onPick(minutes) }
                )
            }
            if (timerActive) {
                YpSleepTimerOption(
                    label = labels.sleepTimerOff,
                    onClick = { onPick(null) }
                )
            }
        }
    }
}

@Composable
private fun YpSleepTimerOption(label: String, onClick: () -> Unit) {
    BasicText(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        text = label,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}
