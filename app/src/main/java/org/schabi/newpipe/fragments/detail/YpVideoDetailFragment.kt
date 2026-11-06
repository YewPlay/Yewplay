/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import coil3.util.CoilUtils
import com.evernote.android.state.State
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.github.yewplay.core.designsystem.YpTheme
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.LinkedList
import java.util.Objects
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.App
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.EmptyFragment
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.YpLocalPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.event.OnKeyDownListener
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked
import org.schabi.newpipe.player.helper.PlayerHelper.isClearingQueueConfirmationRequired
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.DependentPreferenceHelper.getResumePlaybackEnabled
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.ListHelper.getUrlAndNonTorrentStreams
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.NavigationHelper.openPlayQueue
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.YpViewMotion
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * YewPlay: the video page, in Kotlin. The header (title, channel, counts, actions) is drawn in
 * Compose with [YpDetailHeader]; the thumbnail/player area, the tabs and the mini player overlay
 * stay views, because the player service inserts its surface there.
 */
class YpVideoDetailFragment :
    BaseStateFragment<StreamInfo>(),
    BackPressable,
    PlayerServiceExtendedEventListener,
    OnKeyDownListener {

    // tabs
    private var showComments = false
    private var showRelatedItems = false
    private var showDescription = false
    private var selectedTabTag: String? = null

    @AttrRes
    private val tabIcons: MutableList<Int> = ArrayList()

    @StringRes
    private val tabContentDescriptions: MutableList<Int> = ArrayList()
    private var tabSettingsChanged = false
    private var lastAppBarVerticalOffset = Int.MAX_VALUE // prevents useless updates

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (getString(R.string.show_comments_key) == key) {
                showComments = sharedPreferences.getBoolean(key, true)
                tabSettingsChanged = true
            } else if (getString(R.string.show_next_video_key) == key) {
                showRelatedItems = sharedPreferences.getBoolean(key, true)
                tabSettingsChanged = true
            } else if (getString(R.string.show_description_key) == key) {
                showDescription = sharedPreferences.getBoolean(key, true)
                tabSettingsChanged = true
            }
        }

    @State
    @JvmField
    var serviceId = NO_SERVICE_ID

    @State
    @JvmField
    var title: String = ""

    @State
    @JvmField
    var url: String? = null

    var playQueue: PlayQueue? = null

    @State
    @JvmField
    var bottomSheetState = BottomSheetBehavior.STATE_EXPANDED

    @State
    @JvmField
    var lastStableBottomSheetState = BottomSheetBehavior.STATE_EXPANDED

    @State
    @JvmField
    var autoPlayEnabled = true

    private var forceFullscreen = false

    private var currentInfo: StreamInfo? = null
    private var currentWorker: Disposable? = null
    private val disposables = CompositeDisposable()
    private var subscriptionDisposable: Disposable? = null
    private var subscriptionManager: SubscriptionManager? = null
    private var positionSubscriber: Disposable? = null

    private var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */

    private var binding: FragmentVideoDetailBinding? = null

    private lateinit var pageAdapter: TabAdapter

    private var settingsContentObserver: ContentObserver? = null
    private var playerService: PlayerService? = null
    private var player: Player? = null
    private val playerHolder = PlayerHolder.getInstance()

    /** YewPlay: state of the Compose header, updated as the legacy flow progresses. */
    private val headerState = MutableStateFlow(YpDetailHeaderState())

    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    ////////////////////////////////////////////////////////////////////////// */

    override fun onServiceConnected(connectedPlayerService: PlayerService) {
        playerService = connectedPlayerService
    }

    override fun onPlayerConnected(connectedPlayer: Player, playAfterConnect: Boolean) {
        player = connectedPlayer

        // It will do nothing if the player is not in fullscreen mode
        hideSystemUiIfNeeded()

        val playerUi = player!!.UIs().get(MainPlayerUi::class.java)
        if (!player!!.videoPlayerSelected() && !playAfterConnect) {
            return
        }

        if (DeviceUtils.isLandscape(requireContext())) {
            // If the video is playing but orientation changed
            // let's make the video in fullscreen again
            checkLandscape()
        } else if (playerUi.map { ui -> ui.isFullscreen && !ui.isVerticalVideo }.orElse(false) &&
            !DeviceUtils.isTablet(activity)
        ) {
            // Tablet UI has orientation-independent fullscreen.
            // Device is in portrait orientation after rotation but UI is in fullscreen.
            // Return back to non-fullscreen state
            playerUi.ifPresent(MainPlayerUi::toggleFullscreen)
        }

        if (playAfterConnect ||
            (currentInfo != null && isAutoplayEnabled() && playerUi.isEmpty)
        ) {
            autoPlayEnabled = true // forcefully start playing
            openVideoPlayerAutoFullscreen()
        }
        updateOverlayPlayQueueButtonVisibility()
    }

    override fun onPlayerDisconnected() {
        player = null
        // the binding could be null at this point, if the app is finishing
        if (binding != null) {
            restoreDefaultBrightness()
        }
    }

    override fun onServiceDisconnected() {
        playerService = null
    }

    /*//////////////////////////////////////////////////////////////////////// */

    companion object {
        const val KEY_SWITCHING_PLAYERS = "switching_players"

        // YewPlay: opaque mini player, so the video does not show through the floating capsule
        private const val MAX_OVERLAY_ALPHA = 1f
        private const val MAX_PLAYER_HEIGHT = 0.7f

        @JvmField
        val ACTION_SHOW_MAIN_PLAYER: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER"

        @JvmField
        val ACTION_HIDE_MAIN_PLAYER: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER"

        @JvmField
        val ACTION_PLAYER_STARTED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_PLAYER_STARTED"

        @JvmField
        val ACTION_VIDEO_FRAGMENT_RESUMED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED"

        @JvmField
        val ACTION_VIDEO_FRAGMENT_STOPPED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED"

        private const val COMMENTS_TAB_TAG = "COMMENTS"
        private const val RELATED_TAB_TAG = "NEXT VIDEO"
        private const val DESCRIPTION_TAB_TAG = "DESCRIPTION TAB"
        private const val EMPTY_TAB_TAG = "EMPTY TAB"

        /**
         * Stack that contains the "navigation history".<br></br>
         * The peek is the current video.
         */
        private var stack = LinkedList<StackItem>()

        @JvmStatic
        fun getInstance(
            serviceId: Int,
            url: String?,
            name: String,
            queue: PlayQueue?
        ): YpVideoDetailFragment {
            val instance = YpVideoDetailFragment()
            instance.setInitialData(serviceId, url, name, queue)
            return instance
        }

        @JvmStatic
        fun getInstanceInCollapsedState(): YpVideoDetailFragment {
            val instance = YpVideoDetailFragment()
            instance.updateBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
            return instance
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    ////////////////////////////////////////////////////////////////////////// */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        showComments = prefs.getBoolean(getString(R.string.show_comments_key), true)
        showRelatedItems = prefs.getBoolean(getString(R.string.show_next_video_key), true)
        showDescription = prefs.getBoolean(getString(R.string.show_description_key), true)
        selectedTabTag = prefs.getString(
            getString(R.string.stream_info_selected_tab_key),
            COMMENTS_TAB_TAG
        )
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        setupBroadcastReceiver()

        settingsContentObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                if (activity != null && !globalScreenOrientationLocked(activity)) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
        activity.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            settingsContentObserver!!
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = FragmentVideoDetailBinding.inflate(inflater, container, false)
        binding = b
        setupComposeHeader(b)
        return b.root
    }

    /** YewPlay: wires the Compose header to the actions that keep the legacy flows. */
    private fun setupComposeHeader(b: FragmentVideoDetailBinding) {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        b.detailComposeHeader.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        b.detailComposeHeader.setContent {
            val state by headerState.collectAsState()
            YpTheme(themeKey, nightThemeKey) {
                YpDetailHeader(
                    state = state,
                    labels = YpDetailHeaderLabels(
                        subscribe = stringResource(R.string.subscribe_button_title),
                        subscribed = stringResource(R.string.subscribed_button_title),
                        download = stringResource(R.string.download),
                        background = stringResource(R.string.controls_background_title),
                        append = stringResource(R.string.controls_add_to_playlist_title),
                        popup = stringResource(R.string.controls_popup_title),
                        share = stringResource(R.string.share),
                        openInBrowser = stringResource(R.string.open_in_browser),
                        playWithKodi = stringResource(R.string.play_with_kodi_title),
                        crashThePlayer = stringResource(R.string.crash_the_player),
                        ratingsDisabled = stringResource(R.string.disabled),
                        showMore = stringResource(R.string.show_more)
                    ),
                    icons = YpDetailHeaderIcons(
                        chevronDown = painterResource(R.drawable.ic_expand_more),
                        download = painterResource(R.drawable.ic_file_download),
                        headphones = painterResource(R.drawable.ic_headset),
                        playlistAdd = painterResource(R.drawable.ic_playlist_add),
                        popup = painterResource(R.drawable.ic_picture_in_picture),
                        share = painterResource(R.drawable.ic_share),
                        openInBrowser = painterResource(R.drawable.ic_language),
                        kodi = painterResource(R.drawable.ic_cast),
                        crash = painterResource(R.drawable.ic_bug_report),
                        thumbUp = painterResource(R.drawable.ic_thumb_up),
                        thumbDown = painterResource(R.drawable.ic_thumb_down)
                    ),
                    actions = YpDetailHeaderActions(
                        onTitleClick = ::toggleTitleAndSecondaryControls,
                        onTitleLongClick = {
                            runWithInfo { info ->
                                ShareUtils.copyToClipboard(requireContext(), info.name)
                            }
                        },
                        onUploaderClick = {
                            runWithInfo { info ->
                                if (info.subChannelUrl.isNullOrEmpty()) {
                                    if (!info.uploaderUrl.isNullOrEmpty()) {
                                        openChannel(info.uploaderUrl, info.uploaderName)
                                    }
                                    if (DEBUG) {
                                        Log.i(TAG, "Can't open sub-channel: no channel URL")
                                    }
                                } else {
                                    openChannel(info.subChannelUrl, info.subChannelName)
                                }
                            }
                        },
                        onUploaderLongClick = {
                            runWithInfo { info ->
                                if (info.subChannelUrl.isNullOrEmpty()) {
                                    Log.w(TAG, "Can't open parent channel: no parent channel URL")
                                } else {
                                    openChannel(info.uploaderUrl, info.uploaderName)
                                }
                            }
                        },
                        onSubscribe = { subscribeClickListener?.run() },
                        onLike = ::likeCurrentStream,
                        onDownload = {
                            if (PermissionHelper.checkStoragePermissions(
                                    activity,
                                    PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE
                                )
                            ) {
                                openDownloadDialog()
                            }
                        },
                        onDownloadLongClick = {
                            runWithInfo { NavigationHelper.openDownloads(activity) }
                        },
                        onBackground = { openBackgroundPlayer(false) },
                        onBackgroundLongClick = {
                            runWithInfo { openBackgroundPlayer(true) }
                        },
                        onAppend = ::appendToPlaylistClicked,
                        onPopup = { openPopupPlayer(false) },
                        onPopupLongClick = {
                            runWithInfo { openPopupPlayer(true) }
                        },
                        onShare = {
                            runWithInfo { info ->
                                ShareUtils.shareText(
                                    requireContext(),
                                    info.name,
                                    info.url,
                                    info.thumbnails
                                )
                            }
                        },
                        onOpenInBrowser = {
                            runWithInfo { info ->
                                ShareUtils.openUrlInBrowser(requireContext(), info.url)
                            }
                        },
                        onPlayWithKodi = {
                            runWithInfo { info ->
                                KoreUtils.playWithKore(requireContext(), Uri.parse(info.url))
                            }
                        },
                        onCrashThePlayer = {
                            VideoDetailPlayerCrasher.onCrashThePlayer(requireContext(), player)
                        }
                    )
                )
            }
        }
    }

    /** The action of the subscribe button for the current video, set by [setupSubscribeButton]. */
    private var subscribeClickListener: Runnable? = null

    /** Runs [consumer] with the current info, as the legacy guarded click listeners did. */
    private fun runWithInfo(consumer: Consumer<StreamInfo>) {
        val info = currentInfo
        if (!isLoading.get() && info != null) {
            consumer.accept(info)
        }
    }

    override fun onPause() {
        super.onPause()
        currentWorker?.dispose()
        restoreDefaultBrightness()
        val b = binding
        if (b != null) {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit {
                    putString(
                        getString(R.string.stream_info_selected_tab_key),
                        pageAdapter.getItemTitle(b.viewPager.currentItem)
                    )
                }
        }
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) {
            Log.d(TAG, "onResume() called")
        }

        activity.sendBroadcast(Intent(ACTION_VIDEO_FRAGMENT_RESUMED))

        updateOverlayPlayQueueButtonVisibility()

        setupBrightness()

        if (tabSettingsChanged) {
            tabSettingsChanged = false
            initTabs()
            currentInfo?.let(::updateTabs)
        }

        // Check if it was loading when the fragment was stopped/paused
        if (wasLoading.getAndSet(false) && !wasCleared()) {
            startLoading(false)
        }
    }

    override fun onStop() {
        super.onStop()

        if (!activity.isChangingConfigurations) {
            activity.sendBroadcast(Intent(ACTION_VIDEO_FRAGMENT_STOPPED))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionDisposable?.dispose()

        // Stop the service when user leaves the app with double back press
        // if video player is selected. Otherwise unbind
        if (activity.isFinishing && isPlayerAvailable() && player!!.videoPlayerSelected()) {
            playerHolder.stopService()
        } else {
            playerHolder.setListener(null)
        }

        PreferenceManager.getDefaultSharedPreferences(activity)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        activity.unregisterReceiver(broadcastReceiver)
        activity.contentResolver.unregisterContentObserver(settingsContentObserver!!)

        positionSubscriber?.dispose()
        currentWorker?.dispose()
        disposables.clear()
        positionSubscriber = null
        currentWorker = null
        bottomSheetCallback?.let { bottomSheetBehavior?.removeBottomSheetCallback(it) }

        if (activity.isFinishing) {
            playQueue = null
            currentInfo = null
            stack = LinkedList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ReCaptchaActivity.RECAPTCHA_REQUEST -> if (resultCode == Activity.RESULT_OK) {
                NavigationHelper.openVideoDetailFragment(
                    requireContext(),
                    fm,
                    serviceId,
                    url,
                    title,
                    null,
                    false
                )
            } else {
                Log.e(TAG, "ReCaptcha failed")
            }

            else -> Log.e(TAG, "Request code from activity not supported [$requestCode]")
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick (view listeners; the header actions live in the Compose wiring above)
    ////////////////////////////////////////////////////////////////////////// */

    private fun setOnClickListeners() {
        val b = binding ?: return
        b.detailThumbnailRootLayout.setOnClickListener {
            autoPlayEnabled = true // forcefully start playing
            // FIXME Workaround #7427
            if (isPlayerAvailable()) {
                player!!.setRecovery()
            }
            openVideoPlayerAutoFullscreen()
        }

        val overlayListener = View.OnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
        b.overlayThumbnail.setOnClickListener(overlayListener)
        b.overlayMetadataLayout.setOnClickListener(overlayListener)
        b.overlayButtonsLayout.setOnClickListener(overlayListener)
        b.overlayCloseButton.setOnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }
        b.overlayPlayQueueButton.setOnClickListener { openPlayQueue(requireContext()) }
        b.overlayPlayPauseButton.setOnClickListener {
            if (playerIsNotStopped()) {
                player!!.playPause()
                player!!.UIs().get(VideoPlayerUi::class.java)
                    .ifPresent { ui -> ui.hideControls(0, 0) }
                showSystemUi()
            } else {
                autoPlayEnabled = true // forcefully start playing
                openVideoPlayer(false)
            }

            setOverlayPlayPauseImage(isPlayerAvailable() && player!!.isPlaying)
        }
    }

    /** Adds the current video to a playlist, as the old playlist-append button did. */
    private fun appendToPlaylistClicked() {
        runWithInfo { info ->
            if (fm != null) {
                val fragment = parentFragmentManager.findFragmentById(R.id.fragment_holder)

                // commit previous pending changes to database
                if (fragment is YpLocalPlaylistFragment) {
                    fragment.saveImmediate()
                } else if (fragment is MainFragment) {
                    fragment.commitPlaylistTabs()
                }

                disposables.add(
                    PlaylistDialog.createCorrespondingDialog(
                        requireContext(),
                        listOf(StreamEntity(info))
                    ) { dialog -> dialog.show(parentFragmentManager, TAG) }
                )
            }
        }
    }

    private fun setOnLongClickListeners() {
        val b = binding ?: return
        val overlayListener = View.OnLongClickListener {
            val info = currentInfo
            if (isLoading.get() || info == null) {
                false
            } else {
                openChannel(info.uploaderUrl, info.uploaderName)
                true
            }
        }
        b.overlayThumbnail.setOnLongClickListener(overlayListener)
        b.overlayMetadataLayout.setOnLongClickListener(overlayListener)
    }

    private fun openChannel(subChannelUrl: String?, subChannelName: String?) {
        try {
            NavigationHelper.openChannelFragment(
                fm,
                currentInfo!!.serviceId,
                subChannelUrl,
                subChannelName.orEmpty()
            )
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e)
        }
    }

    private fun toggleTitleAndSecondaryControls() {
        headerState.update {
            val expanded = !it.titleExpanded
            it.copy(titleExpanded = expanded, secondaryVisible = expanded)
        }
        // view pager height has changed, update the tab layout
        updateTabLayoutVisibility()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */

    // called from onViewCreated in {@link BaseFragment#onViewCreated}
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        val b = binding ?: return

        pageAdapter = TabAdapter(childFragmentManager)
        b.viewPager.adapter = pageAdapter
        b.tabLayout.setupWithViewPager(b.viewPager)

        b.detailThumbnailRootLayout.requestFocus()

        headerState.update {
            it.copy(
                showKodi = KoreUtils.shouldShowPlayWithKodi(requireContext(), serviceId),
                showCrashThePlayer = DEBUG && PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .getBoolean(getString(R.string.show_crash_the_player_key), false)
            )
        }
        accommodateForTvAndDesktopMode()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {
        super.initListeners()
        val b = binding ?: return

        // Workaround for #5600
        // Forcefully catch click events uncaught by children because otherwise
        // they will be caught by underlying view and "click through" will happen
        b.root.setOnClickListener { }
        b.root.setOnLongClickListener { true }

        setOnClickListeners()
        setOnLongClickListeners()

        val controlsTouchListener = View.OnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN &&
                PlayButtonHelper.shouldShowHoldToAppendTip(activity)
            ) {
                b.touchAppendDetail.animate(true, YpViewMotion.NORMAL) {
                    b.touchAppendDetail.animate(false, 1500, delay = 1000)
                }
            }
            false
        }
        // the Compose background/popup actions cannot show this hint, so it is shown
        // when the user touches the thumbnail instead
        b.detailThumbnailRootLayout.setOnTouchListener(controlsTouchListener)

        b.appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                // prevent useless updates to tab layout visibility if nothing changed
                if (verticalOffset != lastAppBarVerticalOffset) {
                    lastAppBarVerticalOffset = verticalOffset
                    // the view was scrolled
                    updateTabLayoutVisibility()
                }
            }
        )

        setupBottomPlayer()
        if (!playerHolder.isBound) {
            setHeightThumbnail()
        } else {
            playerHolder.startService(false, this)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    ////////////////////////////////////////////////////////////////////////// */

    override fun onKeyDown(keyCode: Int): Boolean {
        return isPlayerAvailable() &&
            player!!.UIs().get(VideoPlayerUi::class.java)
                .map { playerUi -> playerUi.onKeyDown(keyCode) }.orElse(false)
    }

    override fun onBackPressed(): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called")
        }

        // If we are in fullscreen mode just exit from it via first back press
        if (isFullscreen()) {
            if (!DeviceUtils.isTablet(activity)) {
                player!!.pause()
            }
            restoreDefaultOrientation()
            setAutoPlay(false)
            return true
        }

        // If we have something in history of played items we replay it here
        val queue = if (isPlayerAvailable()) player!!.playQueue else null
        if (isPlayerAvailable() &&
            queue != null &&
            player!!.videoPlayerSelected() &&
            queue.previous()
        ) {
            return true // no code here, as previous() was used in the if
        }

        // That means that we are on the start of the stack,
        if (stack.size <= 1) {
            restoreDefaultOrientation()
            return false // let MainActivity handle the onBack (e.g. to minimize the mini player)
        }

        // Remove top
        stack.pop()
        // Get stack item from the new top
        setupFromHistoryItem(Objects.requireNonNull(stack.peek()))

        return true
    }

    private fun setupFromHistoryItem(item: StackItem) {
        setAutoPlay(false)
        hideMainPlayerOnLoadingNewStream()

        setInitialData(item.serviceId, item.url, item.title ?: "", item.playQueue)
        startLoading(false)

        // Maybe an item was deleted in background activity
        val playQueueItem = item.playQueue.item ?: return

        // Update title, url, uploader from the last item in the stack (it's current now)
        val isPlayerStopped = !isPlayerAvailable() || player!!.isStopped
        if (isPlayerStopped) {
            updateOverlayData(playQueueItem.title, playQueueItem.uploader, playQueueItem.thumbnails)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    ////////////////////////////////////////////////////////////////////////// */

    override fun doInitialLoadLogic() {
        if (wasCleared()) {
            return
        }

        if (currentInfo == null) {
            prepareAndLoadInfo()
        } else {
            prepareAndHandleInfoIfNeededAfterDelay(currentInfo!!, false, 50)
        }
    }

    fun selectAndLoadVideo(
        newServiceId: Int,
        newUrl: String?,
        newTitle: String,
        newQueue: PlayQueue?
    ) {
        val oldQueue = playQueue
        if (isPlayerAvailable() && newQueue != null && oldQueue?.item != null &&
            oldQueue.item!!.url != newUrl
        ) {
            // Preloading can be disabled since playback is surely being replaced.
            player!!.disablePreloadingOfCurrentTrack()
        }

        setInitialData(newServiceId, newUrl, newTitle, newQueue)
        startLoading(false, true)
    }

    private fun prepareAndHandleInfoIfNeededAfterDelay(
        info: StreamInfo,
        scrollToTop: Boolean,
        delay: Long
    ) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity == null) {
                return@postDelayed
            }
            // Data can already be drawn, don't spend time twice
            if (info.name == headerState.value.title) {
                return@postDelayed
            }
            prepareAndHandleInfo(info, scrollToTop)
        }, delay)
    }

    private fun prepareAndHandleInfo(info: StreamInfo, scrollToTop: Boolean) {
        if (DEBUG) {
            Log.d(
                TAG,
                "prepareAndHandleInfo() called with: " +
                    "info = [$info], scrollToTop = [$scrollToTop]"
            )
        }

        showLoading()
        initTabs()

        if (scrollToTop) {
            scrollToTop()
        }
        handleResult(info)
        showContent()
    }

    private fun prepareAndLoadInfo() {
        scrollToTop()
        startLoading(false)
    }

    override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)

        initTabs()
        currentInfo = null
        currentWorker?.dispose()

        runWorker(forceLoad, stack.isEmpty())
    }

    private fun startLoading(forceLoad: Boolean, addToBackStack: Boolean) {
        super.startLoading(forceLoad)

        initTabs()
        currentInfo = null
        currentWorker?.dispose()

        runWorker(forceLoad, addToBackStack)
    }

    private fun runWorker(forceLoad: Boolean, addToBackStack: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                isLoading.set(false)
                hideMainPlayerOnLoadingNewStream()
                if (result.ageLimit != NO_AGE_LIMIT && !prefs.getBoolean(
                        getString(R.string.show_age_restricted_content),
                        false
                    )
                ) {
                    hideAgeRestrictedContent()
                } else {
                    handleResult(result)
                    showContent()
                    if (addToBackStack) {
                        if (playQueue == null) {
                            playQueue = SinglePlayQueue(result)
                        }
                        if (stack.isEmpty() ||
                            !stack.peek().playQueue.equalStreams(playQueue)
                        ) {
                            stack.push(StackItem(serviceId, url, title, playQueue))
                        }
                    }

                    if (isAutoplayEnabled() || forceFullscreen) {
                        openVideoPlayerAutoFullscreen()
                    }
                }
            }, { throwable ->
                showError(
                    ErrorInfo(
                        throwable,
                        UserAction.REQUESTED_STREAM,
                        url ?: "no url",
                        serviceId,
                        url
                    )
                )
            })
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    ////////////////////////////////////////////////////////////////////////// */

    private fun initTabs() {
        if (!::pageAdapter.isInitialized) {
            return
        }
        val b = binding ?: return
        if (pageAdapter.count != 0) {
            selectedTabTag = pageAdapter.getItemTitle(b.viewPager.currentItem)
        }
        pageAdapter.clearAllItems()
        tabIcons.clear()
        tabContentDescriptions.clear()

        if (shouldShowComments()) {
            pageAdapter.addFragment(
                CommentsFragment.getInstance(serviceId, url, title),
                COMMENTS_TAB_TAG
            )
            tabIcons.add(R.drawable.ic_comment)
            tabContentDescriptions.add(R.string.comments_tab_description)
        }

        if (showRelatedItems && b.relatedItemsLayout == null) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), RELATED_TAB_TAG)
            tabIcons.add(R.drawable.ic_art_track)
            tabContentDescriptions.add(R.string.related_items_tab_description)
        }

        if (showDescription) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), DESCRIPTION_TAB_TAG)
            tabIcons.add(R.drawable.ic_description)
            tabContentDescriptions.add(R.string.description_tab_description)
        }

        if (pageAdapter.count == 0) {
            pageAdapter.addFragment(EmptyFragment.newInstance(true), EMPTY_TAB_TAG)
        }
        pageAdapter.notifyDataSetUpdate()

        if (pageAdapter.count >= 2) {
            val position = pageAdapter.getItemPositionByTitle(selectedTabTag)
            if (position != -1) {
                b.viewPager.currentItem = position
            }
            updateTabIconsAndContentDescriptions()
        }
        // the page adapter now contains tabs: show the tab layout
        updateTabLayoutVisibility()
    }

    /**
     * To be called whenever [pageAdapter] is modified, since that triggers a refresh in
     * [FragmentVideoDetailBinding.tabLayout] resetting all tab's icons and content
     * descriptions. This reads icons from [tabIcons] and content descriptions from
     * [tabContentDescriptions], which are all set in [initTabs].
     */
    private fun updateTabIconsAndContentDescriptions() {
        val b = binding ?: return
        for (i in tabIcons.indices) {
            val tab = b.tabLayout.getTabAt(i)
            tab?.let {
                it.setIcon(tabIcons[i])
                it.setContentDescription(tabContentDescriptions[i])
            }
        }
    }

    private fun updateTabs(info: StreamInfo) {
        val b = binding ?: return
        if (showRelatedItems) {
            if (b.relatedItemsLayout == null) { // phone
                pageAdapter.updateItem(RELATED_TAB_TAG, RelatedItemsFragment.getInstance(info))
            } else { // tablet + TV
                childFragmentManager.beginTransaction()
                    .replace(R.id.relatedItemsLayout, RelatedItemsFragment.getInstance(info))
                    .commitAllowingStateLoss()
                b.relatedItemsLayout.visibility =
                    if (isFullscreen()) View.GONE else View.VISIBLE
            }
        }

        if (showDescription) {
            pageAdapter.updateItem(DESCRIPTION_TAB_TAG, DescriptionFragment(info))
        }

        b.viewPager.visibility = View.VISIBLE
        // make sure the tab layout is visible
        updateTabLayoutVisibility()
        pageAdapter.notifyDataSetUpdate()
        updateTabIconsAndContentDescriptions()
    }

    private fun shouldShowComments(): Boolean {
        return try {
            showComments && NewPipe.getService(serviceId)
                .serviceInfo
                .mediaCapabilities
                .contains(COMMENTS)
        } catch (e: ExtractionException) {
            false
        }
    }

    fun updateTabLayoutVisibility() {
        val b = binding ?: return
        if (!::pageAdapter.isInitialized) {
            return
        }

        if (pageAdapter.count < 2 || b.viewPager.visibility != View.VISIBLE) {
            // hide tab layout if there is only one tab or if the view pager is also hidden
            b.tabLayout.visibility = View.GONE
        } else {
            // call `post()` to be sure `viewPager.getHitRect()`
            // is up to date and not being currently recomputed
            b.tabLayout.post {
                val act = activity
                if (act != null && binding != null) {
                    val pagerHitRect = Rect()
                    b.viewPager.getHitRect(pagerHitRect)

                    val height = DeviceUtils.getWindowHeight(act.windowManager)
                    val viewPagerVisibleHeight = height - pagerHitRect.top
                    // see TabLayout.DEFAULT_HEIGHT, which is equal to 48dp
                    val tabLayoutHeight = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        48f,
                        resources.displayMetrics
                    )

                    if (viewPagerVisibleHeight > tabLayoutHeight * 2) {
                        // no translation at all when viewPagerVisibleHeight > tabLayout.height * 3
                        b.tabLayout.translationY =
                            0f.coerceAtLeast(tabLayoutHeight * 3 - viewPagerVisibleHeight)
                        b.tabLayout.visibility = View.VISIBLE
                    } else {
                        // view pager is not visible enough
                        b.tabLayout.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun scrollToTop() {
        binding?.appBarLayout?.setExpanded(true, true)
        // notify tab layout of scrolling
        updateTabLayoutVisibility()
    }

    fun scrollToComment(comment: CommentsInfoItem) {
        val b = binding ?: return
        if (!::pageAdapter.isInitialized) {
            return
        }
        val commentsTabPos = pageAdapter.getItemPositionByTitle(COMMENTS_TAB_TAG)
        val fragment = pageAdapter.getItem(commentsTabPos)
        if (fragment !is CommentsFragment) {
            return
        }

        // unexpand the app bar only if scrolling to the comment succeeded
        if (fragment.scrollToComment(comment)) {
            b.appBarLayout.setExpanded(false, false)
            b.viewPager.setCurrentItem(commentsTabPos, false)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    ////////////////////////////////////////////////////////////////////////// */

    private fun toggleFullscreenIfInFullscreenMode() {
        // If a user watched video inside fullscreen mode and than chose another player
        // return to non-fullscreen mode
        if (isPlayerAvailable()) {
            player!!.UIs().get(MainPlayerUi::class.java).ifPresent { playerUi ->
                if (playerUi.isFullscreen) {
                    playerUi.toggleFullscreen()
                }
            }
        }
    }

    private fun openBackgroundPlayer(append: Boolean) {
        val useExternalAudioPlayer = PreferenceManager
            .getDefaultSharedPreferences(activity)
            .getBoolean(activity.getString(R.string.use_external_audio_player_key), false)

        toggleFullscreenIfInFullscreenMode()

        if (isPlayerAvailable()) {
            // FIXME Workaround #7427
            player!!.setRecovery()
        }

        if (useExternalAudioPlayer) {
            showExternalAudioPlaybackDialog()
        } else {
            openNormalBackgroundPlayer(append)
        }
    }

    private fun openPopupPlayer(append: Boolean) {
        if (!PermissionHelper.isPopupEnabledElseAsk(activity)) {
            return
        }

        // See UI changes while remote playQueue changes
        if (!isPlayerAvailable()) {
            playerHolder.startService(false, this)
        } else {
            // FIXME Workaround #7427
            player!!.setRecovery()
        }

        toggleFullscreenIfInFullscreenMode()

        val queue = setupPlayQueueForIntent(append)
        if (append) { // resumePlayback: false
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.POPUP)
        } else {
            replaceQueueIfUserConfirms {
                NavigationHelper.playOnPopupPlayer(activity, queue, true)
            }
        }
    }

    /**
     * Opens the video player, in fullscreen if needed. In order to open fullscreen, the activity
     * is toggled to landscape orientation (which will then cause fullscreen mode).
     *
     * @param directlyFullscreenIfApplicable whether to open fullscreen if we are not already
     *                                       in landscape and screen orientation is locked
     */
    fun openVideoPlayer(directlyFullscreenIfApplicable: Boolean) {
        if (directlyFullscreenIfApplicable &&
            !DeviceUtils.isLandscape(requireContext()) &&
            PlayerHelper.globalScreenOrientationLocked(requireContext())
        ) {
            // Make sure the bottom sheet turns out expanded. When this code kicks in the bottom
            // sheet could not have fully expanded yet, and thus be in the STATE_SETTLING state.
            // When the activity is rotated, and its state is saved and then restored, the bottom
            // sheet would forget what it was doing, since even if STATE_SETTLING is restored, it
            // doesn't tell which state it was settling to, and thus the bottom sheet settles to
            // STATE_COLLAPSED. This can be solved by manually setting the state that will be
            // restored (i.e. bottomSheetState) to STATE_EXPANDED.
            updateBottomSheetState(BottomSheetBehavior.STATE_EXPANDED)
            // toggle landscape in order to open directly in fullscreen
            onScreenRotationButtonClicked()
        }

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)
        ) {
            showExternalVideoPlaybackDialog()
        } else {
            replaceQueueIfUserConfirms(::openMainPlayer)
        }
    }

    /**
     * If the option to start directly fullscreen is enabled, or if `forceFullscreen` is
     * `true` (e.g. when switching from popup player to main player with a different video),
     * calls [openVideoPlayer] with `directlyFullscreenIfApplicable = true`,
     * so that if the user is not already in landscape and he has screen orientation locked the
     * activity rotates and fullscreen starts. Otherwise, if the option to start directly fullscreen
     * is disabled and `forceFullscreen` is `false`, calls
     * [openVideoPlayer] with `directlyFullscreenIfApplicable = false`,
     * hence preventing it from going directly fullscreen.
     * `forceFullscreen` is reset to `false` after this call.
     */
    fun openVideoPlayerAutoFullscreen() {
        openVideoPlayer(
            forceFullscreen || PlayerHelper.isStartMainPlayerFullscreenEnabled(requireContext())
        )
        forceFullscreen = false
    }

    fun setForceFullscreen(force: Boolean) {
        this.forceFullscreen = force
    }

    private fun openNormalBackgroundPlayer(append: Boolean) {
        // See UI changes while remote playQueue changes
        if (!isPlayerAvailable()) {
            playerHolder.startService(false, this)
        }

        val queue = setupPlayQueueForIntent(append)
        if (append) {
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.AUDIO)
        } else {
            replaceQueueIfUserConfirms {
                NavigationHelper.playOnBackgroundPlayer(activity, queue, true)
            }
        }
    }

    private fun openMainPlayer() {
        if (!isPlayerServiceAvailable()) {
            playerHolder.startService(autoPlayEnabled, this)
            return
        }
        if (currentInfo == null) {
            return
        }

        val queue = setupPlayQueueForIntent(false)
        tryAddVideoPlayerView()

        val context = requireContext()
        val playerIntent =
            NavigationHelper.getPlayerIntent(
                context,
                PlayerService::class.java,
                queue,
                PlayerIntentType.AllOthers
            )
                .putExtra(Player.PLAY_WHEN_READY, autoPlayEnabled)
                .putExtra(Player.RESUME_PLAYBACK, true)
        ContextCompat.startForegroundService(activity, playerIntent)
    }

    /**
     * When the video detail fragment is already showing details for a video and the user opens a
     * new one, the video detail fragment changes all of its old data to the new stream, so if there
     * is a video player currently open it should be hidden. This method does exactly that. If
     * autoplay is enabled, the underlying player is not stopped completely, since it is going to
     * be reused in a few milliseconds and the flickering would be annoying.
     */
    private fun hideMainPlayerOnLoadingNewStream() {
        val root = getRoot()
        if (!isPlayerServiceAvailable() || root.isEmpty || !player!!.videoPlayerSelected()) {
            return
        }

        removeVideoPlayerView()
        if (isAutoplayEnabled()) {
            playerService!!.stopForImmediateReusing()
            root.ifPresent { view -> view.visibility = View.GONE }
        } else {
            playerHolder.stopService()
        }
    }

    private fun setupPlayQueueForIntent(append: Boolean): PlayQueue {
        if (append) {
            return SinglePlayQueue(currentInfo)
        }

        var queue = playQueue
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.isEmpty) {
            queue = SinglePlayQueue(currentInfo)
        }

        return queue
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */

    fun setAutoPlay(autoPlay: Boolean) {
        this.autoPlayEnabled = autoPlay
    }

    private fun startOnExternalPlayer(
        context: Context,
        info: StreamInfo,
        selectedStream: Stream
    ) {
        NavigationHelper.playOnExternalPlayer(
            context,
            currentInfo!!.name,
            currentInfo!!.subChannelName,
            selectedStream
        )

        val recordManager = HistoryRecordManager(requireContext())
        disposables.add(
            recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                    { /* successful */ },
                    { error ->
                        showSnackBarError(
                            ErrorInfo(
                                error,
                                UserAction.PLAY_STREAM,
                                "Got an error when modifying history on viewed"
                            )
                        )
                    }
                )
        )
    }

    private fun isExternalPlayerEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(getString(R.string.use_external_video_player_key), false)
    }

    // This method overrides default behaviour when setAutoPlay() is called.
    // Don't auto play if the user selected an external player or disabled it in settings
    private fun isAutoplayEnabled(): Boolean {
        return autoPlayEnabled &&
            !isExternalPlayerEnabled() &&
            (!isPlayerAvailable() || player!!.videoPlayerSelected()) &&
            bottomSheetState != BottomSheetBehavior.STATE_HIDDEN &&
            PlayerHelper.isAutoplayAllowedByUser(requireContext())
    }

    private fun tryAddVideoPlayerView() {
        if (isPlayerAvailable() && view != null) {
            // Setup the surface view height, so that it fits the video correctly; this is done also
            // here, and not only in the Handler, to avoid a choppy fullscreen rotation animation.
            setHeightThumbnail()
        }

        // do all the null checks in the posted lambda, too, since the player, the binding and the
        // view could be set or unset before the lambda gets executed on the next main thread cycle
        Handler(Looper.getMainLooper()).post {
            if (!isPlayerAvailable() || view == null) {
                return@post
            }

            // setup the surface view height, so that it fits the video correctly
            setHeightThumbnail()

            val b = binding ?: return@post
            player!!.UIs().get(MainPlayerUi::class.java).ifPresent { playerUi ->
                // Only reparent the view if it's not already in the playerPlaceholder.
                // Removing and re-adding a SurfaceView destroys its native surface,
                // causing a black screen until the video decoder hits the next
                // keyframe (2-5 seconds on typical DASH/HLS streams).
                if (playerUi.views.root.parent !== b.playerPlaceholder) {
                    playerUi.removeViewFromParent()
                    b.playerPlaceholder.addView(playerUi.views.root)
                }
                playerUi.setupVideoSurfaceIfNeeded()
            }
        }
    }

    private fun removeVideoPlayerView() {
        makeDefaultHeightForVideoPlaceholder()

        player?.UIs()?.get(VideoPlayerUi::class.java)
            ?.ifPresent(VideoPlayerUi::removeViewFromParent)
    }

    private fun makeDefaultHeightForVideoPlaceholder() {
        val b = binding ?: return
        if (view == null) {
            return
        }

        b.playerPlaceholder.layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
        b.playerPlaceholder.requestLayout()
    }

    private val preDrawListener: ViewTreeObserver.OnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        val metrics = resources.displayMetrics

        if (view != null) {
            val height = (
                if (DeviceUtils.isInMultiWindow(activity)) {
                    requireView()
                } else {
                    activity.window.decorView
                }
                ).height
            setHeightThumbnail(height, metrics)
            view!!.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        false
    }

    /**
     * Method which controls the size of thumbnail and the size of main player inside
     * a layout with thumbnail. It decides what height the player should have in both
     * screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher,
     * [MAX_PLAYER_HEIGHT])
     */
    private fun setHeightThumbnail() {
        val metrics = resources.displayMetrics
        val isPortrait = metrics.heightPixels > metrics.widthPixels
        requireView().viewTreeObserver.removeOnPreDrawListener(preDrawListener)

        if (isFullscreen()) {
            val height = (
                if (DeviceUtils.isInMultiWindow(activity)) {
                    requireView()
                } else {
                    activity.window.decorView
                }
                ).height
            // Height is zero when the view is not yet displayed like after orientation change
            if (height != 0) {
                setHeightThumbnail(height, metrics)
            } else {
                requireView().viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        } else {
            val height = (
                if (isPortrait) {
                    metrics.widthPixels / (16.0f / 9.0f)
                } else {
                    metrics.heightPixels / 2.0f
                }
                ).toInt()
            setHeightThumbnail(height, metrics)
        }
    }

    private fun setHeightThumbnail(newHeight: Int, metrics: DisplayMetrics) {
        val b = binding ?: return
        b.detailThumbnailImageView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            newHeight
        )
        b.detailThumbnailImageView.minimumHeight = newHeight
        if (isPlayerAvailable()) {
            val maxHeight = (metrics.heightPixels * MAX_PLAYER_HEIGHT).toInt()
            player!!.UIs().get(VideoPlayerUi::class.java).ifPresent { ui ->
                ui.views.surfaceView.setHeights(
                    newHeight,
                    if (ui.isFullscreen) newHeight else maxHeight
                )
            }
        }
    }

    private fun showContent() {
        binding?.detailContentRootHiding?.visibility = View.VISIBLE
        headerState.update { it.copy(contentVisible = true) }
    }

    protected fun setInitialData(
        newServiceId: Int,
        newUrl: String?,
        newTitle: String,
        newPlayQueue: PlayQueue?
    ) {
        this.serviceId = newServiceId
        this.url = newUrl
        this.title = newTitle
        this.playQueue = newPlayQueue
    }

    private fun setErrorImage(imageResource: Int) {
        val b = binding ?: return
        if (activity == null) {
            return
        }

        b.detailThumbnailImageView.setImageDrawable(
            AppCompatResources.getDrawable(requireContext(), imageResource)
        )
        b.detailThumbnailImageView.animate(false, 0, execOnEnd = {
            b.detailThumbnailImageView.animate(true, YpViewMotion.LONG)
        })
    }

    override fun handleError() {
        super.handleError()
        setErrorImage(R.drawable.not_available_monkey)

        val b = binding ?: return
        if (b.relatedItemsLayout != null) { // hide related streams for tablets
            b.relatedItemsLayout.visibility = View.INVISIBLE
        }

        // hide comments / related streams / description tabs
        b.viewPager.visibility = View.GONE
        b.tabLayout.visibility = View.GONE
    }

    private fun hideAgeRestrictedContent() {
        showTextError(
            getString(
                R.string.restricted_video,
                getString(R.string.show_age_restricted_content_title)
            )
        )
    }

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_SHOW_MAIN_PLAYER ->
                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED

                    ACTION_HIDE_MAIN_PLAYER ->
                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN

                    ACTION_PLAYER_STARTED -> {
                        // If the state is not hidden we don't need to show the mini player
                        if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                        // Rebound to the service if it was closed via notification or mini player
                        if (!playerHolder.isBound) {
                            playerHolder.startService(false, this@YpVideoDetailFragment)
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER)
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER)
        intentFilter.addAction(ACTION_PLAYER_STARTED)
        ContextCompat.registerReceiver(
            activity,
            broadcastReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    ////////////////////////////////////////////////////////////////////////// */

    private fun restoreDefaultOrientation() {
        if (isPlayerAvailable() && player!!.videoPlayerSelected()) {
            toggleFullscreenIfInFullscreenMode()
        }

        // This will show systemUI and pause the player.
        // User can tap on Play button and video will be in fullscreen mode again
        // Note for tablet: trying to avoid orientation changes since it's not easy
        // to physically rotate the tablet every time
        if (activity != null && !DeviceUtils.isTablet(activity)) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */

    override fun showLoading() {
        super.showLoading()

        val b = binding ?: return

        // if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE isn't required
        if (!ExtractorHelper.isCached(serviceId, url.orEmpty(), InfoCache.Type.STREAM)) {
            b.detailContentRootHiding.visibility = View.INVISIBLE
        }

        b.detailThumbnailPlayButton.animate(false, YpViewMotion.EXTRA_FAST)
        b.detailDurationView.animate(false, YpViewMotion.EXTRA_FAST)
        b.detailPositionView.visibility = View.GONE
        b.positionView.visibility = View.GONE

        // the Compose header shows the known title as placeholder and hides the content
        headerState.update {
            it.copy(
                title = title,
                titleExpanded = false,
                secondaryVisible = false,
                contentVisible = false
            )
        }

        if (b.relatedItemsLayout != null) {
            if (showRelatedItems) {
                b.relatedItemsLayout.visibility =
                    if (isFullscreen()) View.GONE else View.INVISIBLE
            } else {
                b.relatedItemsLayout.visibility = View.GONE
            }
        }

        CoilUtils.dispose(b.detailThumbnailImageView)
        CoilUtils.dispose(b.overlayThumbnail)
        b.detailThumbnailImageView.setImageBitmap(null)
    }

    override fun handleResult(info: StreamInfo) {
        super.handleResult(info)
        val b = binding ?: return

        currentInfo = info
        setInitialData(info.serviceId, info.originalUrl, info.name, playQueue)

        updateTabs(info)

        b.detailThumbnailPlayButton.animate(true, YpViewMotion.NORMAL)

        updateHeaderWithInfo(info)

        if (info.duration > 0) {
            b.detailDurationView.text = Localization.getDurationString(info.duration)
            b.detailDurationView.setBackgroundResource(R.drawable.yp_badge_duration)
            b.detailDurationView.animate(true, YpViewMotion.EXTRA_FAST)
        } else if (info.streamType == StreamType.LIVE_STREAM) {
            b.detailDurationView.setText(R.string.duration_live)
            b.detailDurationView.setBackgroundResource(R.drawable.yp_badge_live)
            b.detailDurationView.animate(true, YpViewMotion.EXTRA_FAST)
        } else {
            b.detailDurationView.visibility = View.GONE
        }

        checkUpdateProgressInfo(info)
        CoilHelper.loadDetailsThumbnail(b.detailThumbnailImageView, info.thumbnails)
        showMetaInfoInTextView(
            info.metaInfo,
            b.detailMetaInfoTextView,
            b.detailMetaInfoSeparator,
            disposables
        )

        setupSubscribeButton(info)
    }

    /** YewPlay: fills the Compose header with the loaded stream info. */
    private fun updateHeaderWithInfo(info: StreamInfo) {
        // counts
        val viewCountText: String? = if (info.viewCount >= 0) {
            when {
                info.streamType == StreamType.AUDIO_LIVE_STREAM ->
                    Localization.listeningCount(activity, info.viewCount)

                info.streamType == StreamType.LIVE_STREAM ->
                    Localization.localizeWatchingCount(activity, info.viewCount)

                else -> Localization.localizeViewCount(activity, info.viewCount)
            }
        } else {
            null
        }

        val ratingsDisabled = info.dislikeCount == -1L && info.likeCount == -1L
        val likeCountText: String? = if (!ratingsDisabled && info.likeCount >= 0) {
            Localization.shortCount(activity, info.likeCount)
        } else {
            null
        }
        val dislikeCountText: String? = if (!ratingsDisabled && info.dislikeCount >= 0) {
            Localization.shortCount(activity, info.dislikeCount)
        } else {
            null
        }

        headerState.update { state ->
            var next = state.copy(
                title = info.name,
                titleExpanded = false,
                secondaryVisible = false,
                viewCountText = viewCountText,
                likeCountText = likeCountText,
                dislikeCountText = dislikeCountText,
                ratingsDisabled = ratingsDisabled
            )
            next = if (!info.subChannelName.isNullOrEmpty()) {
                // both uploader and sub channel (e.g. a channel inside a collective)
                val subText = StringBuilder()
                if (!info.uploaderName.isNullOrEmpty()) {
                    subText.append(getString(R.string.video_detail_by, info.uploaderName))
                }
                if (info.uploaderSubscriberCount > -1) {
                    if (subText.isNotEmpty()) {
                        subText.append(Localization.DOT_SEPARATOR)
                    }
                    subText.append(
                        Localization.shortSubscriberCount(activity, info.uploaderSubscriberCount)
                    )
                }
                next.copy(
                    subChannelName = info.subChannelName,
                    subChannelAvatarUrl =
                        ImageStrategy.choosePreferredImage(info.subChannelAvatars),
                    uploaderAvatarUrl = ImageStrategy.choosePreferredImage(info.uploaderAvatars),
                    byLine = subText.toString(),
                    showSubChannelAvatar = true,
                    showUploaderAvatar = true
                )
            } else {
                // the uploader is shown as the (only) channel
                next.copy(
                    subChannelName = info.uploaderName ?: "",
                    subChannelAvatarUrl = ImageStrategy.choosePreferredImage(info.uploaderAvatars),
                    uploaderAvatarUrl = null,
                    byLine = if (info.uploaderSubscriberCount > -1) {
                        Localization.shortSubscriberCount(activity, info.uploaderSubscriberCount)
                    } else {
                        ""
                    },
                    showSubChannelAvatar = true,
                    showUploaderAvatar = false
                )
            }
            next
        }
    }

    fun openDownloadDialog() {
        val info = currentInfo ?: return

        try {
            val downloadDialog = DownloadDialog(activity, info)
            downloadDialog.show(activity.supportFragmentManager, "downloadDialog")
        } catch (e: Exception) {
            ErrorUtil.showSnackbar(
                activity,
                ErrorInfo(e, UserAction.DOWNLOAD_OPEN_DIALOG, "Showing download dialog", info)
            )
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    ////////////////////////////////////////////////////////////////////////// */

    private fun checkUpdateProgressInfo(info: StreamInfo) {
        positionSubscriber?.dispose()
        val b = binding ?: return
        if (!getResumePlaybackEnabled(activity)) {
            b.positionView.visibility = View.GONE
            b.detailPositionView.visibility = View.GONE
            return
        }
        val recordManager = HistoryRecordManager(requireContext())
        positionSubscriber = recordManager.loadStreamState(info)
            .subscribeOn(Schedulers.io())
            .onErrorComplete()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { state ->
                    updatePlaybackProgress(state.progressMillis, info.duration * 1000)
                },
                { // impossible since the onErrorComplete()
                },
                {
                    binding?.let {
                        it.positionView.visibility = View.GONE
                        it.detailPositionView.visibility = View.GONE
                    }
                }
            )
    }

    private fun updatePlaybackProgress(progress: Long, duration: Long) {
        if (!getResumePlaybackEnabled(activity)) {
            return
        }
        val b = binding ?: return
        val progressSeconds = TimeUnit.MILLISECONDS.toSeconds(progress).toInt()
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(duration).toInt()
        // If the old and the new progress values have a big difference then use animation.
        // Otherwise don't because it affects CPU
        val progressDifference = Math.abs(b.positionView.progress - progressSeconds)
        b.positionView.max = durationSeconds
        if (progressDifference > 2) {
            b.positionView.setProgressAnimated(progressSeconds)
        } else {
            b.positionView.progress = progressSeconds
        }
        val position = Localization.getDurationString(progressSeconds.toLong())
        if (position != b.detailPositionView.text.toString()) {
            b.detailPositionView.text = position
        }
        if (b.positionView.visibility != View.VISIBLE) {
            b.positionView.animate(true, YpViewMotion.EXTRA_FAST)
            b.detailPositionView.animate(true, YpViewMotion.EXTRA_FAST)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    ////////////////////////////////////////////////////////////////////////// */

    override fun onViewCreated() {
        tryAddVideoPlayerView()
    }

    override fun onQueueUpdate(queue: PlayQueue) {
        playQueue = queue
        if (DEBUG) {
            Log.d(
                TAG,
                "onQueueUpdate() called with: serviceId = [" +
                    serviceId + "], url = [" + url + "], name = [" +
                    title + "], playQueue = [" + playQueue + "]"
            )
        }

        // Register broadcast receiver to listen to playQueue changes
        // and hide the overlayPlayQueueButton when the playQueue is empty / destroyed.
        queue.broadcastReceiver?.subscribe(
            { updateOverlayPlayQueueButtonVisibility() },
            { }
        )?.let { disposables.add(it) }

        // This should be the only place where we push data to stack.
        // It will allow to have live instance of PlayQueue with actual information about
        // deleted/added items inside Channel/Playlist queue and makes possible to have
        // a history of played items
        val stackPeek = stack.peek()
        if (stackPeek != null && !stackPeek.playQueue.equalStreams(queue)) {
            val playQueueItem = queue.item
            if (playQueueItem != null) {
                stack.push(
                    StackItem(
                        playQueueItem.serviceId,
                        playQueueItem.url,
                        playQueueItem.title,
                        queue
                    )
                )
                return
            } // else continue below
        }

        val stackWithQueue = findQueueInStack(queue)
        if (stackWithQueue != null) {
            // On every MainPlayer service's destroy() playQueue gets disposed and
            // no longer able to track progress. That's why we update our cached disposed
            // queue with the new one that is active and have the same history.
            // Without that the cached playQueue will have an old recovery position
            stackWithQueue.playQueue = queue
        }
    }

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters
    ) {
        setOverlayPlayPauseImage(player?.isPlaying == true)

        when (state) {
            Player.STATE_PLAYING -> {
                val b = binding ?: return
                if (b.positionView.alpha != 1.0f &&
                    player?.playQueue?.item != null &&
                    player!!.playQueue?.item!!.url == url
                ) {
                    b.positionView.animate(true, YpViewMotion.EXTRA_FAST)
                    b.detailPositionView.animate(true, YpViewMotion.EXTRA_FAST)
                }
            }
        }
    }

    override fun onProgressUpdate(
        currentProgress: Int,
        duration: Int,
        bufferPercent: Int
    ) {
        // Progress updates every second even if media is paused. It's useless until playing
        val p = player
        if (p == null || !p.isPlaying || playQueue == null) {
            return
        }

        if (p.playQueue?.item?.url == url) {
            updatePlaybackProgress(currentProgress.toLong(), duration.toLong())
        }
    }

    override fun onMetadataUpdate(info: StreamInfo, queue: PlayQueue) {
        val item = findQueueInStack(queue)
        if (item != null) {
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue)
            // every new played stream gives new title and url.
            // StackItem contains information about first played stream. Let's update it here
            item.title = info.name
            item.url = info.url
        }
        // They are not equal when user watches something in popup while browsing in fragment and
        // then changes screen orientation. In that case the fragment will set itself as
        // a service listener and will receive initial call to onMetadataUpdate()
        if (!queue.equalStreams(playQueue)) {
            return
        }

        updateOverlayData(info.name, info.uploaderName, info.thumbnails)
        if (currentInfo != null && info.url == currentInfo!!.url) {
            return
        }

        currentInfo = info
        setInitialData(info.serviceId, info.url, info.name, queue)
        setAutoPlay(false)
        // Delay execution just because it freezes the main thread, and while playing
        // next/previous video you see visual glitches
        // (when non-vertical video goes after vertical video)
        prepareAndHandleInfoIfNeededAfterDelay(info, true, 200)
    }

    override fun onPlayerError(error: PlaybackException, isCatchableException: Boolean) {
        if (!isCatchableException) {
            // Properly exit from fullscreen
            toggleFullscreenIfInFullscreenMode()
            hideMainPlayerOnLoadingNewStream()
        }
    }

    override fun onServiceStopped() {
        // the binding could be null at this point, if the app is finishing
        if (binding != null) {
            setOverlayPlayPauseImage(false)
            currentInfo?.let {
                updateOverlayData(it.name, it.uploaderName, it.thumbnails)
            }
            updateOverlayPlayQueueButtonVisibility()
        }
    }

    override fun onFullscreenStateChanged(fullscreen: Boolean) {
        setupBrightness()
        val b = binding ?: return
        if (!isPlayerAndPlayerServiceAvailable() ||
            player!!.UIs().get(MainPlayerUi::class.java).isEmpty ||
            getRoot().map { it.parent }.isEmpty
        ) {
            return
        }

        if (fullscreen) {
            hideSystemUiIfNeeded()
            b.overlayPlayPauseButton.requestFocus()
        } else {
            showSystemUi()
        }

        if (b.relatedItemsLayout != null) {
            if (showRelatedItems) {
                b.relatedItemsLayout.visibility =
                    if (fullscreen) View.GONE else View.VISIBLE
            } else {
                b.relatedItemsLayout.visibility = View.GONE
            }
        }
        scrollToTop()

        tryAddVideoPlayerView()
    }

    override fun onScreenRotationButtonClicked() {
        // On Android TV screen rotation is not supported
        // In tablet user experience will be better if screen will not be rotated
        // from landscape to portrait every time.
        // Just turn on fullscreen mode in landscape orientation
        // or portrait & unlocked global orientation
        val isLandscape = DeviceUtils.isLandscape(requireContext())
        if (DeviceUtils.isTv(activity) || DeviceUtils.isTablet(activity) &&
            (!globalScreenOrientationLocked(activity) || isLandscape)
        ) {
            player!!.UIs().get(MainPlayerUi::class.java)
                .ifPresent(MainPlayerUi::toggleFullscreen)
            return
        }

        val newOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        activity.requestedOrientation = newOrientation
    }

    /*
     * Will scroll down to description view after long click on moreOptionsButton
     * */
    override fun onMoreOptionsLongClicked() {
        val b = binding ?: return
        val params = b.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as AppBarLayout.Behavior
        val valueAnimator = ValueAnimator.ofInt(0, -b.playerPlaceholder.height)
        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.addUpdateListener { animation ->
            behavior.topAndBottomOffset = animation.animatedValue as Int
            b.appBarLayout.requestLayout()
        }
        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.duration = 500
        valueAnimator.start()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    ////////////////////////////////////////////////////////////////////////// */

    private fun showSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "showSystemUi() called")
        }

        if (activity == null) {
            return
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity.window.decorView.systemUiVisibility = 0
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.window.statusBarColor = ThemeHelper.resolveColorFromAttr(
            requireContext(),
            android.R.attr.colorPrimary
        )
    }

    private fun hideSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "hideSystemUi() called")
        }

        if (activity == null) {
            return
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        var visibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        // In multiWindow mode status bar is not transparent for devices with cutout
        // if I include this flag. So without it is better in this case
        val isInMultiWindow = DeviceUtils.isInMultiWindow(activity)
        if (!isInMultiWindow) {
            visibility = visibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        activity.window.decorView.systemUiVisibility = visibility

        if (isInMultiWindow || isFullscreen()) {
            activity.window.statusBarColor = Color.TRANSPARENT
            activity.window.navigationBarColor = Color.TRANSPARENT
        }
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    // Listener implementation
    override fun hideSystemUiIfNeeded() {
        if (isFullscreen() &&
            bottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED
        ) {
            hideSystemUi()
        }
    }

    private fun isFullscreen(): Boolean {
        return isPlayerAvailable() && player!!.UIs().get(VideoPlayerUi::class.java)
            .map(VideoPlayerUi::isFullscreen).orElse(false)
    }

    private fun playerIsNotStopped(): Boolean {
        return isPlayerAvailable() && !player!!.isStopped
    }

    private fun restoreDefaultBrightness() {
        if (activity == null) {
            return
        }
        val lp = activity.window.attributes
        if (lp.screenBrightness == -1f) {
            return
        }

        lp.screenBrightness = -1f
        activity.window.attributes = lp
    }

    private fun setupBrightness() {
        if (activity == null) {
            return
        }

        val lp = activity.window.attributes
        if (!isFullscreen() || bottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            restoreDefaultBrightness()
        } else {
            if (PlayerHelper.getActionForRightGestureSide(activity) !=
                getString(R.string.brightness_control_key) &&
                PlayerHelper.getActionForLeftGestureSide(activity) !=
                getString(R.string.brightness_control_key)
            ) {
                return
            }
            val brightnessLevel = PlayerHelper.getScreenBrightness(activity)
            if (brightnessLevel == lp.screenBrightness) {
                return
            }
            lp.screenBrightness = brightnessLevel
            activity.window.attributes = lp
        }
    }

    private fun accommodateForTvAndDesktopMode() {
        val b = binding ?: return
        // the Compose action buttons have no background to tint; on TV the focus
        // highlight comes from the Compose clickable itself
        if (DeviceUtils.isDesktopMode(requireContext())) {
            b.detailThumbnailRootLayout.foreground = null
        }
    }

    private fun checkLandscape() {
        val p = player ?: return
        if ((!p.isPlaying && p.playQueue !== playQueue) || p.playQueue == null) {
            setAutoPlay(true)
        }

        p.UIs().get(MainPlayerUi::class.java).ifPresent(MainPlayerUi::checkLandscape)
        if (globalScreenOrientationLocked(activity) && !p.isPlaying) {
            p.play()
        }
    }

    private fun wasCleared(): Boolean {
        return url == null
    }

    private fun findQueueInStack(queue: PlayQueue): StackItem? {
        var item: StackItem? = null
        val iterator = stack.descendingIterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.playQueue.equalStreams(queue)) {
                item = next
                break
            }
        }
        return item
    }

    private fun replaceQueueIfUserConfirms(onAllow: Runnable) {
        val activeQueue = if (isPlayerAvailable()) player!!.playQueue else null

        if (isClearingQueueConfirmationRequired(activity) &&
            playerIsNotStopped() &&
            activeQueue != null &&
            !activeQueue.equalStreams(playQueue)
        ) {
            showClearingQueueConfirmation(onAllow)
        } else {
            onAllow.run()
        }
    }

    private fun showClearingQueueConfirmation(onAllow: Runnable) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.clear_queue_confirmation_description)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                onAllow.run()
                dialog.dismiss()
            }
            .show()
    }

    private fun showExternalVideoPlaybackDialog() {
        val info = currentInfo ?: return

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.select_quality_external_players)
        builder.setNeutralButton(R.string.open_in_browser) { _, _ ->
            ShareUtils.openUrlInBrowser(requireActivity(), url)
        }

        val videoStreamsForExternalPlayers = ListHelper.getSortedStreamVideosList(
            activity,
            getUrlAndNonTorrentStreams(info.videoStreams),
            getUrlAndNonTorrentStreams(info.videoOnlyStreams),
            false,
            false
        )

        if (videoStreamsForExternalPlayers.isEmpty()) {
            builder.setMessage(R.string.no_video_streams_available_for_external_players)
            builder.setPositiveButton(R.string.ok, null)
        } else {
            val selectedVideoStreamIndexForExternalPlayers =
                ListHelper.getDefaultResolutionIndex(activity, videoStreamsForExternalPlayers)
            val resolutions = videoStreamsForExternalPlayers
                .map(VideoStream::getResolution)
                .toTypedArray()

            builder.setSingleChoiceItems(
                resolutions,
                selectedVideoStreamIndexForExternalPlayers,
                null
            )
            builder.setNegativeButton(R.string.cancel, null)
            builder.setPositiveButton(R.string.ok) { dialog, _ ->
                val index = (dialog as AlertDialog).listView.checkedItemPosition
                startOnExternalPlayer(activity, info, videoStreamsForExternalPlayers[index])
            }
        }
        builder.show()
    }

    private fun showExternalAudioPlaybackDialog() {
        val info = currentInfo ?: return

        val audioStreams = getUrlAndNonTorrentStreams(info.audioStreams)
        val audioTracks = ListHelper.getFilteredAudioStreams(activity, audioStreams)

        if (audioTracks.isEmpty()) {
            Toast.makeText(
                activity,
                R.string.no_audio_streams_available_for_external_players,
                Toast.LENGTH_SHORT
            ).show()
        } else if (audioTracks.size == 1) {
            startOnExternalPlayer(activity, info, audioTracks[0])
        } else {
            val selectedAudioStream = ListHelper.getDefaultAudioFormat(activity, audioTracks)
            val trackNames = audioTracks
                .map { audioStream -> Localization.audioTrackName(activity, audioStream) }
                .toTypedArray()

            AlertDialog.Builder(activity)
                .setTitle(R.string.select_audio_track_external_players)
                .setNeutralButton(R.string.open_in_browser) { _, _ ->
                    ShareUtils.openUrlInBrowser(requireActivity(), url)
                }
                .setSingleChoiceItems(trackNames, selectedAudioStream, null)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    val index = (dialog as AlertDialog).listView.checkedItemPosition
                    startOnExternalPlayer(activity, info, audioTracks[index])
                }
                .show()
        }
    }

    private fun cleanUp() {
        stack.clear()
        currentWorker?.dispose()
        playerHolder.stopService()
        setInitialData(0, null, "", null)
        currentInfo = null
        updateOverlayData(null, null, listOf())
    }

    private fun moveFocusToMainFragment(toMain: Boolean) {
        setupBrightness()
        val mainFragment = requireActivity().findViewById<ViewGroup>(R.id.fragment_holder)
        val toolbar = requireActivity().findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val afterDescendants = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val blockDescendants = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val b = binding ?: return
        if (toMain) {
            mainFragment.descendantFocusability = afterDescendants
            toolbar.descendantFocusability = afterDescendants
            (requireView() as ViewGroup).descendantFocusability = blockDescendants
            if (!mainFragment.hasFocus() && !toolbar.hasFocus()) {
                mainFragment.requestFocus()
            }
        } else {
            mainFragment.descendantFocusability = blockDescendants
            toolbar.descendantFocusability = blockDescendants
            (requireView() as ViewGroup).descendantFocusability = afterDescendants
            if (!b.root.hasFocus()) {
                b.detailThumbnailRootLayout.requestFocus()
            }
        }
    }

    private fun manageSpaceAtTheBottom(showMore: Boolean) {
        val peekHeight = getMiniPlayerPeekHeight()
        val holder = requireActivity().findViewById<View>(R.id.fragment_holder)
        val newBottomPadding = if (showMore) {
            resources.getDimensionPixelSize(R.dimen.yp_bottom_bar_space)
        } else {
            peekHeight
        }
        if (holder.paddingBottom == newBottomPadding) {
            return
        }
        holder.setPadding(
            holder.paddingLeft,
            holder.paddingTop,
            holder.paddingRight,
            newBottomPadding
        )
    }

    private fun setupSubscribeButton(info: StreamInfo) {
        subscriptionDisposable?.dispose()
        subscriptionDisposable = null
        subscribeClickListener = null
        if (info.uploaderUrl.isNullOrEmpty()) {
            headerState.update { it.copy(subscribeVisible = false) }
            return
        }
        if (subscriptionManager == null) {
            subscriptionManager = SubscriptionManager(requireContext())
        }
        subscriptionDisposable = subscriptionManager!!.subscriptionTable()
            .getSubscriptionFlowable(info.serviceId, info.uploaderUrl)
            .map { it.isEmpty() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { notSubscribed ->
                    headerState.update {
                        it.copy(subscribeVisible = true, subscribed = !notSubscribed)
                    }
                    subscribeClickListener = Runnable {
                        toggleSubscription(info, notSubscribed)
                    }
                },
                {
                    headerState.update { s -> s.copy(subscribeVisible = false) }
                }
            )
    }

    private fun toggleSubscription(info: StreamInfo, subscribe: Boolean) {
        val manager = subscriptionManager ?: return
        val action: Completable = if (subscribe) {
            val entity = SubscriptionEntity()
            entity.serviceId = info.serviceId
            entity.url = info.uploaderUrl
            entity.name = info.uploaderName
            entity.avatarUrl = ImageStrategy.imageListToDbUrl(info.uploaderAvatars)
            if (info.uploaderSubscriberCount > -1) {
                entity.subscriberCount = info.uploaderSubscriberCount
            }
            Completable.fromAction { manager.insertSubscription(entity) }
        } else {
            manager.deleteSubscription(info.serviceId, info.uploaderUrl)
        }
        disposables.add(
            action
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { },
                    { e ->
                        ErrorUtil.showUiErrorSnackbar(
                            this,
                            "Updating subscription from the video page",
                            e
                        )
                    }
                )
        )
    }

    private fun likeCurrentStream() {
        val info = currentInfo ?: return
        // YewPlay: without an account a like cannot reach the service, so tapping the like
        // count saves the video in the local "Liked" playlist instead
        val context = requireContext()
        val manager = LocalPlaylistManager(NewPipeDatabase.getInstance(context))
        val likedName = getString(R.string.yp_liked_playlist_name)
        val streams = listOf(StreamEntity(info))
        disposables.add(
            manager.getPlaylistDuplicates(info.url)
                .firstElement()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { entries ->
                        val liked = entries.firstOrNull { it.orderingName == likedName }
                        if (liked != null && liked.timesStreamIsContained > 0) {
                            Toast.makeText(
                                context,
                                R.string.yp_liked_already,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@subscribe
                        }
                        val save: Maybe<List<Long>> = if (liked == null) {
                            manager.createPlaylist(likedName, streams)
                        } else {
                            manager.appendToPlaylist(liked.uid, streams)
                        }
                        disposables.add(
                            save
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                    {
                                        Toast.makeText(
                                            context,
                                            R.string.yp_liked_added,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    { e ->
                                        ErrorUtil.showUiErrorSnackbar(
                                            this,
                                            "Saving liked video",
                                            e
                                        )
                                    }
                                )
                        )
                    },
                    { e ->
                        ErrorUtil.showUiErrorSnackbar(this, "Loading liked playlist", e)
                    }
                )
        )
    }

    private fun getMiniPlayerPeekHeight(): Int {
        return resources.getDimensionPixelSize(R.dimen.mini_player_height) +
            resources.getDimensionPixelSize(R.dimen.yp_bottom_bar_space)
    }

    private fun setupBottomPlayer() {
        val b = binding ?: return
        val params = b.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as AppBarLayout.Behavior?

        val bottomSheetLayout =
            activity.findViewById<FrameLayout>(R.id.fragment_player_holder)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        bottomSheetBehavior!!.state = lastStableBottomSheetState
        updateBottomSheetState(lastStableBottomSheetState)

        val peekHeight = getMiniPlayerPeekHeight()
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            manageSpaceAtTheBottom(false)
            bottomSheetBehavior!!.peekHeight = peekHeight
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                b.overlayLayout.alpha = MAX_OVERLAY_ALPHA
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                b.overlayLayout.alpha = 0f
                setOverlayElementsClickable(false)
            }
        }

        bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateBottomSheetState(newState)

                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        moveFocusToMainFragment(true)
                        manageSpaceAtTheBottom(true)

                        bottomSheetBehavior!!.peekHeight = 0
                        cleanUp()
                    }

                    BottomSheetBehavior.STATE_EXPANDED -> {
                        moveFocusToMainFragment(false)
                        manageSpaceAtTheBottom(false)

                        bottomSheetBehavior!!.peekHeight = peekHeight
                        setOverlayElementsClickable(false)
                        hideSystemUiIfNeeded()
                        if (DeviceUtils.isLandscape(requireContext()) &&
                            isPlayerAvailable() &&
                            player!!.isPlaying &&
                            !isFullscreen() &&
                            !DeviceUtils.isTablet(activity)
                        ) {
                            player!!.UIs().get(MainPlayerUi::class.java)
                                .ifPresent(MainPlayerUi::toggleFullscreen)
                        }
                        setOverlayLook(b.appBarLayout, behavior, 1f)
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        moveFocusToMainFragment(true)
                        manageSpaceAtTheBottom(false)

                        bottomSheetBehavior!!.peekHeight = peekHeight

                        setOverlayElementsClickable(true)
                        if (isPlayerAvailable()) {
                            player!!.UIs().get(MainPlayerUi::class.java)
                                .ifPresent(MainPlayerUi::closeItemsList)
                        }
                        setOverlayLook(b.appBarLayout, behavior, 0f)
                    }

                    BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        if (isFullscreen()) {
                            showSystemUi()
                        }
                        if (isPlayerAvailable()) {
                            player!!.UIs().get(MainPlayerUi::class.java).ifPresent { ui ->
                                if (ui.isControlsVisible) {
                                    ui.hideControls(0, 0)
                                }
                            }
                        }
                    }

                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val binding = binding ?: return
                setOverlayLook(binding.appBarLayout, behavior, slideOffset)
            }
        }

        bottomSheetBehavior!!.addBottomSheetCallback(bottomSheetCallback!!)

        activity.supportFragmentManager.addOnBackStackChangedListener {
            if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun updateOverlayPlayQueueButtonVisibility() {
        val isPlayQueueEmpty =
            player == null || // no player => no play queue :)
                player!!.playQueue == null ||
                player!!.playQueue?.isEmpty ?: true
        binding?.overlayPlayQueueButton?.visibility =
            if (isPlayQueueEmpty) View.GONE else View.VISIBLE
    }

    private fun updateOverlayData(
        overlayTitle: String?,
        uploader: String?,
        thumbnails: List<Image>
    ) {
        val b = binding ?: return
        b.overlayTitleTextView.text = if (overlayTitle.isNullOrEmpty()) "" else overlayTitle
        b.overlayChannelTextView.text = if (uploader.isNullOrEmpty()) "" else uploader
        b.overlayThumbnail.setImageDrawable(null)
        CoilHelper.loadDetailsThumbnail(b.overlayThumbnail, thumbnails)
    }

    private fun setOverlayPlayPauseImage(playerIsPlaying: Boolean) {
        val drawable = if (playerIsPlaying) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play_arrow
        }
        binding?.overlayPlayPauseButton?.setImageResource(drawable)
    }

    private fun setOverlayLook(
        appBar: AppBarLayout,
        behavior: AppBarLayout.Behavior?,
        slideOffset: Float
    ) {
        val b = binding ?: return
        // SlideOffset < 0 when mini player is about to close via swipe.
        // Stop animation in this case
        if (behavior == null || slideOffset < 0) {
            return
        }
        b.overlayLayout.alpha = Math.min(MAX_OVERLAY_ALPHA, 1 - slideOffset)
        // These numbers are not special. They just do a cool transition
        behavior.topAndBottomOffset =
            (-b.detailThumbnailImageView.height * 2 * (1 - slideOffset) / 3).toInt()
        appBar.requestLayout()
    }

    private fun setOverlayElementsClickable(enable: Boolean) {
        val b = binding ?: return
        b.overlayThumbnail.isClickable = enable
        b.overlayThumbnail.isLongClickable = enable
        b.overlayMetadataLayout.isClickable = enable
        b.overlayMetadataLayout.isLongClickable = enable
        b.overlayButtonsLayout.isClickable = enable
        b.overlayPlayQueueButton.isClickable = enable
        b.overlayPlayPauseButton.isClickable = enable
        b.overlayCloseButton.isClickable = enable
    }

    // helpers to check the state of player and playerService
    fun isPlayerAvailable(): Boolean {
        return player != null
    }

    fun isPlayerServiceAvailable(): Boolean {
        return playerService != null
    }

    fun isPlayerAndPlayerServiceAvailable(): Boolean {
        return player != null && playerService != null
    }

    fun getRoot(): Optional<View> {
        return Optional.ofNullable(player)
            .flatMap { p -> p.UIs().get(VideoPlayerUi::class.java) }
            .map { playerUi -> playerUi.views.root }
    }

    private fun updateBottomSheetState(newState: Int) {
        bottomSheetState = newState
        if (newState != BottomSheetBehavior.STATE_DRAGGING &&
            newState != BottomSheetBehavior.STATE_SETTLING
        ) {
            lastStableBottomSheetState = newState
        }
    }
}
