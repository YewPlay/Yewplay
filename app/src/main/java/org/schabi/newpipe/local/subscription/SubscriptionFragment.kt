/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetHeader
import io.github.yewplay.core.designsystem.YpSheetItem
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.feature.subscriptions.ManageGroup
import io.github.yewplay.feature.subscriptions.ManageSubscriptionsActions
import io.github.yewplay.feature.subscriptions.ManageSubscriptionsIcons
import io.github.yewplay.feature.subscriptions.ManageSubscriptionsScreen
import io.github.yewplay.feature.subscriptions.ManageSubscriptionsState
import io.github.yewplay.feature.subscriptions.ManageSubscriptionsStrings
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.reactive.asFlow
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialog
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.local.yewplay.YpSubscriptionImport
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: the subscriptions, in Compose: the groups of channels (new, all, each group) and
 * every subscribed channel. Import and export are in the menu; long press a channel for share,
 * open in browser and unsubscribe, or a group to edit it.
 */
class SubscriptionFragment : BaseFragment() {
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var importExportHelper: SubscriptionsImportExportHelper
    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionManager = SubscriptionManager(requireContext())
        // registers for the result of the file pickers, so it must be created here
        importExportHelper = SubscriptionsImportExportHelper(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        val channelsFlow = subscriptionManager.subscriptions().subscribeOn(Schedulers.io()).asFlow()
        val groupsFlow = FeedDatabaseManager(context).groups().subscribeOn(Schedulers.io()).asFlow()
        // as a tab of the home there is nothing to go back to
        val showBack = parentFragment == null
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val entities by channelsFlow.collectAsState(initial = null)
                val groupEntities by groupsFlow.collectAsState(initial = emptyList())
                val channels = remember(entities) { entities?.map { it.toUi() } }
                YpTheme(themeKey, nightThemeKey) {
                    ManageSubscriptionsScreen(
                        state = ManageSubscriptionsState(
                            channels = channels,
                            groups = groupEntities.map { group ->
                                ManageGroup(group.uid, group.name, painterResource(group.icon.getDrawableRes()))
                            },
                            showBack = showBack
                        ),
                        strings = ManageSubscriptionsStrings(
                            title = stringResource(R.string.tab_subscriptions),
                            groups = stringResource(R.string.feed_groups_header_title),
                            newGroup = stringResource(R.string.feed_create_new_group_button_title),
                            all = stringResource(R.string.all),
                            channels = stringResource(R.string.yp_subs_channels),
                            empty = stringResource(R.string.import_subscriptions_hint),
                            back = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            more = stringResource(R.string.yp_more_options),
                            reorder = stringResource(R.string.yp_subs_reorder_groups)
                        ),
                        icons = ManageSubscriptionsIcons(
                            back = painterResource(R.drawable.ic_arrow_back),
                            more = painterResource(R.drawable.ic_more_vert),
                            add = painterResource(R.drawable.ic_add),
                            all = painterResource(FeedGroupIcon.WHATS_NEW.getDrawableRes()),
                            reorder = painterResource(R.drawable.ic_sort)
                        ),
                        actions = ManageSubscriptionsActions(
                            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                            onMore = {
                                YpSubscriptionImport.showChooser(this@SubscriptionFragment, importExportHelper, fm, true)
                            },
                            onOpenAll = { NavigationHelper.openFeedFragment(fm, FeedGroupEntity.GROUP_ALL_ID, null) },
                            onOpenGroup = { NavigationHelper.openFeedFragment(fm, it.id, it.name) },
                            onEditGroup = { FeedGroupDialog.newInstance(it.id).show(fm, null) },
                            onNewGroup = { FeedGroupDialog.newInstance().show(fm, null) },
                            onReorderGroups = { FeedGroupReorderDialog().show(parentFragmentManager, null) },
                            onOpenChannel = { NavigationHelper.openChannelFragment(fm, it.serviceId, it.url, it.name) },
                            onChannelMenu = { channel ->
                                entities?.firstOrNull { it.serviceId == channel.serviceId && it.url == channel.url }
                                    ?.let(::showChannelMenu)
                            }
                        )
                    )
                }
            }
        }
    }

    private fun SubscriptionEntity.toUi() = YpChannelItem(
        serviceId = serviceId,
        url = url.orEmpty(),
        name = name.orEmpty(),
        avatarUrl = avatarUrl,
        description = null,
        details = null
    )

    /** Share, open in the browser or unsubscribe a channel. */
    private fun showChannelMenu(channel: SubscriptionEntity) {
        val context = requireContext()
        val item = channel.toChannelInfoItem()
        YpDialogs.sheet(context) { dismiss ->
            YpSheet {
                YpSheetHeader(
                    title = item.name,
                    subtitle = null,
                    leading = { YpAvatar(url = channel.avatarUrl, size = 44) }
                )
                YpSheetItem(
                    label = stringResource(R.string.share),
                    icon = painterResource(R.drawable.ic_share),
                    onClick = {
                        dismiss()
                        ShareUtils.shareText(context, item.name, item.url, item.thumbnails)
                    }
                )
                YpSheetItem(
                    label = stringResource(R.string.open_in_browser),
                    icon = painterResource(R.drawable.yp_ic_arrow_square_out),
                    onClick = {
                        dismiss()
                        ShareUtils.openUrlInBrowser(context, item.url)
                    }
                )
                YpSheetItem(
                    label = stringResource(R.string.unsubscribe),
                    icon = painterResource(R.drawable.ic_delete),
                    destructive = true,
                    onClick = {
                        dismiss()
                        unsubscribe(item.serviceId, item.url)
                    }
                )
            }
        }.show()
    }

    private fun unsubscribe(serviceId: Int, url: String) {
        val context = requireContext().applicationContext
        disposables.add(
            subscriptionManager.deleteSubscription(serviceId, url).subscribe {
                Toast.makeText(context, R.string.channel_unsubscribed, Toast.LENGTH_SHORT).show()
            }
        )
    }

    companion object {
        val JSON_MIME_TYPE = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension("json") ?: "application/octet-stream"
    }
}
