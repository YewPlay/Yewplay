/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/**
 * YewPlay Motion Design System — base tokens.
 *
 * Single source of truth for every animation duration and easing curve in the app.
 * UI layers (design system components, screens, View adapters) must reference these
 * tokens instead of hardcoded numbers so motion stays consistent and configurable.
 */
object MotionDurations {
    /** Microinteractions: icon swaps, press feedback, tiny state toggles. */
    const val EXTRA_FAST: Int = 100

    /** Simple fades, selection changes, small alpha/color transitions. */
    const val FAST: Int = 150

    /** Default UI transitions: visibility changes, expansions, tab switches. */
    const val NORMAL: Int = 250

    /** Screen-level transitions: navigation, dialogs, bottom sheets. */
    const val MEDIUM: Int = 350

    /** Large surface motion: player chrome reveal, big layout changes. */
    const val LONG: Int = 450
}

/**
 * Easing curves following the Material 3 motion spec.
 *
 * Entering elements decelerate (fast start, gentle landing); exiting elements
 * accelerate (quick departure). [Emphasized] is reserved for large, expressive
 * moves such as the mini player expansion.
 */
object MotionEasings {
    /** Expressive curve for large surface motion (mini player, big layout shifts). */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Expressive exit: quick start, used when large surfaces leave the screen. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Expressive entrance: gentle landing for large surfaces entering the screen. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Default curve for standard transitions. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Elements entering the screen (fast start, gentle stop). */
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Elements leaving the screen (immediate acceleration). */
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Constant speed; only for progress indicators and loaders. */
    val Linear: Easing = LinearEasing
}
