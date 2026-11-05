/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSkeletonList
import io.github.yewplay.core.designsystem.YpSwitch
import io.github.yewplay.core.designsystem.YpTheme
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.reactive.asFlow
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.yewplay.YpThemePrefs

/**
 * YewPlay: the notifications of new videos per subscribed channel, in Compose: a switch on
 * each channel and "Toggle all" to turn them all on or off at once.
 */
class NotificationModeConfigFragment : Fragment() {
    private lateinit var subscriptionManager: SubscriptionManager
    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionManager = SubscriptionManager(requireContext())
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        val channelsFlow = subscriptionManager.subscriptions().subscribeOn(Schedulers.io()).asFlow()
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val channels by channelsFlow.collectAsState(initial = null)
                YpTheme(themeKey, nightThemeKey) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        YpPillButton(
                            modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                            label = stringResource(R.string.toggle_all),
                            primary = false,
                            onClick = { channels?.let(::toggleAll) }
                        )
                        val list = channels
                        if (list == null) {
                            YpSkeletonList()
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(list, key = { it.uid }) { ChannelRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ChannelRow(channel: SubscriptionEntity) {
        val enabled = channel.notificationMode != NotificationMode.DISABLED
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setMode(channel, !enabled) }
                .padding(horizontal = YpScreenMargin, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            YpAvatar(url = channel.avatarUrl, size = 40)
            Text(
                modifier = Modifier.weight(1f),
                text = channel.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            YpSwitch(checked = enabled, onCheckedChange = { setMode(channel, it) })
        }
    }

    /** Turns all on when the first channel is off, otherwise all off (as NewPipe did). */
    private fun toggleAll(channels: List<SubscriptionEntity>) {
        val first = channels.firstOrNull() ?: return
        val enable = first.notificationMode == NotificationMode.DISABLED
        channels.forEach { setMode(it, enable) }
    }

    private fun setMode(channel: SubscriptionEntity, enabled: Boolean) {
        val mode = if (enabled) NotificationMode.ENABLED else NotificationMode.DISABLED
        disposables.add(
            subscriptionManager.updateNotificationMode(channel.serviceId, channel.url.orEmpty(), mode)
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }
}
