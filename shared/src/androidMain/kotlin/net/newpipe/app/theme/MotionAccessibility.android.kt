/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android honors "Remove animations" (animator duration scale == 0).
 * Read once per composition scope; a system change applies on the next
 * composition (e.g. after the activity resumes and recomposes).
 */
@Composable
actual fun rememberPlatformReduceMotion(): Boolean {
    val contentResolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
