/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.model.YpStreamItem

private val FeaturedText = Color(0xFFF3EEE9)
private val FeaturedMuted = Color(0xCCF3EEE9)

/**
 * Editorial highlight, edge to edge, with the texts over the thumbnail (proposal, p. 13:
 * "Destaque editorial de ponta a ponta"). Texts stay white in both themes (p. 17).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpFeaturedVideo(
    item: YpStreamItem,
    label: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color(0xE618141A)
                    )
                )
        )
        if (item.isLive) {
            YpLiveBadge(
                text = item.durationText.orEmpty(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(YpScreenMargin)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = YpScreenMargin, vertical = 18.dp)
        ) {
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = FeaturedMuted)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = FeaturedText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = channelAndDetails(item),
                style = MaterialTheme.typography.bodySmall,
                color = FeaturedMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Large video item: edge-to-edge thumbnail, title in Bricolage 17 sp, channel in bold and the
 * details on the same line (proposal, p. 19).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpVideoCard(
    item: YpStreamItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    moreIcon: Painter? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
            val duration = item.durationText
            if (item.isLive) {
                YpLiveBadge(
                    text = duration.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            } else if (duration != null) {
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC18141A))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = FeaturedText
                )
            }
            if (item.progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(item.progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = YpScreenMargin, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = channelAndDetails(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (moreIcon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onLongClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = moreIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "Ao vivo" in ruby, with a dot (proposal, p. 19). */
@Composable
fun YpLiveBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onTertiary)
        )
        Spacer(Modifier.size(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiary)
    }
}

/** Channel in bold, then the details: "Tech Direto  112K views • 2 days ago". */
private fun channelAndDetails(item: YpStreamItem) = buildAnnotatedString {
    if (item.uploaderName.isNotEmpty()) {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(item.uploaderName) }
        if (item.details.isNotEmpty()) {
            append("  ")
        }
    }
    append(item.details)
}
