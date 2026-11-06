/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable

/**
 * Standard transition presets of the YewPlay Motion Design System.
 *
 * Every preset reads [LocalReduceMotion] and degrades to an instant change when
 * the user disabled animations at the system level. Use these instead of raw
 * `fadeIn()`/`slideInVertically()` calls so motion stays consistent.
 */
object MotionTransitions {

    /** Simple crossfade for small state swaps (icons, labels, selection). */
    @Composable
    fun fade(durationMillis: Int = MotionDurations.FAST): Pair<EnterTransition, ExitTransition> {
        val reduce = LocalReduceMotion.current
        return fadeIn(motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)) to
            fadeOut(motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate))
    }

    /** Content rising into place (lists appearing, secondary content, toasts). */
    @Composable
    fun slideUp(durationMillis: Int = MotionDurations.NORMAL): Pair<EnterTransition, ExitTransition> {
        val reduce = LocalReduceMotion.current
        return slideInVertically(
            motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)
        ) { it / 8 } + fadeIn(motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)) to
            slideOutVertically(
                motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate)
            ) { it / 8 } + fadeOut(motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate))
    }

    /** Surface sliding up from the bottom edge (player chrome, toolbars). */
    @Composable
    fun slideInFromBottom(
        durationMillis: Int = MotionDurations.NORMAL
    ): Pair<EnterTransition, ExitTransition> {
        val reduce = LocalReduceMotion.current
        return slideInVertically(
            motionSpec(reduce, durationMillis, MotionEasings.EmphasizedDecelerate)
        ) { it } + fadeIn(motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)) to
            slideOutVertically(
                motionSpec(reduce, durationMillis, MotionEasings.EmphasizedAccelerate)
            ) { it } + fadeOut(motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate))
    }

    /** Expanding content (sections, descriptions, accordions). */
    @Composable
    fun expand(durationMillis: Int = MotionDurations.NORMAL): Pair<EnterTransition, ExitTransition> {
        val reduce = LocalReduceMotion.current
        return expandVertically(
            motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)
        ) + fadeIn(motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)) to
            shrinkVertically(
                motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate)
            ) + fadeOut(motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate))
    }

    /** Confirmation pop (favorite toggles, success feedback). Kept subtle. */
    @Composable
    fun pop(durationMillis: Int = MotionDurations.FAST): Pair<EnterTransition, ExitTransition> {
        val reduce = LocalReduceMotion.current
        return scaleIn(
            motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate),
            initialScale = 0.8f
        ) + fadeIn(motionSpec(reduce, durationMillis, MotionEasings.StandardDecelerate)) to
            scaleOut(
                motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate),
                targetScale = 0.8f
            ) + fadeOut(motionSpec(reduce, durationMillis, MotionEasings.StandardAccelerate))
    }
}
