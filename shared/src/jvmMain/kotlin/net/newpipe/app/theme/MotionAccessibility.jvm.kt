/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.runtime.Composable

/**
 * Desktop has no system-wide reduce-motion setting exposed here; always off.
 */
@Composable
actual fun rememberPlatformReduceMotion(): Boolean = false
