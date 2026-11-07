/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.schabi.newpipe.player.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.ypMoveDragged
import kotlin.math.roundToInt
import net.newpipe.app.theme.LocalReduceMotion
import net.newpipe.app.theme.MotionDurations
import net.newpipe.app.theme.MotionEasings
import net.newpipe.app.theme.motionSpec
import org.schabi.newpipe.player.YpQueueEntry
import org.schabi.newpipe.player.YpQueueItemAction

private val ScrimColor = Color(0x99000000)
private val BubbleColor = Color(0x60000000)
private val PanelColor = Color(0xE6141414)
private val AccentRed = Color(0xFFF44336)

enum class YpPlayAction { PLAY, PAUSE, REPLAY }

enum class YpPanelMode { NONE, QUEUE, SEGMENTS }

/** A chapter/segment entry of the segments panel. */
data class YpSegmentEntry(
    val title: String,
    val startSeconds: Int,
    val selected: Boolean
)

/** Everything the player chrome shows; updated from the player's callbacks. */
data class YpChromeState(
    val controlsVisible: Boolean = false,
    /** Black loading panel with spinner: the player is blocked (e.g. switching streams). */
    val blocked: Boolean = false,
    /** Transparent spinner over the video: buffering during playback. */
    val buffering: Boolean = false,
    val title: String = "",
    val uploader: String = "",
    val playAction: YpPlayAction = YpPlayAction.PLAY,
    val showPrevious: Boolean = false,
    val showNext: Boolean = false,
    val seekEnabled: Boolean = true,
    val progressMillis: Int = 0,
    val durationMillis: Int = 0,
    val bufferPercent: Int = 0,
    /** Center bubble text while dragging the seek bar; null when not seeking. */
    val seekDisplayText: String? = null,
    /** Preview thumbnail at the dragged position; null when unavailable. */
    val seekPreview: Bitmap? = null,
    val seekPreviewWidthFraction: Float = 0.25f,
    /** Chapter title bubble while dragging; null when there are no chapters. */
    val chapterTitle: String? = null,
    /** Chapter marker positions as fractions of the duration. */
    val chapterFractions: List<Float> = emptyList(),
    val isLive: Boolean = false,
    val liveSyncEnabled: Boolean = true,
    val currentTimeText: String = "",
    val endTimeText: String = "",
    val speedText: String = "1x",
    val qualityText: String = "",
    val showQuality: Boolean = false,
    val audioTrackText: String = "",
    val showAudioTrack: Boolean = false,
    val captionText: String = "",
    val showCaption: Boolean = false,
    val resizeText: String = "",
    val showResize: Boolean = false,
    val shuffled: Boolean = false,
    val muted: Boolean = false,
    val repeatOff: Boolean = true,
    val repeatOne: Boolean = false,
    // main player chrome
    /** Fullscreen main player: only then the chrome applies system bar insets. */
    val fullscreen: Boolean = false,
    val metadataVisible: Boolean = false,
    val closeVisible: Boolean = true,
    val secondaryVisible: Boolean = false,
    val showFullScreen: Boolean = false,
    val showKodi: Boolean = false,
    val showShare: Boolean = true,
    val showOpenInBrowser: Boolean = true,
    val showMute: Boolean = true,
    val showQueue: Boolean = false,
    val showSegments: Boolean = false,
    val screenRotationVisible: Boolean = false,
    val screenRotationIsExit: Boolean = false,
    // popup chrome
    val popupMode: Boolean = false,
    val closingOverlayVisible: Boolean = false,
    // queue / segments panel
    val panelMode: YpPanelMode = YpPanelMode.NONE,
    val queueItems: List<YpQueueEntry> = emptyList(),
    val queueIndex: Int = -1,
    val queueTimeText: String = "",
    val segments: List<YpSegmentEntry> = emptyList(),
    val queueComplete: Boolean = true,
    // gesture indicators
    val volumeIndicator: Float? = null,
    val brightnessIndicator: Float? = null,
    // menus
    val qualityOptions: List<String> = emptyList(),
    val audioTrackOptions: List<String> = emptyList(),
    val captionOptions: List<String> = emptyList(),
    val speedOptions: List<String> = emptyList()
)

