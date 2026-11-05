/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.core.model.YpChannel
import io.github.yewplay.core.model.YpGroup
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.subscriptions.FeedActions
import io.github.yewplay.feature.subscriptions.FeedEntry
import io.github.yewplay.feature.subscriptions.FeedIcons
import io.github.yewplay.feature.subscriptions.FeedMenuEntry
import io.github.yewplay.feature.subscriptions.FeedScreen
import io.github.yewplay.feature.subscriptions.FeedSection
import io.github.yewplay.feature.subscriptions.FeedStrings
import io.github.yewplay.feature.subscriptions.FeedUiState
import io.github.yewplay.feature.subscriptions.SubscriptionsActions
import io.github.yewplay.feature.subscriptions.SubscriptionsStrings
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.reactive.asFlow
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.subscription.SubscriptionsImportExportHelper
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.local.yewplay.YpSubscriptionImport
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StreamTypeUtil

/**
 * YewPlay: "Subscriptions" (proposal, p. 14) in Compose: channel avatars with the ruby dot,
 * groups as tabs and the feed by day. Loading and refreshing are NewPipe's FeedViewModel and
 * FeedLoadService, as before.
 */
class FeedFragment : BaseFragment() {
    private lateinit var viewModel: FeedViewModel
    private lateinit var importHelper: SubscriptionsImportExportHelper
    private val disposables = CompositeDisposable()
    private val uiState = MutableStateFlow(FeedUiState())

    /** Channels with videos from the last 24 hours, for the ruby dot on the avatars. */
    private val recentUploaders = MutableStateFlow<Set<String>>(emptySet())

    private var groupId = FeedGroupEntity.GROUP_ALL_ID
    private var groupName = ""
    private var streams: Map<String, StreamWithState> = emptyMap()
    private var lastError: ErrorInfo? = null

