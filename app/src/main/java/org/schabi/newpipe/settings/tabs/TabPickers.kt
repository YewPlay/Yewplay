/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetItem
import io.github.yewplay.core.designsystem.YpSheetTitle
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.reactive.asFlow
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper

/**
 * YewPlay: the sheets that pick what a new tab of the home shows: a kiosk, a channel, a
 * playlist or a group of channels. Each one hands the chosen [Tab] to `onPicked`.
 */
object TabPickers {

    private enum class Leading { ICON, AVATAR, COVER }

    private class Option(
        val key: String,
        val title: String,
        val tab: Tab,
        @param:DrawableRes val icon: Int = 0,
        val imageUrl: String? = null,
        val leading: Leading = Leading.ICON
    )

    fun pickKiosk(context: Context, onPicked: (Tab) -> Unit) {
        val options = try {
            NewPipe.getServices().flatMap { service ->
                service.kioskList.availableKiosks.map { kioskId ->
                    Option(
                        key = "${service.serviceId}/$kioskId",
                        title = context.getString(
                            R.string.service_kiosk_string,
                            service.serviceInfo.name,
                            KioskTranslator.getTranslatedKioskName(kioskId, context)
                        ),
                        tab = Tab.KioskTab(service.serviceId, kioskId),
                        icon = ServiceHelper.getIcon(service.serviceId)
                    )
                }
            }
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(context, "Selecting kiosk", e)
            return
        }
        show(context, R.string.select_a_kiosk, R.string.select_a_kiosk, Flowable.just(options), onPicked)
    }

    fun pickChannel(context: Context, onPicked: (Tab) -> Unit) {
        val options = SubscriptionManager(context).subscriptions().map { list ->
            list.map {
                Option(
                    key = it.uid.toString(),
                    title = it.name.orEmpty(),
                    tab = Tab.ChannelTab(it.serviceId, it.url, it.name),
                    imageUrl = it.avatarUrl,
                    leading = Leading.AVATAR
                )
            }
        }
        show(context, R.string.select_a_channel, R.string.no_channel_subscribed_yet, options, onPicked)
    }

    fun pickPlaylist(context: Context, onPicked: (Tab) -> Unit) {
        val database = NewPipeDatabase.getInstance(context)
        val options = MergedPlaylistManager.getMergedOrderedPlaylists(
            LocalPlaylistManager(database),
            RemotePlaylistManager(database)
        ).map { list ->
            list.mapNotNull { item ->
                when (item) {
                    is PlaylistMetadataEntry -> Option(
                        key = "l" + item.uid,
                        title = item.orderingName.orEmpty(),
                        tab = Tab.PlaylistTab(item.uid, item.orderingName),
                        imageUrl = item.thumbnailUrl,
                        leading = Leading.COVER
                    )

                    is PlaylistRemoteEntity -> Option(
                        key = "r" + item.uid,
                        title = item.orderingName.orEmpty(),
                        tab = Tab.PlaylistTab(item.serviceId, item.url, item.orderingName),
                        imageUrl = item.thumbnailUrl,
                        leading = Leading.COVER
                    )

                    else -> null
                }
            }
        }
        show(context, R.string.select_a_playlist, R.string.no_playlist_bookmarked_yet, options, onPicked)
    }

    fun pickFeedGroup(context: Context, onPicked: (Tab) -> Unit) {
        val options = NewPipeDatabase.getInstance(context).feedGroupDAO().getAll().map { list ->
            list.map {
                Option(
                    key = it.uid.toString(),
                    title = it.name,
                    tab = Tab.FeedGroupTab(it.uid, it.name, it.icon.getDrawableRes()),
                    icon = it.icon.getDrawableRes()
                )
            }
        }
        show(context, R.string.select_a_feed_group, R.string.no_feed_group_created_yet, options, onPicked)
    }

    private fun show(
        context: Context,
        title: Int,
        empty: Int,
        source: Flowable<List<Option>>,
        onPicked: (Tab) -> Unit
    ) {
        YpDialogs.sheet(context) { dismiss ->
            val flow = remember { source.subscribeOn(Schedulers.io()).asFlow() }
            val options by flow.collectAsState(initial = null)
            YpSheet {
                YpSheetTitle(stringResource(title))
                OptionList(options, stringResource(empty)) {
                    dismiss()
                    onPicked(it.tab)
                }
            }
        }.show()
    }

    @Composable
    private fun OptionList(options: List<Option>?, emptyText: String, onPick: (Option) -> Unit) {
        when {
            options == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            options.isEmpty() -> Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 16.dp),
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                items(options, key = { it.key }) { option ->
                    YpSheetItem(
                        label = option.title,
                        onClick = { onPick(option) },
                        leading = { OptionLeading(option) }
                    )
                }
            }
        }
    }

    @Composable
    private fun OptionLeading(option: Option) {
        when (option.leading) {
            Leading.ICON -> Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(if (option.icon != 0) option.icon else R.drawable.ic_whatshot),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Leading.AVATAR, Leading.COVER -> Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(if (option.leading == Leading.AVATAR) CircleShape else RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = option.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