class YpChromeLabels(
    val play: String,
    val pause: String,
    val replay: String,
    val previousStream: String,
    val nextStream: String,
    val close: String,
    val moreOptions: String,
    val repeat: String,
    val shuffle: String,
    val live: String,
    val queue: String,
    val chapters: String,
    val addToPlaylist: String,
    val toggleFullscreen: String,
    val toggleOrientation: String,
    val share: String,
    val mute: String,
    val unmute: String,
    val kodi: String,
    val openInBrowser: String,
    val remove: String,
    val streamDetails: String,
    val channelDetails: String,
    val download: String,
    val captionNone: String
)

class YpChromeIcons(
    val close: Painter,
    val play: Painter,
    val pause: Painter,
    val replay: Painter,
    val previous: Painter,
    val next: Painter,
    val repeatOff: Painter,
    val repeatOne: Painter,
    val repeatAll: Painter,
    val shuffle: Painter,
    val moreOptions: Painter,
    val fullScreen: Painter,
    val fullScreenExit: Painter,
    val kodi: Painter,
    val browser: Painter,
    val share: Painter,
    val volumeOff: Painter,
    val volumeMute: Painter,
    val volumeDown: Painter,
    val volumeUp: Painter,
    val brightnessLow: Painter,
    val brightnessMedium: Painter,
    val brightnessHigh: Painter,
    val queue: Painter,
    val segments: Painter,
    val playlistAdd: Painter,
    val drag: Painter
)

class YpChromeActions(
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeekStart: () -> Unit = {},
    /** Called continuously while dragging, with the fraction and the position in ms. */
    val onSeekPreview: (Float, Int) -> Unit = { _, _ -> },
    val onSeekEnd: (Int) -> Unit = {},
    val onSeekCancel: () -> Unit = {},
    val onLiveSync: () -> Unit = {},
    val onQualitySelected: (Int) -> Unit = {},
    val onAudioTrackSelected: (Int) -> Unit = {},
    /** -1 disables captions. */
    val onCaptionSelected: (Int) -> Unit = {},
    val onSpeedSelected: (Int) -> Unit = {},
    /** Main player opens the full playback parameter dialog instead of the speed menu. */
    val onPlaybackSpeedDialog: () -> Unit = {},
    val onResize: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onMoreOptions: () -> Unit = {},
    val onMoreOptionsLong: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onShareLong: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onKodi: () -> Unit = {},
    val onOpenInBrowser: () -> Unit = {},
    val onFullScreen: () -> Unit = {},
    val onScreenRotation: () -> Unit = {},
    val onQueueClicked: () -> Unit = {},
    val onSegmentsClicked: () -> Unit = {},
    val onPanelClose: () -> Unit = {},
    val onAddToPlaylist: () -> Unit = {},
    val onQueueSelect: (Int) -> Unit = {},
    val onQueueItemAction: (Int, YpQueueItemAction) -> Unit = { _, _ -> },
    val onQueueMove: (Int, Int) -> Unit = { _, _ -> },
    val onQueueMoveFinished: () -> Unit = {},
    val onQueueEndReached: () -> Unit = {},
    val onSegmentClick: (Int) -> Unit = {},
    val onSegmentLongClick: (Int) -> Unit = {},
    /** Any chrome click: used to re-schedule the auto-hide of the controls. */
    val onAnyClick: (Boolean) -> Unit = {}
)

@Composable
fun YpPlayerChrome(
    state: YpChromeState,
    labels: YpChromeLabels,
    icons: YpChromeIcons,
    actions: YpChromeActions
) {
    val reduceMotion = LocalReduceMotion.current
    Box(Modifier.fillMaxSize()) {
        if (state.blocked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
            return@Box
        }

        if (state.buffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        YpGestureIndicators(state, icons)

        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                // scrim that replaces playbackControlsShadow + top/bottom shadow views
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ScrimColor)
                )
                Column(Modifier.fillMaxSize()) {
                    YpTopControls(state, labels, icons, actions)
                    Spacer(Modifier.weight(1f))
                    YpCenterControls(state, labels, icons, actions)
                    Spacer(Modifier.weight(1f))
                    YpBottomControls(state, labels, icons, actions)
                }
            }
        }

        // seek preview bubble floats over the seek bar even while controls fade logic runs
        if (state.controlsVisible && state.seekDisplayText != null) {
            YpSeekPreview(state)
        }

        if (state.closingOverlayVisible) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = icons.close,
                    contentDescription = labels.close,
                    tint = AccentRed,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = state.panelMode != YpPanelMode.NONE,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            YpItemsPanel(state, labels, icons, actions)
        }
    }
}

