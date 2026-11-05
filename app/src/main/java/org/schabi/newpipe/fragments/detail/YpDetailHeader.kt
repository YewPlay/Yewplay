/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSubscribeButton
import io.github.yewplay.core.designsystem.YpSubscribeLabels

/**
 * YewPlay: state of the video detail header (title, channel, counts, actions), drawn in Compose.
 * The fragment updates a copy of this state as the legacy flow (loading, result, player events)
 * progresses; the header itself is stateless.
 */
data class YpDetailHeaderState(
    val title: String = "",
    val titleExpanded: Boolean = false,
    val contentVisible: Boolean = false,
    // uploader row
    val subChannelName: String = "",
    val subChannelAvatarUrl: String? = null,
    val uploaderName: String = "",
    val uploaderAvatarUrl: String? = null,
    val byLine: String = "",
    val showSubChannelAvatar: Boolean = false,
    val showUploaderAvatar: Boolean = false,
    // subscribe
    val subscribeVisible: Boolean = false,
    val subscribed: Boolean = false,
    // counts
    val viewCountText: String? = null,
    val likeCountText: String? = null,
    val dislikeCountText: String? = null,
    val ratingsDisabled: Boolean = false,
    // secondary controls
    val secondaryVisible: Boolean = false,
    val showKodi: Boolean = false,
    val showCrashThePlayer: Boolean = false
)

/** Labels of the detail header, resolved from the app's strings. */
data class YpDetailHeaderLabels(
    val subscribe: String,
    val subscribed: String,
    val download: String,
    val background: String,
    val append: String,
    val popup: String,
    val share: String,
    val openInBrowser: String,
    val playWithKodi: String,
    val crashThePlayer: String,
    val ratingsDisabled: String,
    val showMore: String
)

/** Icons of the detail header, resolved from the app's drawables. */
data class YpDetailHeaderIcons(
    val chevronDown: Painter,
    val download: Painter,
    val headphones: Painter,
    val playlistAdd: Painter,
    val popup: Painter,
    val share: Painter,
    val openInBrowser: Painter,
    val kodi: Painter,
    val crash: Painter,
    val thumbUp: Painter,
    val thumbDown: Painter
)

/** Actions of the detail header, wired to the legacy flows of the fragment. */
data class YpDetailHeaderActions(
    val onTitleClick: () -> Unit,
    val onTitleLongClick: () -> Unit,
    val onUploaderClick: () -> Unit,
    val onUploaderLongClick: () -> Unit,
    val onSubscribe: () -> Unit,
    val onLike: () -> Unit,
    val onDownload: () -> Unit,
    val onDownloadLongClick: () -> Unit,
    val onBackground: () -> Unit,
    val onBackgroundLongClick: () -> Unit,
    val onAppend: () -> Unit,
    val onPopup: () -> Unit,
    val onPopupLongClick: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onPlayWithKodi: () -> Unit,
    val onCrashThePlayer: () -> Unit
)

/** A labeled action button of the detail page: icon over label, 44 dp high (proposal, p. 16). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YpDetailAction(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHighest)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier.size(19.dp),
            painter = icon,
            contentDescription = null,
            tint = colors.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * YewPlay: the video detail header in Compose (proposal, p. 16): title with chevron, channel row
 * with subscribe, counts, the five labeled actions and the secondary panel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpDetailHeader(
    state: YpDetailHeaderState,
    labels: YpDetailHeaderLabels,
    icons: YpDetailHeaderIcons,
    actions: YpDetailHeaderActions,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        // Title: one line; tap expands to ten lines and shows the secondary panel
        val chevronRotation by animateFloatAsState(
            targetValue = if (state.titleExpanded) 180f else 0f,
            label = "chevron"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = state.contentVisible,
                    onClick = actions.onTitleClick,
                    onLongClick = actions.onTitleLongClick
                )
                .padding(horizontal = YpScreenMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = if (state.titleExpanded) 10 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.contentVisible) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                    painter = icons.chevronDown,
                    contentDescription = labels.showMore,
                    tint = colors.onSurfaceVariant
                )
            }
        }

        if (state.contentVisible) {
            // Channel row: avatars, names, subscribe (proposal: "Inscrever-se em marfim")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = actions.onUploaderClick,
                        onLongClick = actions.onUploaderLongClick
                    )
                    .padding(horizontal = YpScreenMargin, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.showSubChannelAvatar) {
                    YpAvatar(url = state.subChannelAvatarUrl, size = 40)
                }
                if (state.showUploaderAvatar) {
                    if (state.showSubChannelAvatar) {
                        Spacer(Modifier.width(4.dp))
                    }
                    YpAvatar(url = state.uploaderAvatarUrl, size = 40)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val channelName = state.subChannelName.ifEmpty { state.uploaderName }
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.byLine.isNotEmpty()) {
                        Text(
                            text = state.byLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (state.subscribeVisible) {
                    Spacer(Modifier.width(10.dp))
                    YpSubscribeButton(
                        subscribed = state.subscribed,
                        labels = YpSubscribeLabels(labels.subscribe, labels.subscribed),
                        onClick = actions.onSubscribe
                    )
                }
            }

            // Counts: views on the left, thumbs on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YpScreenMargin, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.viewCountText != null) {
                    Text(
                        text = state.viewCountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.ratingsDisabled) {
                    Text(
                        text = labels.ratingsDisabled,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = actions.onLike)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = icons.thumbUp,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                        if (state.likeCountText != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = state.likeCountText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (state.dislikeCountText != null) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = icons.thumbDown,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = state.dislikeCountText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // The five labeled actions (proposal, p. 16)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YpScreenMargin, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YpDetailAction(
                    icon = icons.download,
                    label = labels.download,
                    onClick = actions.onDownload,
                    onLongClick = actions.onDownloadLongClick,
                    modifier = Modifier.weight(1f)
                )
                YpDetailAction(
                    icon = icons.headphones,
                    label = labels.background,
                    onClick = actions.onBackground,
                    onLongClick = actions.onBackgroundLongClick,
                    modifier = Modifier.weight(1f)
                )
                YpDetailAction(
                    icon = icons.playlistAdd,
                    label = labels.append,
                    onClick = actions.onAppend,
                    modifier = Modifier.weight(1f)
                )
                YpDetailAction(
                    icon = icons.popup,
                    label = labels.popup,
                    onClick = actions.onPopup,
                    onLongClick = actions.onPopupLongClick,
                    modifier = Modifier.weight(1f)
                )
            }

            // Secondary panel: share, open in browser, Kodi (and the debug crasher)
            AnimatedVisibility(visible = state.secondaryVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = YpScreenMargin, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    YpDetailAction(
                        icon = icons.share,
                        label = labels.share,
                        onClick = actions.onShare,
                        modifier = Modifier.weight(1f)
                    )
                    YpDetailAction(
                        icon = icons.openInBrowser,
                        label = labels.openInBrowser,
                        onClick = actions.onOpenInBrowser,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.showKodi) {
                        YpDetailAction(
                            icon = icons.kodi,
                            label = labels.playWithKodi,
                            onClick = actions.onPlayWithKodi,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (state.showCrashThePlayer) {
                        YpDetailAction(
                            icon = icons.crash,
                            label = labels.crashThePlayer,
                            onClick = actions.onCrashThePlayer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

        }
    }
}
