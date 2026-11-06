/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import net.newpipe.app.theme.LocalReduceMotion
import net.newpipe.app.theme.MotionDurations
import net.newpipe.app.theme.MotionEasings
import net.newpipe.app.theme.MotionTransitions
import net.newpipe.app.theme.motionSpec

// YewPlay Motion Design System — reusable modifiers.
// Values and curves live in net.newpipe.app.theme.MotionDurations / MotionEasings;
// enter/exit presets in MotionTransitions. Everything here honors LocalReduceMotion.

/**
 * Subtle press feedback (scale down while pressed) for clickable surfaces
 * that intentionally have no ripple, e.g. the bottom bar. Uses [graphicsLayer]
 * so pressing never triggers recomposition.
 */
@Composable
fun Modifier.ypPressFeedback(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = motionSpec(reduce, MotionDurations.EXTRA_FAST, MotionEasings.Standard),
        label = "ypPressFeedback"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Standard list-item motion (add/remove/reorder) for LazyColumn items.
 * Cheap: only fade + placement, no layout measurement tricks. Under reduce
 * motion, items snap into place without movement.
 *
 * Do not apply to very large feeds where items churn constantly; prefer it on
 * user-managed lists (playlists, queue, subscriptions management).
 */
@Composable
fun Modifier.ypAnimateItem(scope: LazyItemScope): Modifier {
    val reduce = LocalReduceMotion.current
    return with(scope) {
        animateItem(
            fadeInSpec = if (reduce) {
                snap()
            } else {
                tween(MotionDurations.FAST, easing = MotionEasings.StandardDecelerate)
            },
            fadeOutSpec = if (reduce) {
                snap()
            } else {
                tween(MotionDurations.FAST, easing = MotionEasings.StandardAccelerate)
            },
            placementSpec = if (reduce) {
                snap()
            } else {
                tween(MotionDurations.NORMAL, easing = MotionEasings.Standard)
            }
        )
    }
}

/** Same as [ypAnimateItem] for LazyVerticalGrid items. */
@Composable
fun Modifier.ypAnimateGridItem(scope: LazyGridItemScope): Modifier {
    val reduce = LocalReduceMotion.current
    return with(scope) {
        animateItem(
            fadeInSpec = if (reduce) {
                snap()
            } else {
                tween(MotionDurations.FAST, easing = MotionEasings.StandardDecelerate)
            },
            fadeOutSpec = if (reduce) {
                snap()
            } else {
                tween(MotionDurations.FAST, easing = MotionEasings.StandardAccelerate)
            },
            placementSpec = if (reduce) {
                snap()
            } else {
                tween(MotionDurations.NORMAL, easing = MotionEasings.Standard)
            }
        )
    }
}

/**
 * Subtle skeleton shimmer: the block pulses gently while content loads.
 * Continuous by nature, but reads [LocalReduceMotion] and freezes at full
 * opacity when the user reduced motion. Uses [graphicsLayer] (no recomposition
 * during the loop) and stops automatically once the skeleton leaves composition.
 */
@Composable
fun Modifier.ypShimmer(): Modifier {
    val reduce = LocalReduceMotion.current
    if (reduce) return this
    val transition = rememberInfiniteTransition(label = "ypShimmer")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDurations.LONG * 2, easing = MotionEasings.Standard),
            repeatMode = RepeatMode.REVERSE
        ),
        label = "ypShimmerAlpha"
    )
    return graphicsLayer { this.alpha = alpha }
}