@Composable
private fun YpGestureIndicators(state: YpChromeState, icons: YpChromeIcons) {
    val volume = state.volumeIndicator
    val brightness = state.brightnessIndicator
    val fraction = volume ?: brightness ?: return
    val icon = when {
        volume != null && fraction <= 0f -> icons.volumeOff
        volume != null && fraction < 0.25f -> icons.volumeMute
        volume != null && fraction < 0.75f -> icons.volumeDown
        volume != null -> icons.volumeUp
        fraction < 0.25f -> icons.brightnessLow
        fraction < 0.75f -> icons.brightnessMedium
        else -> icons.brightnessHigh
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BubbleColor)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(painter = icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.width(120.dp),
                color = Color.White,
                trackColor = Color(0x40FFFFFF)
            )
        }
    }
}

@Composable
private fun YpChromeButton(
    painter: Painter,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    rotation: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    if (alpha <= 0f) {
        // mirrors View.INVISIBLE: reserves the space but does not draw or receive clicks
        Spacer(modifier.size(size))
        return
    }
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = modifier
            .size(size)
            .rotate(rotation)
            .graphicsLayer(alpha = alpha)
            .let {
                if (onLongClick != null) {
                    it.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    it.clickable(onClick = onClick)
                }
            }
            .padding(if (size >= 40.dp) 8.dp else 4.dp)
    )
}

@Composable
private fun YpTopControls(
    state: YpChromeState,
    labels: YpChromeLabels,
    icons: YpChromeIcons,
    actions: YpChromeActions
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (state.fullscreen) Modifier.systemBarsPadding() else Modifier)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(56.dp)
        ) {
            if (state.closeVisible) {
                YpChromeButton(icons.close, labels.close, 36.dp) {
                    actions.onClose()
                    actions.onAnyClick(false)
                }
                Spacer(Modifier.width(8.dp))
            }
            if (state.metadataVisible) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = state.uploader,
                        color = Color(0xB3FFFFFF),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (state.showAudioTrack) {
                YpTextMenuButton(
                    text = state.audioTrackText,
                    options = state.audioTrackOptions,
                    onSelected = actions.onAudioTrackSelected,
                    onAnyClick = actions.onAnyClick
                )
            }
            if (state.showQuality) {
                YpTextMenuButton(
                    text = state.qualityText,
                    options = state.qualityOptions,
                    onSelected = actions.onQualitySelected,
                    onAnyClick = actions.onAnyClick
                )
            }
            if (state.popupMode) {
                YpTextMenuButton(
                    text = state.speedText,
                    options = state.speedOptions,
                    onSelected = actions.onSpeedSelected,
                    onAnyClick = actions.onAnyClick
                )
            } else {
                TextButton(onClick = {
                    actions.onPlaybackSpeedDialog()
                    actions.onAnyClick(false)
                }) {
                    Text(state.speedText, color = Color.White)
                }
            }
            if (state.showQueue) {
                YpChromeButton(icons.queue, labels.queue, 35.dp) {
                    actions.onQueueClicked()
                    actions.onAnyClick(false)
                }
            }
            if (state.showSegments) {
                YpChromeButton(icons.segments, labels.chapters, 35.dp) {
                    actions.onSegmentsClicked()
                    actions.onAnyClick(false)
                }
            }
            if (!state.popupMode) {
                YpChromeButton(
                    icons.moreOptions,
                    labels.moreOptions,
                    40.dp,
                    onClick = {
                        actions.onMoreOptions()
                        actions.onAnyClick(true)
                    },
                    onLongClick = actions.onMoreOptionsLong,
                    rotation = if (state.secondaryVisible) 180f else 0f
                )
            }
        }
        // secondary controls
        if (state.popupMode || state.secondaryVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(48.dp)
            ) {
                if (state.showResize) {
                    TextButton(onClick = {
                        actions.onResize()
                        actions.onAnyClick(false)
                    }) {
                        Text(state.resizeText, color = Color.White)
                    }
                }
                if (state.showCaption) {
                    YpTextMenuButton(
                        text = state.captionText,
                        options = listOf(labels.captionNone) + state.captionOptions,
                        onSelected = { actions.onCaptionSelected(it - 1) },
                        onAnyClick = actions.onAnyClick
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.showFullScreen) {
                    YpChromeButton(icons.fullScreen, labels.toggleFullscreen, 40.dp) {
                        actions.onFullScreen()
                        actions.onAnyClick(false)
                    }
                }
                if (state.showKodi) {
                    YpChromeButton(icons.kodi, labels.kodi, 40.dp) {
                        actions.onKodi()
                        actions.onAnyClick(false)
                    }
                }
                if (state.showOpenInBrowser) {
                    YpChromeButton(icons.browser, labels.openInBrowser, 40.dp) {
                        actions.onOpenInBrowser()
                        actions.onAnyClick(false)
                    }
                }
                if (state.showShare) {
                    YpChromeButton(
                        icons.share,
                        labels.share,
                        40.dp,
                        onClick = {
                            actions.onShare()
                            actions.onAnyClick(false)
                        },
                        onLongClick = actions.onShareLong
                    )
                }
                if (state.showMute) {
                    YpChromeButton(
                        if (state.muted) icons.volumeOff else icons.volumeUp,
                        if (state.muted) labels.unmute else labels.mute,
                        40.dp
                    ) {
                        actions.onMute()
                        actions.onAnyClick(false)
                    }
                }
            }
        }
    }
}