    /** The update whose item errors were already shown, so they are shown only once. */
    private var handledUpdate: OffsetDateTime? = null
    private var handledFirstLoad = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(KEY_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
            ?: FeedGroupEntity.GROUP_ALL_ID
        groupName = arguments?.getString(KEY_GROUP_NAME).orEmpty()
        uiState.update {
            it.copy(selectedGroupId = groupId.takeIf { id -> id != FeedGroupEntity.GROUP_ALL_ID })
        }
        // registers for the result of the file picker, so it must be created here
        importHelper = SubscriptionsImportExportHelper(this)
        val factory = FeedViewModel.getFactory(requireContext(), groupId)
        viewModel = ViewModelProvider(this, factory)[FeedViewModel::class.java]
        viewModel.stateLiveData.observe(this) { state -> state?.let(::handleState) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        val subscriptions = SubscriptionManager(context).subscriptions().asFlow()
        val groups = FeedDatabaseManager(context).groups().asFlow()

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by uiState.collectAsState()
                val subscriptionList by subscriptions.collectAsState(initial = null)
                val groupList by groups.collectAsState(initial = emptyList())
                val recent by recentUploaders.collectAsState()
                YpTheme(themeKey, nightThemeKey) {
                    FeedScreen(
                        state = state.copy(
                            hasSubscriptions = subscriptionList?.isNotEmpty(),
                            channels = channels(subscriptionList.orEmpty(), recent),
                            groups = groupList.map { YpGroup(it.uid, it.name) }
                        ),
                        menu = listOf(
                            FeedMenuEntry(stringResource(R.string.yp_subscriptions_channels)) {
                                NavigationHelper.openSubscriptionFragment(parentFragmentManager)
                            },
                            FeedMenuEntry(stringResource(R.string.fast_mode), ::showFastModeHelp)
                        ),
                        strings = FeedStrings(
                            title = stringResource(R.string.yp_nav_subscriptions),
                            header = SubscriptionsStrings(
                                all = stringResource(R.string.yp_feed_all),
                                addGroup = stringResource(R.string.yp_feed_add_group)
                            ),
                            filter = stringResource(R.string.feed_show_hide_streams),
                            empty = stringResource(R.string.no_videos),
                            emptyDetail = stringResource(R.string.empty_list_subtitle),
                            refresh = stringResource(R.string.yp_refresh),
                            error = YpErrorLabels(
                                retry = stringResource(R.string.retry),
                                openInBrowser = stringResource(R.string.open_in_browser)
                            ),
                            noSubscriptionsTitle = stringResource(R.string.yp_subs_empty_title),
                            noSubscriptionsText = stringResource(R.string.yp_subs_empty_text),
                            findChannels = stringResource(R.string.yp_subs_find),
                            importSubscriptions = stringResource(R.string.yp_subs_import)
                        ),
                        icons = FeedIcons(
                            filter = painterResource(R.drawable.ic_visibility_on),
                            menu = painterResource(R.drawable.ic_more_vert),
                            itemMenu = painterResource(R.drawable.ic_more_vert)
                        ),
                        actions = FeedActions(
                            header = SubscriptionsActions(
                                openChannel = { channel ->
                                    NavigationHelper.openChannelFragment(
                                        fm,
                                        channel.serviceId,
                                        channel.url,
                                        channel.name
                                    )
                                },
                                selectGroup = ::selectGroup,
                                addGroup = {
                                    FeedGroupDialog.newInstance().show(parentFragmentManager, null)
                                }
                            ),
                            onFilter = ::showStreamVisibilityDialog,
                            onItemClick = ::openVideo,
                            onItemMenu = ::showItemMenu,
                            onRefresh = ::refresh,
                            onErrorAction = {
                                lastError?.let { ErrorUtil.openActivity(requireContext(), it) }
                            },
                            onFindChannels = {
                                NavigationHelper.openSearchFragment(
                                    fm,
                                    ServiceHelper.getSelectedServiceId(requireContext()),
                                    ""
                                )
                            },
                            onImportSubscriptions = {
                                YpSubscriptionImport.showChooser(this@FeedFragment, importHelper, fm)
                            }
                        )
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    private fun channels(subscriptions: List<SubscriptionEntity>, recent: Set<String>): List<YpChannel> {
        return subscriptions
            .mapNotNull { subscription ->
                subscription.url?.let { url ->
                    YpChannel(
                        serviceId = subscription.serviceId,
                        url = url,
                        name = subscription.name.orEmpty(),
                        avatarUrl = subscription.avatarUrl,
                        hasNewVideos = url in recent
                    )
                }
            }
            .sortedByDescending { it.hasNewVideos }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* States                                                                                    */
    /* ---------------------------------------------------------------------------------------- */

    private fun handleState(state: FeedState) {
        when (state) {
            is FeedState.ProgressState -> {
                val indeterminate = state.currentProgress == -1 && state.maxProgress == -1
                uiState.update {
                    it.copy(
                        isRefreshing = true,
                        error = null,
                        progressText = when {
                            !indeterminate -> "${state.currentProgress}/${state.maxProgress}"
                            state.progressMessage > 0 -> getString(state.progressMessage)
                            else -> null
                        },
                        progress = if (!indeterminate && state.maxProgress > 0) {
                            state.currentProgress.toFloat() / state.maxProgress
                        } else {
                            null
                        }
                    )
                }
            }

            is FeedState.LoadedState -> handleLoaded(state)

            is FeedState.ErrorState -> {
                val error = state.error
                if (error == null) {
                    uiState.update { it.copy(isRefreshing = false) }
                } else {
                    val info = ErrorInfo(error, UserAction.REQUESTED_FEED, "Loading feed")
                    lastError = info
                    uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = YpItems.errorUi(requireContext(), info)
                        )
                    }
                }
            }
        }
    }

    private fun handleLoaded(state: FeedState.LoadedState) {
        val list = state.items.map { it.streamWithState }
        val dayAgo = OffsetDateTime.now().minusDays(1)
        recentUploaders.value = list.mapNotNull { item ->
            item.stream.uploaderUrl.takeIf { item.stream.uploadDate?.isAfter(dayAgo) == true }
        }.toSet()

        val keyed = list.mapIndexed { index, item -> "${item.stream.uid}:$index" to item }
        streams = keyed.toMap()
        val sections = sections(keyed, state.oldestUpdate)
        uiState.update {
            it.copy(
                sections = sections,
                isLoading = false,
                isRefreshing = false,
                progress = null,
                progressText = null,
                isEmpty = list.isEmpty(),
                error = null,
                notice = if (state.notLoadedCount > 0) {
                    getString(R.string.feed_subscription_not_loaded_count, state.notLoadedCount)
                } else {
                    null
                }
            )
        }

        // show the errors of each update only once, as before
        if (!handledFirstLoad || handledUpdate != state.oldestUpdate) {
            handleItemsErrors(state.itemsErrors)
        }
        handledFirstLoad = true
        handledUpdate = state.oldestUpdate
    }

    /** "Today", "Yesterday", "This week" and "Older"; items without a date stay where they are. */
    private fun sections(
        items: List<Pair<String, StreamWithState>>,
        oldestUpdate: OffsetDateTime?
    ): List<FeedSection> {
        val today = LocalDate.now()
        val groups = linkedMapOf<Int, MutableList<FeedEntry>>()
        var last = R.string.yp_feed_today
        for ((key, item) in items) {
            val date = item.stream.uploadDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()
            val title = when {
                date == null -> last
                !date.isBefore(today) -> R.string.yp_feed_today
                date == today.minusDays(1) -> R.string.yp_feed_yesterday
                date.isAfter(today.minusDays(7)) -> R.string.yp_feed_this_week
                else -> R.string.yp_feed_older
            }
            last = title
            groups.getOrPut(title) { mutableListOf() }.add(FeedEntry(key, toUi(item)))
        }
        val updated = getString(
            R.string.feed_oldest_subscription_update,
            oldestUpdate?.let { Localization.relativeTime(it) } ?: "—"
        )
        return groups.entries.mapIndexed { index, (title, entries) ->
            val trailing = if (index == 0) {
                if (title == R.string.yp_feed_today) {
                    resources.getQuantityString(R.plurals.yp_feed_new, entries.size, entries.size) +
                        ", " + updated.replaceFirstChar { it.lowercase() }
                } else {
                    updated
                }
            } else {
                null
            }
            FeedSection(getString(title), trailing, entries)
        }
    }

    private fun toUi(item: StreamWithState): YpStreamItem {
        val stream = item.stream
        val context = requireContext()
        val live = StreamTypeUtil.isLiveStream(stream.streamType)
        val viewCount = stream.viewCount
        val views = when {
            viewCount == null || viewCount < 0 -> ""
            stream.streamType == StreamType.AUDIO_LIVE_STREAM -> Localization.listeningCount(context, viewCount)
            stream.streamType == StreamType.LIVE_STREAM -> Localization.shortWatchingCount(context, viewCount)
            else -> Localization.shortViewCount(context, viewCount)
        }
        val date = stream.uploadDate?.let { Localization.relativeTime(it) } ?: stream.textualUploadDate.orEmpty()
        val progress = item.stateProgressMillis
        return YpStreamItem(
            serviceId = stream.serviceId,
            url = stream.url,
            title = stream.title,
            uploaderName = stream.uploader,
            thumbnailUrl = stream.thumbnailUrl,
            durationText = when {
                stream.duration > 0 -> Localization.getDurationString(stream.duration)
                live -> getString(R.string.duration_live)
                else -> null
            },
            isLive = live,
            details = when {
                date.isEmpty() -> views
                views.isEmpty() -> date
                else -> Localization.concatenateStrings(views, date)
            },
            progress = if (progress != null && stream.duration > 0) {
                (progress / 1000f / stream.duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Actions                                                                                   */
    /* ---------------------------------------------------------------------------------------- */

    private fun refresh() {
        requireActivity().startService(
            Intent(requireContext(), FeedLoadService::class.java)
                .putExtra(FeedLoadService.EXTRA_GROUP_ID, groupId)
        )
    }

    private fun selectGroup(group: YpGroup?) {
        val newGroupId = group?.id ?: FeedGroupEntity.GROUP_ALL_ID
        if (newGroupId == groupId) {
            return
        }
        parentFragmentManager.popBackStack()
        NavigationHelper.openFeedFragment(parentFragmentManager, newGroupId, group?.name)
    }

    private fun openVideo(entry: FeedEntry) {
        val stream = streams[entry.key]?.stream ?: return
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            fm,
            stream.serviceId,
            stream.url,
            stream.title,
            null,
            false
        )
    }

    private fun showItemMenu(entry: FeedEntry) {
        val stream = streams[entry.key]?.stream ?: return
        try {
            val item = stream.toStreamInfoItem()
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, item).create().show()
        } catch (e: IllegalArgumentException) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening the item menu", e)
        }
    }

    private fun showStreamVisibilityDialog() {
        val labels = arrayOf(
            getString(R.string.feed_show_watched),
            getString(R.string.feed_show_partially_watched),
            getString(R.string.feed_show_upcoming)
        )
        val checked = booleanArrayOf(
            viewModel.getShowPlayedItemsFromPreferences(),
            viewModel.getShowPartiallyPlayedItemsFromPreferences(),
            viewModel.getShowFutureItemsFromPreferences()
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.feed_hide_streams_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.setSaveShowPlayedItems(checked[0])
                viewModel.setSaveShowPartiallyPlayedItems(checked[1])
                viewModel.setSaveShowFutureItems(checked[2])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFastModeHelp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val key = getString(R.string.feed_use_dedicated_fetch_method_key)
        val dedicated = prefs.getBoolean(key, false)
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.feed_use_dedicated_fetch_method_help_text)
            .setNeutralButton(
                if (dedicated) {
                    R.string.feed_use_dedicated_fetch_method_disable_button
                } else {
                    R.string.feed_use_dedicated_fetch_method_enable_button
                }
            ) { _, _ -> prefs.edit { putBoolean(key, !dedicated) } }
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Errors of single channels, as before                                                      */
    /* ---------------------------------------------------------------------------------------- */

    private fun handleItemsErrors(errors: List<Throwable>) {
        errors.forEachIndexed { i, t ->
            if (t is FeedLoadService.RequestException && t.cause is ContentNotAvailableException) {
                disposables.add(
                    Single.fromCallable {
                        NewPipeDatabase.getInstance(requireContext()).subscriptionDAO()
                            .getSubscription(t.subscriptionId)
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { entity -> showFeedNotAvailable(entity, t.cause, errors.subList(i + 1, errors.size)) },
                            { }
                        )
                )
                // the remaining errors are handled after this dialog
                return
            }
        }
        if (errors.isNotEmpty()) {
            ErrorUtil.showSnackbar(this, ErrorInfo(errors, UserAction.REQUESTED_FEED, ""))
        }
    }

    private fun showFeedNotAvailable(
        subscription: SubscriptionEntity,
        cause: Throwable?,
        nextErrors: List<Throwable>
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val fastMode = prefs.getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feed_load_error)
            .setPositiveButton(R.string.unsubscribe) { _, _ ->
                SubscriptionManager(requireContext())
                    .deleteSubscription(subscription.serviceId, subscription.url!!)
                    .subscribe()
                handleItemsErrors(nextErrors)
            }
            .setNegativeButton(R.string.cancel, null)
        var message = getString(R.string.feed_load_error_account_info, subscription.name)
        if (cause is AccountTerminatedException) {
            message += "\n" + getString(R.string.feed_load_error_terminated)
        } else if (cause is ContentNotAvailableException) {
            if (fastMode) {
                message += "\n" + getString(R.string.feed_load_error_fast_unknown)
                builder.setNeutralButton(R.string.feed_use_dedicated_fetch_method_disable_button) { _, _ ->
                    prefs.edit { putBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false) }
                }
            } else if (!cause.message.isNullOrEmpty()) {
                message += "\n" + cause.message
            }
        }
        builder.setMessage(message).show()
    }

    companion object {
        const val KEY_GROUP_ID = "ARG_GROUP_ID"
        const val KEY_GROUP_NAME = "ARG_GROUP_NAME"

        @JvmStatic
        fun newInstance(
            groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
            groupName: String? = null
        ): FeedFragment = FeedFragment().apply {
            arguments = Bundle().apply {
                putLong(KEY_GROUP_ID, groupId)
                putString(KEY_GROUP_NAME, groupName)
            }
        }
    }
}
