/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Guards the YewPlay Motion Design System contract: token values stay stable,
 * the accessibility resolver always collapses to instant changes under
 * reduce-motion, and easing curves stay within spec.
 */
class MotionTest {

    @Test
    fun durationTokensMatchSpec() {
        assertEquals(100, MotionDurations.EXTRA_FAST)
        assertEquals(150, MotionDurations.FAST)
        assertEquals(250, MotionDurations.NORMAL)
        assertEquals(350, MotionDurations.MEDIUM)
        assertEquals(450, MotionDurations.LONG)
    }

    @Test
    fun reduceMotionCollapsesToSnap() {
        val spec = motionSpec<Float>(reduceMotion = true)
        assertIs<SnapSpec<*>>(spec)
    }

    @Test
    fun normalMotionResolvesToTweenWithTokenValues() {
        val spec = motionSpec<Float>(
            reduceMotion = false,
            durationMillis = MotionDurations.NORMAL,
            easing = MotionEasings.Standard
        )
        assertIs<TweenSpec<Float>>(spec)
        assertEquals(MotionDurations.NORMAL, (spec as TweenSpec).durationMillis)
    }

    @Test
    fun reduceMotionKeepsDelayButSkipsMovement() {
        val spec = motionSpec<Float>(reduceMotion = true, delayMillis = 30)
        assertIs<SnapSpec<*>>(spec)
        assertEquals(30, (spec as SnapSpec).delay)
    }

    @Test
    fun easingCurvesRespectBounds() {
        val curves = listOf(
            MotionEasings.Emphasized,
            MotionEasings.EmphasizedAccelerate,
            MotionEasings.EmphasizedDecelerate,
            MotionEasings.Standard,
            MotionEasings.StandardDecelerate,
            MotionEasings.StandardAccelerate,
            MotionEasings.Linear
        )
        for (easing in curves) {
            assertEquals(0f, easing.transform(0f), 0.0001f)
            assertEquals(1f, easing.transform(1f), 0.0001f)
            var previous = -1f
            var t = 0f
            // decelerate/standard curves must be monotonic in [0,1]
            while (t <= 1f) {
                val v = easing.transform(t)
                assertTrue(v >= previous, "curve regressed at $t")
                previous = v
                t += 0.05f
            }
        }
    }
}
