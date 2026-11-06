/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import android.provider.Settings
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import net.newpipe.app.theme.MotionDurations

/**
 * YewPlay Motion Design System — adapter for the View system.
 *
 * Mirrors the Compose tokens ([MotionDurations]) and the M3 cubic-bezier curves as
 * framework [Interpolator]s so legacy View-based screens share the same motion
 * language. Under the system "remove animations" accessibility setting, every
 * duration collapses to 0 (instant state change, no visible movement).
 */
object YpViewMotion {
    val EXTRA_FAST: Long = MotionDurations.EXTRA_FAST.toLong()
    val FAST: Long = MotionDurations.FAST.toLong()
    val NORMAL: Long = MotionDurations.NORMAL.toLong()
    val MEDIUM: Long = MotionDurations.MEDIUM.toLong()
    val LONG: Long = MotionDurations.LONG.toLong()

    /** M3 standard (0.2, 0, 0, 1) — transições padrão. */
    val standard: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** M3 standard decelerate (0, 0, 0, 1) — elementos entrando. */
    val standardDecelerate: Interpolator = PathInterpolator(0f, 0f, 0f, 1f)

    /** M3 standard accelerate (0.3, 0, 1, 1) — elementos saindo. */
    val standardAccelerate: Interpolator = PathInterpolator(0.3f, 0f, 1f, 1f)

    /** M3 emphasized (0.2, 0, 0, 1) — grandes superfícies (mini player). */
    val emphasized: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** True when the user disabled animations in the system settings. */
    fun isReduceMotionEnabled(context: Context): Boolean = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    /**
     * Duration honoring reduce-motion: returns 0 (instant) when the user asked
     * the system to remove animations, otherwise the token value unchanged.
     */
    fun duration(context: Context, baseMillis: Long): Long = if (isReduceMotionEnabled(context)) 0L else baseMillis
}
