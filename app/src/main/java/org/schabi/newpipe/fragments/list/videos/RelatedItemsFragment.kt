/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.videos

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpChannelRow
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpPlaylistRow
import io.github.yewplay.core.designsystem.YpSubscribeLabels
import io.github.yewplay.core.designsystem.YpSwitchPreferenceRow
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.core.designsystem.YpVideoRow
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the videos related to the one playing (a tab of the video screen), in Compose:
 * "Autoplay" on top, then the videos, channels and playlists.
 */
class RelatedItemsFragment : BaseFragment() {
    private lateinit var related: List<InfoItem>
    private lateinit var prefs: SharedPreferences
    private var autoQueue by mutableStateOf(false)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == getString(R.string.auto_queue_key)) {
            autoQueue = prefs.getBoolean(key, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("UNCHECKED_CAST")
        related = (BundleCompat.getSerializable(requireArguments(), KEY_ITEMS, ArrayList::class.java) as? List<InfoItem>)
            .orEmpty()
            .filter { YpItems.isSupported(it) }
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        autoQueue = prefs.getBoolean(getString(R.string.auto_queue_key), false)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) {
                    val items = remember(related) { related.map { it to YpItems.toUi(context, it) } }
                    val more = painterResource(R.drawable.ic_more_vert)
                    val subscribeLabels = YpSubscribeLabels(
                        subscribe = stringResource(R.string.subscribe_button_title),
                        subscribed = stringResource(R.string.subscribed_button_title)
                    )
                    LazyColumn(
                        // the list scrolls inside the video screen, whose header collapses with it
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(rememberNestedScrollInteropConnection()),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            YpSwitchPreferenceRow(
                                title = stringResource(R.string.yp_autoplay_next),
                                summary = stringResource(R.string.yp_autoplay_next_summary),
                                checked = autoQueue,
                                onCheckedChange = { value -> prefs.edit { putBoolean(getString(R.string.auto_queue_key), value) } }
                            )
                        }
                        if (items.isEmpty()) {
                            item { YpMessageState(title = stringResource(R.string.no_videos), detail = null) }
                        }
                        items(items, key = { (item, _) -> item.serviceId.toString() + item.url }) { (item, ui) ->
                            RelatedRow(item, ui, more, subscribeLabels)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RelatedRow(item: InfoItem, ui: YpListItem, more: Painter, labels: YpSubscribeLabels) {
        when (ui) {
            is YpStreamItem -> YpVideoRow(
                item = ui,
                onClick = { open(item) },
                onLongClick = { showMenu(item) },
                moreIcon = more
            )

            is YpChannelItem -> YpChannelRow(
                item = ui,
                subscribed = null,
                labels = labels,
                onClick = { open(item) },
                onToggleSubscription = {}
            )

            is YpPlaylistItem -> YpPlaylistRow(item = ui, onClick = { open(item) })
        }
    }

    private fun open(item: InfoItem) {
        when (item.infoType) {
            InfoItem.InfoType.STREAM -> NavigationHelper.openVideoDetailFragment(
                requireContext(),
                fm,
                item.serviceId,
                item.url,
                item.name,
                null,
                false
            )

            InfoItem.InfoType.CHANNEL -> NavigationHelper.openChannelFragment(fm, item.serviceId, item.url, item.name)

            InfoItem.InfoType.PLAYLIST -> NavigationHelper.openPlaylistFragment(fm, item.serviceId, item.url, item.name)

            else -> Unit
        }
    }

    /** The actions of a video; the sheet belongs to the video screen, which outlives this tab. */
    private fun showMenu(item: InfoItem) {
        val stream = item as? StreamInfoItem ?: return
        val owner = parentFragment ?: this
        try {
            InfoItemDialog.Builder(owner.activity, owner.context, owner, stream).create().show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, stream)
        }
    }

    companion object {
        private const val KEY_ITEMS = "related_items"

        @JvmStatic
        fun getInstance(info: StreamInfo): RelatedItemsFragment = RelatedItemsFragment().apply {
            arguments = bundleOf(KEY_ITEMS to ArrayList(info.relatedItems.orEmpty()))
        }
    }
}
