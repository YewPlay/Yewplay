/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS honors the system "Reduce Motion" accessibility setting.
 */
@Composable
actual fun rememberPlatformReduceMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
