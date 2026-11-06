/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * YewPlay: a slider and a switch drawn with foundation only, in the colors of the theme (ruby
 * track, ivory/ink thumb). The material3 ones are not used: the material3 version of the app does
 * not run with its foundation version.
 */

/**
 * A horizontal slider for [value] in [valueRange]. With [steps] > 0 the value snaps to
 * [steps] + 2 evenly spaced positions (both ends included).
 */
@Composable
fun YpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val onChange by rememberUpdatedState(onValueChange)
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val trackWidth = maxWidth
        val widthPx = with(LocalDensity.current) { trackWidth.toPx() }.coerceAtLeast(1f)
        val thumb = 20.dp
        fun valueAt(x: Float): Float {
            var f = (x / widthPx).coerceIn(0f, 1f)
            if (steps > 0) {
                val intervals = steps + 1
                f = (f * intervals).roundToInt() / intervals.toFloat()
            }
            return valueRange.start + f * span
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(widthPx, steps, valueRange) {
                    detectTapGestures { onChange(valueAt(it.x)) }
                }
                .pointerInput(widthPx, steps, valueRange) {
                    detectHorizontalDragGestures { change, _ -> onChange(valueAt(change.position.x)) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
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
                    .offset(x = (trackWidth - thumb) * fraction)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(colors.onSurface)
            )
        }
    }
}

/** An on/off switch: a pill with a round thumb that moves to the end when [checked]. */
@Composable
fun YpSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switch")
    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(if (checked) colors.primary else colors.surfaceContainerHighest)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onPrimary else colors.onSurfaceVariant)
        )
    }
}
