package org.schabi.newpipe.player.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.media3.common.VideoSize
import androidx.media3.ui.SubtitleView
import java.util.Collections
import java.util.Optional
import java.util.WeakHashMap
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.fragments.detail.YpVideoDetailFragment
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.YpQueueItemAction
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.MainPlayerGestureListener
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND
import org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP
import org.schabi.newpipe.player.mapPlayQueueEntries
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE
import org.schabi.newpipe.player.playQueueTimeText
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils

class MainPlayerUi(player: Player, views: YpPlayerViews) :
    VideoPlayerUi(player, views), View.OnLayoutChangeListener {

    override var isFullscreen: Boolean = false
        private set
    var isVerticalVideo: Boolean = false
        private set
    private var fragmentIsVisible = false

    private var settingsContentObserver: ContentObserver? = null

    private val queueItemKeys = WeakHashMap<PlayQueueItem, String>()
    private var nextQueueItemKey = 0
    private var lastProgressMillis = 0

    private val isQueueVisible: Boolean
        get() = chromeState.value.panelMode == YpPanelMode.QUEUE
    private val areSegmentsVisible: Boolean
        get() = chromeState.value.panelMode == YpPanelMode.SEGMENTS

    private fun keyFor(item: PlayQueueItem): String = queueItemKeys.getOrPut(item) { "q${nextQueueItemKey++}" }

    // ///////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    // ///////////////////////////////////////////////////////////

    /**
     * Open fullscreen on tablets where the option to have the main player start automatically in
     * fullscreen mode is on. Rotating the device to landscape is already done in [YpVideoDetailFragment.openVideoPlayer]
     * when the thumbnail is clicked, and that's enough for phones, but not for tablets since the
     * mini player can be also shown in landscape.
     */
    private fun directlyOpenFullscreenIfNeeded() {
        if (PlayerHelper.isStartMainPlayerFullscreenEnabled(player.service) &&
            DeviceUtils.isTablet(player.service) &&
            PlayerHelper.globalScreenOrientationLocked(player.service)
        ) {
            player.fragmentListener
                .ifPresent(PlayerServiceEventListener::onScreenRotationButtonClicked)
        }
    }

    override fun setupAfterIntent() {
        // needed for tablets, check the function for a better explanation
        directlyOpenFullscreenIfNeeded()

        super.setupAfterIntent()

        initVideoPlayer()

        // note: this is for automatically playing (when "Resume playback" is off), see #6179
        if (player.playWhenReady) {
            player.play()
        } else {
            player.pause()
        }
    }

    override fun buildGestureListener(): BasePlayerGestureListener {
        return MainPlayerGestureListener(this)
    }

    override fun buildActions(): YpChromeActions {
        return super.buildActions().let { base ->
            YpChromeActions(
                onPlayPause = base.onPlayPause,
                onPrevious = base.onPrevious,
                onNext = base.onNext,
                onSeekStart = base.onSeekStart,
                onSeekPreview = base.onSeekPreview,
                onSeekEnd = base.onSeekEnd,
                onSeekCancel = base.onSeekCancel,
                onLiveSync = base.onLiveSync,
                onQualitySelected = base.onQualitySelected,
                onAudioTrackSelected = base.onAudioTrackSelected,
                onCaptionSelected = base.onCaptionSelected,
                onSpeedSelected = base.onSpeedSelected,
                onPlaybackSpeedDialog = base.onPlaybackSpeedDialog,
                onResize = base.onResize,
                onRepeat = base.onRepeat,
                onShuffle = base.onShuffle,
                onClose = base.onClose,
                onMoreOptions = base.onMoreOptions,
                onMoreOptionsLong = ::onMoreOptionsLongClickedMain,
                onShare = base.onShare,
                onShareLong = base.onShareLong,
                onMute = base.onMute,
                onKodi = base.onKodi,
                onOpenInBrowser = base.onOpenInBrowser,
                onFullScreen = base.onFullScreen,
                onScreenRotation = {
                    // only if it's not a vertical video or vertical video but in landscape with
                    // locked orientation a screen orientation can be changed automatically
                    if (!isVerticalVideo ||
                        (isLandscape() && PlayerHelper.globalScreenOrientationLocked(context))
                    ) {
                        player.fragmentListener.ifPresent(
                            PlayerServiceEventListener::onScreenRotationButtonClicked
                        )
                    } else {
                        toggleFullscreen()
                    }
                    // hide controls in fullscreen immediately
                    afterChromeClick(isFullscreen)
                },
                onQueueClicked = ::onQueueClicked,
                onSegmentsClicked = ::onSegmentsClicked,
                onPanelClose = ::closeItemsList,
                onAddToPlaylist = {
                    parentActivity.ifPresent { activity ->
                        PlaylistDialog.showForPlayQueue(player, activity.supportFragmentManager)
                    }
                },
                onQueueSelect = { index ->
                    player.playQueue?.streams?.getOrNull(index)?.let(player::selectQueueItem)
                },
                onQueueItemAction = ::onQueueItemAction,
                onQueueMove = { from, to -> player.playQueue?.move(from, to) },
                onQueueMoveFinished = { player.playQueue?.let(::refreshQueueState) },
                onQueueEndReached = {
                    player.playQueue?.let { queue ->
                        if (!queue.isComplete) {
                            queue.fetch()
                        }
                    }
                },
                onSegmentClick = ::onSegmentClick,
                onSegmentLongClick = ::onSegmentLongClick,
                onAnyClick = base.onAnyClick
            )
        }
    }

    private fun onMoreOptionsLongClickedMain() {
        player.fragmentListener
            .ifPresent(PlayerServiceEventListener::onMoreOptionsLongClicked)
        hideControls(0, 0)
        hideSystemUIIfNeeded()
    }

    override fun setupElementsVisibility() {
        super.setupElementsVisibility()
        closeItemsList()
        showHideKodiButton()
        setupScreenRotationButton()
        chromeState.update {
            it.copy(
                popupMode = false,
                showFullScreen = false,
                showResize = true,
                showShare = true,
                showOpenInBrowser = true,
                showMute = true,
                fullscreen = isFullscreen,
                metadataVisible = isFullscreen,
                closeVisible = !isFullscreen
            )
        }
        settingsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                setupScreenRotationButton()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            settingsContentObserver!!
        )
        views.root.addOnLayoutChangeListener(this)
    }

    override fun initPlayback() {
        super.initPlayback()
        player.playQueue?.let(::refreshQueueState)

        // make sure video and text tracks are enabled if the user is in the app, in the case
        // the user switched from background player to main player
        player.useVideoAndSubtitles(fragmentIsVisible)
    }

    override fun removeViewFromParent() {
        // view was added to fragment
        val parent = views.root.parent
        if (parent is ViewGroup) {
            parent.removeView(views.root)
        }
    }

    override fun destroy() {
        super.destroy()

        // exit from fullscreen when the user closes the player via notification
        if (isFullscreen) {
            toggleFullscreen()
        }

        settingsContentObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        views.root.removeOnLayoutChangeListener(this)

        removeViewFromParent()
    }

    override fun smoothStopForImmediateReusing() {
        super.smoothStopForImmediateReusing()
        // Android TV will handle back button in case controls will be visible
        // (one more additional unneeded click while the player is hidden)
        hideControls(0, 0)
        closeItemsList()
    }

    private fun initVideoPlayer() {
        // restore last resize mode
        setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(player))
        views.root.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    // ///////////////////////////////////////////////////////////
    // Broadcast receiver
    // ///////////////////////////////////////////////////////////

    override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED ->
                // close it because when changing orientation from portrait (in fullscreen
                // mode) the size of the queue layout can be larger than the screen size
                closeItemsList()

            ACTION_PLAY_PAUSE ->
                // ensure that we have audio-only stream playing when a user started to play
                // from notification's play button from outside of the app
                if (!fragmentIsVisible) {
                    onFragmentStopped()
                }

            YpVideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED -> {
                fragmentIsVisible = false
                onFragmentStopped()
            }

            YpVideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED -> {
                // restore video source when the user returns to the fragment
                fragmentIsVisible = true
                player.useVideoAndSubtitles(true)

                // when a user returns from background, the system UI will always be shown
                // even if controls are invisible: hide it in that case
                if (!isControlsVisible) {
                    hideSystemUIIfNeeded()
                }
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Fragment binding
    // ///////////////////////////////////////////////////////////

    override fun onFragmentListenerSet() {
        super.onFragmentListenerSet()
        fragmentIsVisible = true
        player.fragmentListener.ifPresent(PlayerServiceEventListener::onViewCreated)
    }

    /**
     * This will be called when a user goes to another app/activity, turns off a screen.
     * We don't want to interrupt playback and don't want to see notification so the next
     * lines of code will enable audio-only playback only if needed.
     */
    private fun onFragmentStopped() {
        if (player.isPlaying || player.isLoading) {
            when (PlayerHelper.getMinimizeOnExitAction(context)) {
                MINIMIZE_ON_EXIT_MODE_BACKGROUND -> player.useVideoAndSubtitles(false)

                MINIMIZE_ON_EXIT_MODE_POPUP -> parentActivity.ifPresent { activity ->
                    player.playQueue?.let { queue ->
                        player.setRecovery()
                        NavigationHelper.playOnPopupPlayer(activity, queue, true)
                    }
                }

                else -> player.pause()
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Playback states
    // ///////////////////////////////////////////////////////////

    override fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
        super.onUpdateProgress(currentProgress, duration, bufferPercent)
        lastProgressMillis = currentProgress

        if (areSegmentsVisible) {
            chromeState.update {
                it.copy(
                    segments = selectSegmentAt(
                        it.segments,
                        getNearestStreamSegmentPosition(currentProgress.toLong())
                    )
                )
            }
        }
        if (isQueueVisible) {
            updateQueueTime(currentProgress)
        }
    }

    override fun onPlaying() {
        super.onPlaying()
        checkLandscape()
    }

    override fun onCompleted() {
        super.onCompleted()
        if (isFullscreen) {
            toggleFullscreen()
        }
    }

    // ///////////////////////////////////////////////////////////
    // Controls showing / hiding
    // ///////////////////////////////////////////////////////////

    override fun showOrHideButtons() {
        super.showOrHideButtons()
        val playQueue = player.playQueue ?: return

        val showQueue = playQueue.streams.isNotEmpty()
        val showSegment = !player.currentStreamInfo
            .map(StreamInfo::getStreamSegments)
            .map { obj: List<StreamSegment> -> obj.isEmpty() }
            .orElse(true)

        chromeState.update {
            it.copy(showQueue = showQueue, showSegments = showSegment)
        }
    }

    override fun showSystemUIPartially() {
        if (isFullscreen) {
            parentActivity.map(Activity::getWindow).ifPresent { window ->
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                val visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                window.decorView.systemUiVisibility = visibility
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    override fun hideSystemUIIfNeeded() {
        player.fragmentListener.ifPresent(PlayerServiceEventListener::hideSystemUiIfNeeded)
    }

    override fun calculateMaxEndScreenThumbnailHeight(bitmap: Bitmap): Float {
        val screenHeight = context.resources.displayMetrics.heightPixels

        if (DeviceUtils.isTv(context) && !isFullscreen) {
            val videoInfoHeight = DeviceUtils.dpToPx(DETAIL_ROOT_MINIMUM_HEIGHT, context) +
                DeviceUtils.spToPx(DETAIL_TITLE_TEXT_SIZE_TV, context)
            return minOf(bitmap.height.toFloat(), (screenHeight - videoInfoHeight).toFloat())
        } else if (DeviceUtils.isTablet(context) && isLandscape() && !isFullscreen) {
            val videoInfoHeight = DeviceUtils.dpToPx(DETAIL_ROOT_MINIMUM_HEIGHT, context) +
                DeviceUtils.spToPx(DETAIL_TITLE_TEXT_SIZE_TABLET, context)
            return minOf(bitmap.height.toFloat(), (screenHeight - videoInfoHeight).toFloat())
        } else {
            // fullscreen player: max height is the device height
            return minOf(bitmap.height.toFloat(), screenHeight.toFloat())
        }
    }

    private fun showHideKodiButton() {
        // show the kodi button if it supports the current service and is enabled in settings
        val queueItem = player.playQueue?.item
        chromeState.update {
            it.copy(
                showKodi = queueItem != null &&
                    KoreUtils.shouldShowPlayWithKodi(context, queueItem.serviceId)
            )
        }
    }

    override fun setupSubtitleView(captionScale: Float) {
        views.subtitleView.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale
        )
    }

    // ///////////////////////////////////////////////////////////
    // Gestures
    // ///////////////////////////////////////////////////////////

    override fun onLayoutChange(
        view: View,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
        ol: Int,
        ot: Int,
        or: Int,
        ob: Int
    ) {
        if (l != ol || t != ot || r != or || b != ob) {
            // use a smaller value to be consistent across screen orientations, and to make
            // usage easier; multiply by 3/4 to ensure the user does not need to move the
            // finger up to the screen border to reach the maximum volume/brightness
            val width = r - l
            val height = b - t
            val min = minOf(width, height)
            maxGestureLengthPx = (min * 0.75).toInt()
        }
    }

    // ///////////////////////////////////////////////////////////
    // Play queue, segments and streams
    // ///////////////////////////////////////////////////////////

    override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        showHideKodiButton()
        if (areSegmentsVisible) {
            if (setSegmentItems(info)) {
                val adapterPosition = getNearestStreamSegmentPosition(
                    player.exoPlayer?.currentPosition ?: 0L
                )
                chromeState.update { it.copy(segments = selectSegmentAt(it.segments, adapterPosition)) }
            } else {
                closeItemsList()
            }
        }
        player.playQueue?.let(::refreshQueueState)
    }

    override fun onPlayQueueEdited() {
        super.onPlayQueueEdited()
        showOrHideButtons()
        player.playQueue?.let(::refreshQueueState)
    }

    private fun refreshQueueState(queue: PlayQueue) {
        chromeState.update {
            it.copy(
                queueItems = mapPlayQueueEntries(queue, ::keyFor),
                queueIndex = queue.index,
                queueComplete = queue.isComplete,
                queueTimeText = playQueueTimeText(queue, lastProgressMillis) ?: ""
            )
        }
    }

    private fun onQueueClicked() {
        hideSystemUIIfNeeded()
        player.playQueue?.let(::refreshQueueState)
        updateQueueTime((player.exoPlayer?.currentPosition ?: 0L).toInt())
        chromeState.update { it.copy(panelMode = YpPanelMode.QUEUE) }
        hideControls(0, 0)
    }

    private fun setSegmentItems(info: StreamInfo): Boolean {
        val segments = info.streamSegments
        if (segments.isNullOrEmpty()) {
            return false
        }
        chromeState.update {
            it.copy(
                segments = segments.map { segment ->
                    YpSegmentEntry(segment.title, segment.startTimeSeconds, false)
                }
            )
        }
        return true
    }

    private fun selectSegmentAt(
        segments: List<YpSegmentEntry>,
        position: Int
    ): List<YpSegmentEntry> {
        return segments.mapIndexed { index, entry -> entry.copy(selected = index == position) }
    }

    private fun onSegmentsClicked() {
        hideSystemUIIfNeeded()
        player.currentStreamInfo.ifPresent { info ->
            if (setSegmentItems(info)) {
                val adapterPosition = getNearestStreamSegmentPosition(
                    player.exoPlayer?.currentPosition ?: 0L
                )
                chromeState.update {
                    it.copy(
                        panelMode = YpPanelMode.SEGMENTS,
                        segments = selectSegmentAt(it.segments, adapterPosition)
                    )
                }
            }
        }
        hideControls(0, 0)
    }

    fun closeItemsList() {
        if (chromeState.value.panelMode != YpPanelMode.NONE) {
            chromeState.update { it.copy(panelMode = YpPanelMode.NONE) }
        }
    }

    private fun onSegmentClick(seconds: Int) {
        player.seekTo(seconds * 1000L)
        player.triggerProgressUpdate()
    }

    private fun onSegmentLongClick(seconds: Int) {
        val currentMetadata = player.currentMetadata
        if (currentMetadata == null || currentMetadata.serviceId != YouTube.serviceId) {
            return
        }
        val currentItem = player.currentItem ?: return
        ShareUtils.shareText(
            context,
            currentItem.title,
            player.videoUrl + "&t=" + seconds,
            currentItem.thumbnails
        )
    }

    private fun getNearestStreamSegmentPosition(playbackPosition: Long): Int {
        val segments = player.currentStreamInfo
            .map(StreamInfo::getStreamSegments)
            .orElse(Collections.emptyList())

        var nearestPosition = 0
        for (segment in segments) {
            if (segment.startTimeSeconds * 1000L > playbackPosition) {
                break
            }
            nearestPosition++
        }
        return maxOf(0, nearestPosition - 1)
    }

    private fun updateQueueTime(currentTime: Int) {
        val playQueue = player.playQueue ?: return
        chromeState.update {
            it.copy(queueTimeText = playQueueTimeText(playQueue, currentTime) ?: "")
        }
    }

    override fun isAnyListViewOpen(): Boolean {
        return chromeState.value.panelMode != YpPanelMode.NONE
    }

    // ///////////////////////////////////////////////////////////
    // Queue item actions (the six legacy popup menu actions)
    // ///////////////////////////////////////////////////////////

    private fun onQueueItemAction(index: Int, action: YpQueueItemAction) {
        val queue = player.playQueue ?: return
        val item = queue.streams.getOrNull(index) ?: return
        val activity = parentActivity.orElse(null)
        when (action) {
            YpQueueItemAction.REMOVE -> {
                val itemIndex = queue.indexOf(item)
                if (itemIndex != -1) {
                    queue.remove(itemIndex)
                }
            }

            YpQueueItemAction.DETAILS -> NavigationHelper.openVideoDetail(
                context,
                item.serviceId,
                item.url,
                item.title,
                null,
                false
            )

            YpQueueItemAction.APPEND_PLAYLIST -> activity?.let {
                PlaylistDialog.createCorrespondingDialog(
                    it,
                    listOf(StreamEntity(item))
                ) { dialog ->
                    dialog.show(it.supportFragmentManager, "MainPlayerUi@append_playlist")
                }
            }

            YpQueueItemAction.CHANNEL_DETAILS -> activity?.let {
                org.schabi.newpipe.util.SparseItemUtil.fetchUploaderUrlIfSparse(
                    it,
                    item.serviceId,
                    item.url,
                    item.uploaderUrl
                ) { uploaderUrl ->
                    NavigationHelper.openChannelFragmentUsingIntent(
                        it,
                        item.serviceId,
                        uploaderUrl,
                        item.uploader
                    )
                }
            }

            YpQueueItemAction.SHARE -> ShareUtils.shareText(
                context,
                item.title,
                item.url,
                item.thumbnails
            )

            YpQueueItemAction.DOWNLOAD -> activity?.let {
                org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                    it,
                    item.serviceId,
                    item.url
                ) { info ->
                    org.schabi.newpipe.download.DownloadDialog(it, info)
                        .show(it.supportFragmentManager, "downloadDialog")
                }
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Click listeners
    // ///////////////////////////////////////////////////////////

    override fun onPlaybackSpeedClicked() {
        parentActivity.ifPresent { activity ->
            PlaybackParameterDialog.newInstance(
                player.playbackSpeed.toDouble(),
                player.playbackPitch.toDouble(),
                player.playbackSkipSilence,
                player::setPlaybackParameters
            ).show(activity.supportFragmentManager, null)
        }
    }

    override fun onKeyDown(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE && isFullscreen) {
            player.playPause()
            if (player.isPlaying) {
                hideControls(0, 0)
            }
            return true
        }
        return super.onKeyDown(keyCode)
    }

    // ///////////////////////////////////////////////////////////
    // Video size, orientation, fullscreen
    // ///////////////////////////////////////////////////////////

    private fun setupScreenRotationButton() {
        chromeState.update {
            it.copy(
                screenRotationVisible = PlayerHelper.globalScreenOrientationLocked(context) ||
                    isVerticalVideo || DeviceUtils.isTablet(context),
                screenRotationIsExit = isFullscreen
            )
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        isVerticalVideo = videoSize.width < videoSize.height

        if (PlayerHelper.globalScreenOrientationLocked(context) &&
            isFullscreen &&
            isLandscape() == isVerticalVideo &&
            !DeviceUtils.isTv(context) &&
            !DeviceUtils.isTablet(context)
        ) {
            // set correct orientation
            player.fragmentListener.ifPresent(
                PlayerServiceEventListener::onScreenRotationButtonClicked
            )
        }

        setupScreenRotationButton()
    }

    fun toggleFullscreen() {
        val fragmentListener = player.fragmentListener.orElse(null)
        if (fragmentListener == null || player.exoPlayerIsNull()) {
            return
        }

        isFullscreen = !isFullscreen
        if (isFullscreen) {
            // Android needs tens of milliseconds to send new insets but a user is able to see
            // how controls change their position: just hide the controls to hide this visual
            // inconsistency
            hideControls(0, 0)
        }
        fragmentListener.onFullscreenStateChanged(isFullscreen)

        chromeState.update {
            it.copy(
                fullscreen = isFullscreen,
                metadataVisible = isFullscreen,
                closeVisible = !isFullscreen
            )
        }
        setupScreenRotationButton()
    }

    fun checkLandscape() {
        // check if landscape is correct
        val videoInLandscapeButNotInFullscreen = isLandscape() &&
            !isFullscreen &&
            !player.isAudioOnly
        val notPaused = player.currentState != Player.STATE_COMPLETED &&
            player.currentState != Player.STATE_PAUSED

        if (videoInLandscapeButNotInFullscreen && notPaused && !DeviceUtils.isTablet(context)) {
            toggleFullscreen()
        }
    }

    // ///////////////////////////////////////////////////////////
    // Getters
    // ///////////////////////////////////////////////////////////

    private val parentContext: Optional<Context>
        get() = Optional.ofNullable(views.root.parent)
            .filter { it is ViewGroup }
            .map { (it as ViewGroup).context }

    val parentActivity: Optional<androidx.appcompat.app.AppCompatActivity>
        get() = parentContext
            .filter { it is androidx.appcompat.app.AppCompatActivity }
            .map { it as androidx.appcompat.app.AppCompatActivity }

    fun isLandscape(): Boolean {
        // DisplayMetrics from the activity context knows about the MultiWindow feature
        // while DisplayMetrics from the app context doesn't
        return DeviceUtils.isLandscape(parentContext.orElse(player.service))
    }

    companion object {
        // see the Javadoc of calculateMaxEndScreenThumbnailHeight for information
        private const val DETAIL_ROOT_MINIMUM_HEIGHT = 85 // dp
        private const val DETAIL_TITLE_TEXT_SIZE_TV = 16 // sp
        private const val DETAIL_TITLE_TEXT_SIZE_TABLET = 15 // sp
    }
}
