/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the user asked the system to reduce motion (accessibility).
 *
 * Provided once by `AppTheme`. When `true`, [motionSpec] resolves to an instant
 * [snap] so every animated state change still happens — just without visible
 * movement. Functionality must never depend on an animation playing.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Reads the platform "reduce motion" setting (accessibility).
 *
 * Android: animator duration scale == 0 ("Remove animations").
 * iOS: UIAccessibilityIsReduceMotionEnabled. Desktop: not supported (false).
 */
@Composable
expect fun rememberPlatformReduceMotion(): Boolean

/**
 * Central spec resolver: honors [LocalReduceMotion] by collapsing any motion
 * into an instant state change while keeping the state machine intact.
 *
 * @param reduceMotion pass `LocalReduceMotion.current` from composables, or the
 * platform flag from non-composable code.
 */
fun <T> motionSpec(
    reduceMotion: Boolean,
    durationMillis: Int = MotionDurations.NORMAL,
    easing: Easing = MotionEasings.Standard,
    delayMillis: Int = 0
): FiniteAnimationSpec<T> = if (reduceMotion) {
    snap(delayMillis)
} else {
    tween(durationMillis, delayMillis, easing)
}
