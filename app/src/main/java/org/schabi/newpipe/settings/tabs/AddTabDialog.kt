/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import androidx.annotation.DrawableRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetItem
import io.github.yewplay.core.designsystem.YpSheetTitle
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs

/** YewPlay: "Choose a tab" as a Compose bottom sheet, one row per tab with its icon. */
class AddTabDialog(
    context: Context,
    items: Array<ChooseTabListItem>,
    actions: DialogInterface.OnClickListener
) {
    private val dialog: Dialog = YpDialogs.sheet(context) { dismiss ->
        YpSheet {
            YpSheetTitle(stringResource(R.string.tab_choose))
            LazyColumn {
                itemsIndexed(items) { index, item ->
                    YpSheetItem(
                        label = item.itemName,
                        icon = painterResource(if (item.itemIcon > 0) item.itemIcon else R.drawable.ic_whatshot),
                        onClick = {
                            dismiss()
                            actions.onClick(dialog, index)
                        }
                    )
                }
            }
        }
    }

    fun show() {
        dialog.show()
    }

    class ChooseTabListItem(
        @JvmField val tabId: Int,
        @JvmField val itemName: String,
        @JvmField @param:DrawableRes val itemIcon: Int
    ) {
        constructor(context: Context, tab: Tab) : this(
            tab.tabId,
            tab.getTabName(context),
            tab.getTabIconRes(context)
        )
    }
}