@Composable
private fun YpTextMenuButton(
    text: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    onAnyClick: (Boolean) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = {
            open = true
            onAnyClick(true)
        }) {
            Text(text, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = {
            open = false
            onAnyClick(false)
        }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        open = false
                        onSelected(index)
                        onAnyClick(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun YpCenterControls(
    state: YpChromeState,
    labels: YpChromeLabels,
    icons: YpChromeIcons,
    actions: YpChromeActions
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.weight(1f))
        YpChromeButton(
            icons.previous,
            labels.previousStream,
            40.dp,
            onClick = {
                actions.onPrevious()
                actions.onAnyClick(false)
            },
            alpha = if (state.showPrevious) 1f else 0f
        )
        Spacer(Modifier.weight(0.5f))
        YpChromeButton(
            when (state.playAction) {
                YpPlayAction.PLAY -> icons.play
                YpPlayAction.PAUSE -> icons.pause
                YpPlayAction.REPLAY -> icons.replay
            },
            when (state.playAction) {
                YpPlayAction.PLAY -> labels.play
                YpPlayAction.PAUSE -> labels.pause
                YpPlayAction.REPLAY -> labels.replay
            },
            60.dp,
            onClick = {
                actions.onPlayPause()
                actions.onAnyClick(true)
            }
        )
        Spacer(Modifier.weight(0.5f))
        YpChromeButton(
            icons.next,
            labels.nextStream,
            40.dp,
            onClick = {
                actions.onNext()
                actions.onAnyClick(false)
            },
            alpha = if (state.showNext) 1f else 0f
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun YpBottomControls(
    state: YpChromeState,
    labels: YpChromeLabels,
    icons: YpChromeIcons,
    actions: YpChromeActions
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val fraction = if (dragFraction >= 0f) {
        dragFraction
    } else if (state.durationMillis > 0) {
        (state.progressMillis.toFloat() / state.durationMillis).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferFraction = if (state.durationMillis > 0) {
        state.bufferPercent / 100f
    } else {
        0f
    }
    val currentSeek by rememberUpdatedState(actions.onSeekPreview)
    val currentSeekEnd by rememberUpdatedState(actions.onSeekEnd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state.fullscreen) Modifier.systemBarsPadding() else Modifier)
            .padding(horizontal = 12.dp)
            .height(40.dp)
    ) {
        Text(
            text = if (dragFraction >= 0f) {
                state.seekDisplayText ?: state.currentTimeText
            } else {
                state.currentTimeText
            },
            color = Color.White,
            fontSize = 13.sp
        )
        Spacer(Modifier.width(8.dp))
        YpPlayerSeekBar(
            fraction = fraction,
            bufferFraction = bufferFraction,
            chapterFractions = state.chapterFractions,
            enabled = state.seekEnabled,
            onSeekStart = {
                actions.onSeekStart()
            },
            onSeek = { f ->
                dragFraction = f
                currentSeek(f, (f * state.durationMillis).roundToInt())
            },
            onSeekEnd = { f ->
                dragFraction = -1f
                currentSeekEnd((f * state.durationMillis).roundToInt())
            },
            onSeekCancel = {
                dragFraction = -1f
                actions.onSeekCancel()
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        if (state.isLive) {
            Text(
                text = labels.live.uppercase(),
                color = if (state.liveSyncEnabled) AccentRed else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = state.liveSyncEnabled) {
                    actions.onLiveSync()
                    actions.onAnyClick(false)
                }
            )
        } else {
            Text(text = state.endTimeText, color = Color.White, fontSize = 13.sp)
        }
        if (state.screenRotationVisible) {
            Spacer(Modifier.width(4.dp))
            YpChromeButton(
                if (state.screenRotationIsExit) icons.fullScreenExit else icons.fullScreen,
                labels.toggleOrientation,
                40.dp
            ) {
                actions.onScreenRotation()
                actions.onAnyClick(true)
            }
        }
    }
}

@SuppressWarnings("LongParameterList")
@Composable
private fun YpPlayerSeekBar(
    fraction: Float,
    bufferFraction: Float,
    chapterFractions: List<Float>,
    enabled: Boolean,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    onSeekCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)
    val currentOnSeekCancel by rememberUpdatedState(onSeekCancel)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val thumbRadius = with(density) { 6.dp.toPx() }
    val heightPx = with(density) { 4.dp.toPx() }

    Box(
        modifier
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { position: Offset ->
                    onSeekStart()
                    val f = (position.x / widthPx).coerceIn(0f, 1f)
                    currentOnSeek(f)
                    currentOnSeekEnd(f)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        onSeekStart()
                    },
                    onDragEnd = {
                        currentOnSeekEnd(fraction)
                    },
                    onDragCancel = {
                        currentOnSeekCancel()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        currentOnSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // track
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x40FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bufferFraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Color(0x60FFFFFF))
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(AccentRed)
            )
        }
        // chapter markers
        chapterFractions.forEach { chapterFraction ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            ((widthPx - heightPx) * chapterFraction.coerceIn(0f, 1f))
                                .roundToInt(),
                            0
                        )
                    }
                    .size(4.dp)
                    .background(Color.White)
            )
        }
        // thumb
        Box(
            Modifier
                .offset {
                    IntOffset(
                        ((widthPx - thumbRadius * 2) * fraction.coerceIn(0f, 1f)).roundToInt(),
                        0
                    )
                }
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AccentRed)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.YpSeekPreview(state: YpChromeState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 56.dp)
    ) {
        if (state.chapterTitle != null) {
            Text(
                text = state.chapterTitle,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BubbleColor)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Text(
            text = state.seekDisplayText ?: "",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BubbleColor)
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
        state.seekPreview?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(state.seekPreviewWidthFraction)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun YpItemsPanel(
    state: YpChromeState,
    labels: YpChromeLabels,
    icons: YpChromeIcons,
    actions: YpChromeActions
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(PanelColor)
    ) {
        // header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp)
        ) {
            if (state.panelMode == YpPanelMode.SEGMENTS) {
                Text(
                    text = labels.chapters,
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                YpChromeButton(
                    when {
                        state.repeatOne -> icons.repeatOne
                        !state.repeatOff -> icons.repeatAll
                        else -> icons.repeatOff
                    },
                    labels.repeat,
                    40.dp,
                    onClick = actions.onRepeat,
                    alpha = 1f
                )
                YpChromeButton(
                    icons.shuffle,
                    labels.shuffle,
                    40.dp,
                    onClick = actions.onShuffle,
                    alpha = if (state.shuffled) 1f else 0.3f
                )
                Text(
                    text = state.queueTimeText,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                YpChromeButton(icons.playlistAdd, labels.addToPlaylist, 40.dp) {
                    actions.onAddToPlaylist()
                }
            }
            YpChromeButton(icons.close, labels.close, 40.dp) {
                actions.onPanelClose()
            }
        }
        // content
        if (state.panelMode == YpPanelMode.SEGMENTS) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.segments, key = { _, s -> "segment:" + s.startSeconds }) {
                        _,
                        segment
                    ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (segment.selected) Color(0x33FFFFFF) else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = { actions.onSegmentClick(segment.startSeconds) },
                                onLongClick = { actions.onSegmentLongClick(segment.startSeconds) }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = segment.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            YpPanelQueueList(state, labels, icons, actions)
        }
    }
}

@Composable
private fun YpPanelQueueList(
    state: YpChromeState,
    labels: YpChromeLabels,
    icons: YpChromeIcons,
    actions: YpChromeActions
) {
    val listState = rememberLazyListState()
    val items = rememberUpdatedState(state.queueItems)
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.queueIndex) {
        if (state.queueIndex >= 0 && state.queueIndex < items.value.size) {
            listState.scrollToItem(state.queueIndex)
        }
    }
    LaunchedEffect(listState, state.queueComplete) {
        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.collect { last ->
            if (last != null && last >= items.value.size - 1 && !state.queueComplete) {
                actions.onQueueEndReached()
            }
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items.value, key = { _, entry -> "panel:" + entry.key }) { index, entry ->
            var menuOpen by remember(entry.key) { mutableStateOf(false) }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index == state.queueIndex) Color(0x33FFFFFF) else Color.Transparent
                        )
                        .combinedClickable(
                            onClick = { actions.onQueueSelect(index) },
                            onLongClick = { menuOpen = true }
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    AsyncImage(
                        model = entry.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(64.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF222222))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = entry.details,
                            color = Color(0xB3FFFFFF),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.durationText != null) {
                        Text(
                            text = entry.durationText,
                            color = Color(0xB3FFFFFF),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        painter = icons.drag,
                        contentDescription = null,
                        tint = Color(0xB3FFFFFF),
                        modifier = Modifier
                            .size(28.dp)
                            .pointerInput(entry.key) {
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
                                        dragOffset = ypMoveDragged(
                                            listState,
                                            "panel:",
                                            items.value.map { it.key },
                                            entry.key,
                                            dragOffset + amount.y,
                                            actions.onQueueMove
                                        )
                                    }
                                )
                            }
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(labels.streamDetails) }, onClick = {
                        menuOpen = false
                        actions.onQueueItemAction(index, YpQueueItemAction.DETAILS)
                    })
                    DropdownMenuItem(text = { Text(labels.addToPlaylist) }, onClick = {
                        menuOpen = false
                        actions.onQueueItemAction(index, YpQueueItemAction.APPEND_PLAYLIST)
                    })
                    DropdownMenuItem(text = { Text(labels.channelDetails) }, onClick = {
                        menuOpen = false
                        actions.onQueueItemAction(index, YpQueueItemAction.CHANNEL_DETAILS)
                    })
                    DropdownMenuItem(text = { Text(labels.share) }, onClick = {
                        menuOpen = false
                        actions.onQueueItemAction(index, YpQueueItemAction.SHARE)
                    })
                    DropdownMenuItem(text = { Text(labels.download) }, onClick = {
                        menuOpen = false
                        actions.onQueueItemAction(index, YpQueueItemAction.DOWNLOAD)
                    })
                    DropdownMenuItem(text = { Text(labels.remove) }, onClick = {
                        menuOpen = false
                        actions.onQueueItemAction(index, YpQueueItemAction.REMOVE)
                    })
                }
            }
        }
    }
}
