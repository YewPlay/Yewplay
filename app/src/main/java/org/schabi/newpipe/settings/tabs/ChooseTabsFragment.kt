/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTheme
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper

/**
 * YewPlay: the tabs of the home, in Compose: each tab with buttons to move it up or down and to
 * remove it, "Add tab" (with the pickers of kiosk, channel, playlist and group) and "Restore
 * defaults". The changes are saved when the screen is left.
 */
class ChooseTabsFragment : Fragment() {
    private lateinit var tabsManager: TabsManager
    private val tabs = mutableStateListOf<Tab>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabsManager = TabsManager.getManager(requireContext())
        tabs.addAll(tabsManager.tabs)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) { TabsScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.main_page_content))
    }

    override fun onPause() {
        super.onPause()
        tabsManager.saveTabs(tabs.toList())
    }

    @Composable
    private fun TabsScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                text = stringResource(R.string.yp_tabs_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(tabs) { index, tab ->
                    TabRow(index, tab)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = YpScreenMargin + 40.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YpScreenMargin, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                YpPillButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.yp_tabs_add),
                    primary = true,
                    onClick = ::showAddTab
                )
                YpPillButton(
                    label = stringResource(R.string.restore_defaults),
                    primary = false,
                    onClick = ::confirmRestoreDefaults
                )
            }
        }
    }

    @Composable
    private fun TabRow(index: Int, tab: Tab) {
        val context = requireContext()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = YpScreenMargin, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(tab.getTabIconRes(context)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                text = tabName(context, tab),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(enabled = index > 0, onClick = { move(index, index - 1) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_up),
                    contentDescription = stringResource(R.string.yp_tabs_move_up)
                )
            }
            IconButton(enabled = index < tabs.lastIndex, onClick = { move(index, index + 1) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = stringResource(R.string.yp_tabs_move_down)
                )
            }
            IconButton(onClick = { remove(index) }) {
                Icon(painter = painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.delete))
            }
        }
    }

    private fun move(from: Int, to: Int) {
        if (to in tabs.indices) {
            tabs.add(to, tabs.removeAt(from))
        }
    }

    private fun remove(index: Int) {
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            // the home always keeps a tab
            tabs.add(Tab.Type.BLANK.tab)
        }
    }

    private fun confirmRestoreDefaults() {
        YpDialogs.card(requireContext()) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.restore_defaults),
                message = stringResource(R.string.restore_defaults_confirmation),
                confirmLabel = stringResource(R.string.ok),
                onConfirm = {
                    tabsManager.resetTabs()
                    tabs.clear()
                    tabs.addAll(tabsManager.tabs)
                    dismiss()
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            )
        }.show()
    }

    private fun showAddTab() {
        val available = availableTabs(requireContext())
        if (available.isEmpty()) {
            return
        }
        AddTabDialog(requireContext(), available) { _, which -> addTab(available[which].tabId) }.show()
    }

    private fun addTab(tabId: Int) {
        val context = requireContext()
        when (Tab.typeFrom(tabId)) {
            null -> ErrorUtil.showSnackbar(
                this,
                ErrorInfo(
                    IllegalStateException("Tab id not found: $tabId"),
                    UserAction.SOMETHING_ELSE,
                    "Choosing tabs on settings"
                )
            )

            Tab.Type.KIOSK -> TabPickers.pickKiosk(context) { tabs.add(it) }

            Tab.Type.CHANNEL -> TabPickers.pickChannel(context) { tabs.add(it) }

            Tab.Type.PLAYLIST -> TabPickers.pickPlaylist(context) { tabs.add(it) }

            Tab.Type.FEEDGROUP -> TabPickers.pickFeedGroup(context) { tabs.add(it) }

            else -> tabs.add(Tab.typeFrom(tabId)!!.tab)
        }
    }

    private fun availableTabs(context: Context): Array<ChooseTabListItem> = Tab.Type.values().mapNotNull { type ->
        val tab = type.tab
        when (type) {
            Tab.Type.BLANK -> if (tab in tabs) {
                null
            } else {
                ChooseTabListItem(tab.tabId, getString(R.string.blank_page_summary), tab.getTabIconRes(context))
            }

            Tab.Type.KIOSK ->
                ChooseTabListItem(tab.tabId, getString(R.string.kiosk_page_summary), R.drawable.ic_whatshot)

            Tab.Type.CHANNEL ->
                ChooseTabListItem(tab.tabId, getString(R.string.channel_page_summary), tab.getTabIconRes(context))

            Tab.Type.DEFAULT_KIOSK -> if (tab in tabs) {
                null
            } else {
                ChooseTabListItem(tab.tabId, getString(R.string.default_kiosk_page_summary), R.drawable.ic_whatshot)
            }

            Tab.Type.PLAYLIST ->
                ChooseTabListItem(tab.tabId, getString(R.string.playlist_page_summary), tab.getTabIconRes(context))

            Tab.Type.FEEDGROUP ->
                ChooseTabListItem(tab.tabId, getString(R.string.feed_group_page_summary), tab.getTabIconRes(context))

            else -> if (tab in tabs) null else ChooseTabListItem(context, tab)
        }
    }.toTypedArray()

    private fun tabName(context: Context, tab: Tab): String = when (Tab.typeFrom(tab.tabId)) {
        Tab.Type.BLANK -> getString(R.string.blank_page_summary)

        Tab.Type.DEFAULT_KIOSK -> getString(R.string.default_kiosk_page_summary)

        Tab.Type.KIOSK ->
            ServiceHelper.getNameOfServiceById((tab as Tab.KioskTab).kioskServiceId) + "/" + tab.getTabName(context)

        Tab.Type.CHANNEL ->
            ServiceHelper.getNameOfServiceById((tab as Tab.ChannelTab).channelServiceId) + "/" + tab.getTabName(context)

        Tab.Type.PLAYLIST -> {
            val serviceId = (tab as Tab.PlaylistTab).playlistServiceId
            val service = if (serviceId == -1) getString(R.string.local) else ServiceHelper.getNameOfServiceById(serviceId)
            service + "/" + tab.getTabName(context)
        }

        Tab.Type.FEEDGROUP -> getString(R.string.feed_groups_header_title) + "/" + (tab as Tab.FeedGroupTab).feedGroupName

        else -> tab.getTabName(context)
    }
}
