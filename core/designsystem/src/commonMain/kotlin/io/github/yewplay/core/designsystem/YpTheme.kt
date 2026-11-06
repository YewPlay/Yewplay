/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.theme.yewplayColorScheme

/**
 * YewPlay theme for screens hosted in the app's fragments: same colors, fonts and shapes as the
 * Compose screens of :shared, chosen from the theme preferences. Dialogs pass
 * [opaqueBackground] = false, since they draw their own surface over the screen.
 */
@Composable
fun YpTheme(
    themeKey: String,
    nightThemeKey: String,
    opaqueBackground: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = yewplayColorScheme(themeKey, nightThemeKey, isSystemInDarkTheme())
    AppTheme(colorScheme = colorScheme) {
        // screens hosted in fragments have no Surface, so give texts the theme's content color
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            // opaque: while a screen fades in (or its content loads), the previous one must not
            // show through it
            if (opaqueBackground) {
                Box(modifier = Modifier.background(colorScheme.background)) { content() }
            } else {
                content()
            }
        }
    }
}
