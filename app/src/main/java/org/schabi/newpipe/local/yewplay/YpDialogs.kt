/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.yewplay.core.designsystem.YpTheme

/**
 * YewPlay: shows Compose content as an Android dialog, so that the dialogs keep working from
 * any screen (and from dialog fragments) while their whole look is drawn in Compose.
 */
object YpDialogs {

    /**
     * A bottom sheet (actions of an item, choosing a playlist, ...). [content] gets a function
     * that closes the sheet.
     */
    @JvmStatic
    fun sheet(context: Context, content: @Composable (dismiss: () -> Unit) -> Unit): Dialog {
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(composeView(context) { content(dialog::dismiss) })
        dialog.setOnShowListener {
            // the sheet surface is drawn in Compose (YpSheet): no material background behind it
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    /**
     * A dialog in the middle of the screen (confirmations, a name to type, ...). With
     * [showKeyboard] the keyboard opens with it, for dialogs that start on a text field.
     */
    @JvmStatic
    @JvmOverloads
    fun card(
        context: Context,
        showKeyboard: Boolean = false,
        content: @Composable (dismiss: () -> Unit) -> Unit
    ): Dialog {
        val dialog = AppCompatDialog(context)
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(
            composeView(context) {
                Box(Modifier.padding(horizontal = 24.dp)) {
                    content(dialog::dismiss)
                }
            }
        )
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    if (showKeyboard) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE else 0
            )
        }
        return dialog
    }

    private fun composeView(context: Context, content: @Composable () -> Unit): ComposeView {
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YpTheme(themeKey, nightThemeKey, opaqueBackground = false) { content() }
            }
        }
    }
}
