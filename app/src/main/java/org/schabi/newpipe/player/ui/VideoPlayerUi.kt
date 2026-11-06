package org.schabi.newpipe.player.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.BitmapCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.video.VideoSize
import io.github.yewplay.core.designsystem.YpTheme
import java.util.Collections
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.detail.YpVideoDetailFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.DisplayPortion
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playback.SurfaceHolderCallback
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay.PerformListener.FastSeekDirection

/**
 * Base class for the video player UIs (main and popup). The video surface, the subtitle view,
 * the end screen thumbnail and the fast-seek overlay remain views driven imperatively by the
 * player and the gesture listeners; every piece of playback chrome (top/center/bottom controls,
 * menus, indicators, queue and segments panels) is rendered in Compose by [YpPlayerChrome]
 * inside [YpPlayerViews.controlsView] and driven by [chromeState].
 */
abstract class VideoPlayerUi(
    player: Player,
    @JvmField val views: YpPlayerViews
) : PlayerUi(player) {

    val chromeState = MutableStateFlow(YpChromeState())
    protected val controlsVisibilityHandler = Handler(Looper.getMainLooper())
    private var surfaceHolderCallback: SurfaceHolderCallback? = null
    var surfaceIsSetup = false
        private set

    @Volatile
    var isSomePopupMenuVisible = false

    var gestureDetector: GestureDetector? = null
        private set
    var playerGestureListener: BasePlayerGestureListener? = null
        private set

    val seekbarPreviewThumbnailHolder = SeekbarPreviewThumbnailHolder()
    protected var currentChapters: List<StreamSegment> = Collections.emptyList()
    private var lastChapterForHaptic: StreamSegment? = null

    /** Maximum length in px of the volume/brightness gestures, set on layout changes. */
    var maxGestureLengthPx = 0

    init {
        setupSubtitleView()
        val listener = buildGestureListener()
        playerGestureListener = listener
        gestureDetector = GestureDetector(context, listener)
        views.root.setOnTouchListener(playerGestureListener)
        setupPlayerSeekOverlay()
        views.controlsView.setContent {
            YpTheme(
                themeKey = YpThemePrefs.themeKey(context),
                nightThemeKey = YpThemePrefs.nightThemeKey(context),
                // the chrome floats over the video surface: no opaque background here
                opaqueBackground = false
            ) {
                val state by chromeState.collectAsState()
                YpPlayerChrome(
                    state = state,
                    labels = buildLabels(),
                    icons = buildIcons(),
                    actions = buildActions()
                )
            }
        }
    }

    private fun buildLabels() = YpChromeLabels(
        play = context.getString(R.string.play),
        pause = context.getString(R.string.pause),
        replay = context.getString(R.string.replay),
        previousStream = context.getString(R.string.previous_stream),
        nextStream = context.getString(R.string.next_stream),
        close = context.getString(R.string.close),
        moreOptions = context.getString(R.string.more_options),
        repeat = context.getString(R.string.notification_action_repeat),
        shuffle = context.getString(R.string.notification_action_shuffle),
        live = context.getString(R.string.duration_live),
        queue = context.getString(R.string.open_play_queue),
        chapters = context.getString(R.string.chapters),
        addToPlaylist = context.getString(R.string.add_to_playlist),
        toggleFullscreen = context.getString(R.string.toggle_fullscreen),
        toggleOrientation = context.getString(R.string.toggle_screen_orientation),
        share = context.getString(R.string.share),
        mute = context.getString(R.string.mute),
        unmute = context.getString(R.string.unmute),
        kodi = context.getString(R.string.play_with_kodi_title),
        openInBrowser = context.getString(R.string.open_in_browser),
        remove = context.getString(R.string.play_queue_remove),
        streamDetails = context.getString(R.string.play_queue_stream_detail),
        channelDetails = context.getString(R.string.show_channel_details),
        download = context.getString(R.string.download),
        captionNone = context.getString(R.string.caption_none)
    )

    @androidx.compose.runtime.Composable
    private fun buildIcons(): YpChromeIcons {
        return YpChromeIcons(
            close = painterResource(R.drawable.ic_close),
            play = painterResource(R.drawable.ic_play_arrow),
            pause = painterResource(R.drawable.ic_pause),
            replay = painterResource(R.drawable.ic_replay),
            previous = painterResource(R.drawable.ic_previous),
            next = painterResource(R.drawable.ic_next),
            repeatOff = painterResource(com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_off),
            repeatOne = painterResource(com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_one),
            repeatAll = painterResource(com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_all),
            shuffle = painterResource(R.drawable.ic_shuffle),
            moreOptions = painterResource(R.drawable.ic_expand_more),
            fullScreen = painterResource(R.drawable.ic_fullscreen),
            fullScreenExit = painterResource(R.drawable.ic_fullscreen_exit),
            kodi = painterResource(R.drawable.ic_cast),
            browser = painterResource(R.drawable.ic_language),
            share = painterResource(R.drawable.ic_share),
            volumeOff = painterResource(R.drawable.ic_volume_off),
            volumeMute = painterResource(R.drawable.ic_volume_mute),
            volumeDown = painterResource(R.drawable.ic_volume_down),
            volumeUp = painterResource(R.drawable.ic_volume_up),
            brightnessLow = painterResource(R.drawable.ic_brightness_low),
            brightnessMedium = painterResource(R.drawable.ic_brightness_medium),
            brightnessHigh = painterResource(R.drawable.ic_brightness_high),
            queue = painterResource(R.drawable.ic_list),
            segments = painterResource(R.drawable.ic_menu_book),
            playlistAdd = painterResource(R.drawable.ic_playlist_add),
            drag = painterResource(R.drawable.ic_drag_handle)
        )
    }

    protected open fun buildActions() = YpChromeActions(
        onPlayPause = clickAction(hideImmediately = true) { player.playPause() },
        onPrevious = clickAction { player.playPrevious() },
        onNext = clickAction { player.playNext() },
        onSeekStart = ::onSeekStart,
        onSeekPreview = ::onSeekPreview,
        onSeekEnd = ::onSeekEnd,
        onSeekCancel = ::onSeekCancel,
        onLiveSync = clickAction { player.seekToDefault() },
        onQualitySelected = ::onQualitySelected,
        onAudioTrackSelected = ::onAudioTrackSelected,
        onCaptionSelected = ::onCaptionSelected,
        onSpeedSelected = ::onSpeedSelected,
        onPlaybackSpeedDialog = clickAction { onPlaybackSpeedClicked() },
        onResize = clickAction { onResizeClicked() },
        onRepeat = clickAction { player.cycleNextRepeatMode() },
        onShuffle = clickAction { player.toggleShuffleModeEnabled() },
        onClose = clickAction {
            // set package to this app's package to prevent the intent from being seen outside
            context.sendBroadcast(
                Intent(YpVideoDetailFragment.ACTION_HIDE_MAIN_PLAYER)
                    .setPackage(App.PACKAGE_NAME)
            )
        },
        onMoreOptions = ::onMoreOptionsClicked,
        onMoreOptionsLong = ::onMoreOptionsLongClicked,
        onShare = clickAction {
            player.currentItem?.let { item ->
                ShareUtils.shareText(
                    context,
                    item.title,
                    player.videoUrlAtCurrentTime,
                    item.thumbnails
                )
            }
        },
        onShareLong = {
            ShareUtils.copyToClipboard(context, player.videoUrlAtCurrentTime)
        },
        onMute = clickAction { player.toggleMute() },
        onKodi = clickAction {
            if (player.currentMetadata != null) {
                player.pause()
                KoreUtils.playWithKore(context, Uri.parse(player.videoUrl))
            }
        },
        onOpenInBrowser = clickAction {
            player.currentStreamInfo.ifPresent { streamInfo ->
                ShareUtils.openUrlInBrowser(player.context, streamInfo.originalUrl)
            }
        },
        onFullScreen = clickAction {
            player.setRecovery()
            NavigationHelper.playOnMainPlayer(
                context,
                player.playQueue ?: return@clickAction,
                true
            )
        },
        onAnyClick = ::afterChromeClick
    )

    abstract fun buildGestureListener(): BasePlayerGestureListener

    protected open fun onMoreOptionsClicked() {
        chromeState.update { it.copy(secondaryVisible = !it.secondaryVisible) }
        showControls(DEFAULT_CONTROLS_DURATION)
    }

    protected open fun onMoreOptionsLongClicked() {}

    /**
     * Wraps a chrome action with the legacy click handling: the controls are re-shown and then
     * hidden again when playing (immediately for some actions, after a delay for the others).
     */
    protected fun clickAction(hideImmediately: Boolean = false, runnable: () -> Unit): () -> Unit {
        return {
            runnable()
            afterChromeClick(hideImmediately)
        }
    }

    protected fun afterChromeClick(hideImmediately: Boolean) {
        if (player.currentState == Player.STATE_COMPLETED) {
            return
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        chromeState.update { it.copy(controlsVisible = true) }
        showSystemUIPartially()
        if (player.currentState == Player.STATE_PLAYING && !isSomePopupMenuVisible) {
            if (hideImmediately) {
                hideControls(0, 0)
            } else {
                hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME)
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Fast seek overlay (double tap)
    // ///////////////////////////////////////////////////////////

    private fun setupPlayerSeekOverlay() {
        views.fastSeekOverlay
            .seekSecondsSupplier { PlayerHelper.retrieveSeekDurationFromPreferences(player) / 1000 }
            .performListener(object : org.schabi.newpipe.views.player.PlayerFastSeekOverlay
                .PerformListener {
                override fun onDoubleTap() {
                    views.fastSeekOverlay.animate(true, SEEK_OVERLAY_DURATION)
                }

                override fun onDoubleTapEnd() {
                    views.fastSeekOverlay.animate(false, SEEK_OVERLAY_DURATION)
                }

                override fun getFastSeekDirection(portion: DisplayPortion): FastSeekDirection {
                    if (player.exoPlayerIsNull()) {
                        // abort seeking
                        playerGestureListener?.endMultiDoubleTap()
                        return FastSeekDirection.NONE
                    }
                    if (portion === DisplayPortion.LEFT) {
                        // check if it's possible to rewind
                        // small puffer to eliminate infinite rewind seeking
                        if (player.exoPlayer.currentPosition < 500L) {
                            return FastSeekDirection.NONE
                        }
                        return FastSeekDirection.BACKWARD
                    } else if (portion === DisplayPortion.RIGHT) {
                        // check if it's possible to fast-forward
                        if (player.currentState == Player.STATE_COMPLETED ||
                            player.exoPlayer.currentPosition >= player.exoPlayer.duration
                        ) {
                            return FastSeekDirection.NONE
                        }
                        return FastSeekDirection.FORWARD
                    }
                    // portion == DisplayPortion.MIDDLE
                    return FastSeekDirection.NONE
                }

                override fun seek(forward: Boolean) {
                    playerGestureListener?.keepInDoubleTapMode()
                    if (forward) {
                        player.fastForward()
                    } else {
                        player.fastRewind()
                    }
                }
            })
        playerGestureListener?.doubleTapControls(views.fastSeekOverlay)
    }

    fun deinitPlayerSeekOverlay() {
        views.fastSeekOverlay
            .seekSecondsSupplier(null)
            .performListener(null)
    }

    // ///////////////////////////////////////////////////////////
    // Setup and destroy
    // ///////////////////////////////////////////////////////////

    override fun setupAfterIntent() {
        super.setupAfterIntent()
        setupElementsVisibility()
        views.root.visibility = android.view.View.VISIBLE
    }

    protected open fun setupElementsVisibility() {
        chromeState.update { it.copy(muted = player.isMuted, secondaryVisible = false) }
    }

    override fun initPlayer() {
        super.initPlayer()
        setupVideoSurfaceIfNeeded()
    }

    override fun initPlayback() {
        super.initPlayback()
        // #6825 - ensure that the shuffle-button is in the correct state on the UI
        player.exoPlayer?.let { exo ->
            chromeState.update {
                it.copy(
                    shuffled = exo.shuffleModeEnabled,
                    repeatOff = exo.repeatMode == com.google.android.exoplayer2.Player
                        .REPEAT_MODE_OFF,
                    repeatOne = exo.repeatMode == com.google.android.exoplayer2.Player
                        .REPEAT_MODE_ONE
                )
            }
        }
    }

    abstract fun removeViewFromParent()

    override fun destroyPlayer() {
        super.destroyPlayer()
        clearVideoSurface()
    }

    override fun destroy() {
        super.destroy()
        views.endScreen.setImageDrawable(null)
        deinitPlayerSeekOverlay()
        views.root.setOnTouchListener(null)
        playerGestureListener = null
        gestureDetector = null
    }

    // ///////////////////////////////////////////////////////////
    // Thumbnail / end screen
    // ///////////////////////////////////////////////////////////

    override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if (Intent.ACTION_CONFIGURATION_CHANGED == intent.action) {
            // when the orientation changes the end screen thumbnail must be re-scaled,
            // otherwise it can enlarge the whole player
            updateEndScreenThumbnail(player.thumbnail)
        }
    }

    override fun onThumbnailLoaded(bitmap: Bitmap?) {
        super.onThumbnailLoaded(bitmap)
        updateEndScreenThumbnail(bitmap)
    }

    private fun updateEndScreenThumbnail(thumbnail: Bitmap?) {
        if (thumbnail == null) {
            views.endScreen.setImageDrawable(null)
            return
        }
        val endScreenHeight = calculateMaxEndScreenThumbnailHeight(thumbnail)
        val endScreenBitmap = BitmapCompat.createScaledBitmap(
            thumbnail,
            (thumbnail.width / (thumbnail.height / endScreenHeight)).toInt(),
            endScreenHeight.toInt(),
            null,
            true
        )
        views.endScreen.setImageBitmap(endScreenBitmap)
    }

    protected abstract fun calculateMaxEndScreenThumbnailHeight(bitmap: Bitmap): Float

    // ///////////////////////////////////////////////////////////
    // Progress and seeking
    // ///////////////////////////////////////////////////////////

    override fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
        chromeState.update {
            it.copy(
                durationMillis = duration,
                progressMillis = if (player.currentState != Player.STATE_PAUSED &&
                    player.currentState != Player.STATE_PAUSED_SEEK
                ) {
                    currentProgress
                } else {
                    it.progressMillis
                },
                currentTimeText = PlayerHelper.getTimeString(currentProgress.toLong()),
                endTimeText = PlayerHelper.getTimeString(duration.toLong()),
                bufferPercent = bufferPercent,
                liveSyncEnabled = !player.isLiveEdge
            )
        }
    }

    private fun onSeekStart() {
        if (player.currentState != Player.STATE_PAUSED_SEEK) {
            player.changeState(Player.STATE_PAUSED_SEEK)
        }
        showControls(0)
    }

    private fun onSeekPreview(fraction: Float, progressMs: Int) {
        val chapter = getChapterAtMs(progressMs.toLong())
        if (chapter != lastChapterForHaptic) {
            lastChapterForHaptic = chapter
            if (currentChapters.isNotEmpty()) {
                views.root.performHapticFeedback(
                    android.view.HapticFeedbackConstants.CLOCK_TICK
                )
            }
        }
        chromeState.update {
            it.copy(
                seekDisplayText = PlayerHelper.getTimeString(progressMs.toLong()),
                seekPreview = seekbarPreviewThumbnailHolder.getBitmapAt(progressMs)
                    .orElse(null),
                chapterTitle = if (currentChapters.isEmpty()) null else chapter?.title
            )
        }
    }

    private fun onSeekEnd(progressMs: Int) {
        player.seekTo(progressMs.toLong())
        if (player.exoPlayer?.duration == progressMs.toLong()) {
            player.exoPlayer?.play()
        }
        chromeState.update {
            it.copy(
                seekDisplayText = null,
                seekPreview = null,
                chapterTitle = null,
                progressMillis = progressMs,
                currentTimeText = PlayerHelper.getTimeString(progressMs.toLong())
            )
        }
        if (player.currentState == Player.STATE_PAUSED_SEEK) {
            player.changeState(Player.STATE_BUFFERING)
        }
        if (!player.isProgressLoopRunning) {
            player.startProgressLoop()
        }
        showControlsThenHide()
    }

    private fun onSeekCancel() {
        chromeState.update {
            it.copy(seekDisplayText = null, seekPreview = null, chapterTitle = null)
        }
        if (player.currentState == Player.STATE_PAUSED_SEEK) {
            player.changeState(Player.STATE_BUFFERING)
        }
        if (!player.isProgressLoopRunning) {
            player.startProgressLoop()
        }
        showControlsThenHide()
    }

    private fun getChapterAtMs(positionMs: Long): StreamSegment? {
        var result: StreamSegment? = null
        for (seg in currentChapters) {
            if (seg.startTimeSeconds * 1000L > positionMs) {
                break
            }
            result = seg
        }
        return result
    }

    // ///////////////////////////////////////////////////////////
    // Controls showing / hiding
    // ///////////////////////////////////////////////////////////

    val isControlsVisible: Boolean
        get() = chromeState.value.controlsVisible

    fun showControlsThenHide() {
        showOrHideButtons()
        showSystemUIPartially()

        val hideTime = if (views.root.isInTouchMode) {
            DEFAULT_CONTROLS_HIDE_TIME
        } else {
            DPAD_CONTROLS_HIDE_TIME
        }

        chromeState.update { it.copy(controlsVisible = true) }
        hideControls(DEFAULT_CONTROLS_DURATION, hideTime)
    }

    fun showControls(duration: Long) {
        showOrHideButtons()
        showSystemUIPartially()
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        chromeState.update { it.copy(controlsVisible = true) }
    }

    fun hideControls(duration: Long, delay: Long) {
        showOrHideButtons()
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        controlsVisibilityHandler.postDelayed({
            chromeState.update { it.copy(controlsVisible = false, secondaryVisible = false) }
            hideSystemUIIfNeeded()
        }, delay)
    }

    protected open fun showOrHideButtons() {
        val playQueue = player.playQueue ?: return
        chromeState.update {
            it.copy(
                showPrevious = playQueue.index != 0,
                showNext = playQueue.index + 1 != playQueue.streams.size
            )
        }
    }

    protected open fun showSystemUIPartially() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected open fun hideSystemUIIfNeeded() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected open fun isAnyListViewOpen(): Boolean = false

    open val isFullscreen: Boolean
        get() = false

    // ///////////////////////////////////////////////////////////
    // Playback states
    // ///////////////////////////////////////////////////////////

    override fun onPrepared() {
        super.onPrepared()
        chromeState.update {
            it.copy(
                durationMillis = (player.exoPlayer?.duration ?: 0L).toInt(),
                speedText = PlayerHelper.formatSpeed(player.playbackSpeed.toDouble())
            )
        }
    }

    override fun onBlocked() {
        super.onBlocked()
        // if we are e.g. switching players, hide controls
        hideControls(DEFAULT_CONTROLS_DURATION, 0)
        chromeState.update {
            it.copy(
                blocked = true,
                seekEnabled = false,
                playAction = YpPlayAction.PLAY
            )
        }
        views.surfaceForeground.animate(true, 100)
        views.root.keepScreenOn = false
    }

    override fun onPlaying() {
        super.onPlaying()
        updateStreamRelatedViews()
        chromeState.update {
            it.copy(
                blocked = false,
                buffering = false,
                seekEnabled = true,
                playAction = YpPlayAction.PAUSE,
                seekDisplayText = null
            )
        }
        views.root.keepScreenOn = true
    }

    override fun onBuffering() {
        super.onBuffering()
        chromeState.update { it.copy(blocked = false, buffering = true) }
        views.root.keepScreenOn = true
    }

    override fun onPaused() {
        super.onPaused()
        // don't let UI elements popup during double tap seeking
        if (playerGestureListener?.isDoubleTapping != true) {
            showControls(400)
            chromeState.update { it.copy(buffering = false, playAction = YpPlayAction.PLAY) }
        }
        views.root.keepScreenOn = false
    }

    override fun onPausedSeek() {
        super.onPausedSeek()
        views.root.keepScreenOn = true
    }

    override fun onCompleted() {
        super.onCompleted()
        chromeState.update {
            it.copy(
                playAction = YpPlayAction.REPLAY,
                progressMillis = it.durationMillis,
                currentTimeText = PlayerHelper.getTimeString(it.durationMillis.toLong()),
                buffering = false,
                seekDisplayText = null
            )
        }
        views.root.keepScreenOn = false
        showControls(500)
        views.surfaceForeground.animate(true, 100)
    }

    // ///////////////////////////////////////////////////////////
    // Repeat, shuffle, mute
    // ///////////////////////////////////////////////////////////

    override fun onRepeatModeChanged(repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        chromeState.update {
            it.copy(
                repeatOff = repeatMode == com.google.android.exoplayer2.Player.REPEAT_MODE_OFF,
                repeatOne = repeatMode == com.google.android.exoplayer2.Player.REPEAT_MODE_ONE
            )
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        chromeState.update { it.copy(shuffled = shuffleModeEnabled) }
    }

    override fun onMuteUnmuteChanged(isMuted: Boolean) {
        super.onMuteUnmuteChanged(isMuted)
        chromeState.update { it.copy(muted = isMuted) }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        chromeState.update { it.copy(speedText = PlayerHelper.formatSpeed(playbackParameters.speed.toDouble())) }
    }

    override fun onRenderedFirstFrame() {
        super.onRenderedFirstFrame()
        views.surfaceForeground.animate(false, DEFAULT_CONTROLS_DURATION)
    }

    // ///////////////////////////////////////////////////////////
    // Metadata & stream related views
    // ///////////////////////////////////////////////////////////

    override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        updateStreamRelatedViews()
        chromeState.update { it.copy(title = info.name, uploader = info.uploaderName) }

        seekbarPreviewThumbnailHolder.resetFrom(player.context, info.previewFrames)

        // chapter markers on the seek bar
        currentChapters = info.streamSegments ?: Collections.emptyList()
        lastChapterForHaptic = null
        val duration = info.duration
        chromeState.update {
            it.copy(
                chapterFractions = if (duration > 0) {
                    currentChapters.map { seg -> seg.startTimeSeconds.toFloat() / duration }
                } else {
                    emptyList()
                },
                chapterTitle = null
            )
        }
    }

    protected fun updateStreamRelatedViews() {
        player.currentStreamInfo.ifPresent { info ->
            var showQuality = false
            var showAudioTrack = false
            var showEndTime = false
            var showLiveSync = false
            var videoVisible = true
            var endScreenVisible = false

            when (info.streamType) {
                org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM,
                org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM -> {
                    videoVisible = false
                    endScreenVisible = true
                    showEndTime = true
                }

                org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM -> {
                    videoVisible = false
                    endScreenVisible = true
                    showLiveSync = true
                }

                org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM -> {
                    showLiveSync = true
                }

                org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM,
                org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM -> {
                    val noQuality = player.currentMetadata?.maybeQuality?.isEmpty ?: false
                    if (!noQuality &&
                        !(info.videoStreams.isEmpty() && info.videoOnlyStreams.isEmpty())
                    ) {
                        buildQualityMenu()
                        buildAudioTrackMenu()
                        showQuality = true
                        showAudioTrack = chromeState.value.audioTrackOptions.size >= 2
                    }
                    showEndTime = true
                }

                else -> {
                    endScreenVisible = false
                    showEndTime = true
                }
            }

            buildPlaybackSpeedMenu()
            views.surfaceView.visibility =
                if (videoVisible) android.view.View.VISIBLE else android.view.View.GONE
            views.endScreen.visibility =
                if (endScreenVisible) android.view.View.VISIBLE else android.view.View.GONE
            chromeState.update {
                it.copy(
                    showQuality = showQuality,
                    showAudioTrack = showAudioTrack,
                    isLive = showLiveSync
                )
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Menus
    // ///////////////////////////////////////////////////////////

    private fun buildQualityMenu() {
        val availableStreams = Optional.ofNullable(player.currentMetadata)
            .flatMap(MediaItemTag::getMaybeQuality)
            .map(MediaItemTag.Quality::getSortedVideoStreams)
            .orElse(null) ?: return

        val options = availableStreams.map { videoStream: VideoStream ->
            MediaFormat.getNameById(videoStream.formatId) + " " + videoStream.resolution
        }
        val selected = player.selectedVideoStream
            .map { s -> s.resolution }
            .orElse(chromeState.value.qualityText)
        chromeState.update { it.copy(qualityOptions = options, qualityText = selected) }
    }

    private fun buildAudioTrackMenu() {
        val availableStreams = Optional.ofNullable(player.currentMetadata)
            .flatMap(MediaItemTag::getMaybeAudioTrack)
            .map(MediaItemTag.AudioTrack::getAudioStreams)
            .orElse(null)
        if (availableStreams == null || availableStreams.size < 2) {
            chromeState.update { it.copy(audioTrackOptions = emptyList()) }
            return
        }
        val options = availableStreams.map { audioStream: AudioStream ->
            Localization.audioTrackName(context, audioStream)
        }
        val selected = player.selectedAudioStream
            .map { s -> Localization.audioTrackName(context, s) }
            .orElse(chromeState.value.audioTrackText)
        chromeState.update { it.copy(audioTrackOptions = options, audioTrackText = selected) }
    }

    private fun buildPlaybackSpeedMenu() {
        chromeState.update {
            it.copy(
                speedOptions = PLAYBACK_SPEEDS.map { PlayerHelper.formatSpeed(it.toDouble()) },
                speedText = PlayerHelper.formatSpeed(player.playbackSpeed.toDouble())
            )
        }
    }

    private fun onQualitySelected(index: Int) {
        val currentMetadata = player.currentMetadata
        if (currentMetadata == null || currentMetadata.maybeQuality.isEmpty) {
            return
        }
        val quality = currentMetadata.maybeQuality.get()
        val availableStreams = quality.sortedVideoStreams
        val selectedStreamIndex = quality.selectedVideoStreamIndex
        if (selectedStreamIndex == index || availableStreams.size <= index) {
            return
        }
        val newResolution = availableStreams[index].resolution
        player.setPlaybackQuality(newResolution)
        chromeState.update { it.copy(qualityText = it.qualityOptions[index]) }
    }

    private fun onAudioTrackSelected(index: Int) {
        val currentMetadata = player.currentMetadata
        if (currentMetadata == null || currentMetadata.maybeAudioTrack.isEmpty) {
            return
        }
        val audioTrack = currentMetadata.maybeAudioTrack.get()
        val availableStreams = audioTrack.audioStreams
        val selectedStreamIndex = audioTrack.selectedAudioStreamIndex
        if (selectedStreamIndex == index || availableStreams.size <= index) {
            return
        }
        val newAudioTrack = availableStreams[index].audioTrackId
        player.setAudioTrack(newAudioTrack)
        chromeState.update { it.copy(audioTrackText = it.audioTrackOptions[index]) }
    }

    private fun onSpeedSelected(index: Int) {
        val speed = PLAYBACK_SPEEDS[index]
        player.setPlaybackSpeed(speed)
        chromeState.update { it.copy(speedText = PlayerHelper.formatSpeed(speed.toDouble())) }
    }

    protected abstract fun onPlaybackSpeedClicked()

    /** Called when a menu opens or closes, so the controls are not hidden underneath it. */
    fun onMenuVisibilityChanged(visible: Boolean) {
        isSomePopupMenuVisible = visible
        if (!visible) {
            player.selectedVideoStream
                .ifPresent { s -> chromeState.update { it.copy(qualityText = s.resolution) } }
            if (player.isPlaying) {
                hideControls(DEFAULT_CONTROLS_DURATION, 0)
                hideSystemUIIfNeeded()
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Captions (text tracks)
    // ///////////////////////////////////////////////////////////

    override fun onTextTracksChanged(currentTracks: Tracks) {
        super.onTextTracksChanged(currentTracks)

        val trackTypeTextSupported = !currentTracks.containsType(C.TRACK_TYPE_TEXT) ||
            currentTracks.isTypeSupported(C.TRACK_TYPE_TEXT, false)
        if (player.trackSelector.currentMappedTrackInfo == null || !trackTypeTextSupported) {
            chromeState.update { it.copy(showCaption = false) }
            return
        }

        // extract all loaded languages
        val textTracks = currentTracks.groups
            .filter { trackGroupInfo -> C.TRACK_TYPE_TEXT == trackGroupInfo.type }
        val availableLanguages = textTracks
            .map { it.mediaTrackGroup }
            .filter { textTrack -> textTrack.length > 0 }
            .map { textTrack -> textTrack.getFormat(0).language ?: "" }

        // find selected text track
        val selectedTrack = textTracks
            .filter { it.isSelected }
            .filter { it.mediaTrackGroup.length >= 1 }
            .map { it.mediaTrackGroup.getFormat(0) }
            .firstOrNull()

        // apply caption language from previous user preference
        val textRendererIndex = player.captionRendererIndex
        if (textRendererIndex != Player.RENDERER_UNAVAILABLE) {
            val userPreferredLanguage = player.prefs.getString(
                context.getString(R.string.caption_user_set_key),
                null
            )
            if (userPreferredLanguage == null) {
                player.trackSelector.setParameters(
                    player.trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true)
                )
            } else {
                val selectedPreferredLanguages =
                    player.trackSelector.parameters.preferredTextLanguages
                if (!selectedPreferredLanguages.contains(userPreferredLanguage)) {
                    player.trackSelector.setParameters(
                        player.trackSelector.buildUponParameters()
                            .setPreferredTextLanguages(
                                userPreferredLanguage,
                                PlayerHelper.captionLanguageStemOf(userPreferredLanguage)
                            )
                            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                            .setRendererDisabled(textRendererIndex, false)
                    )
                }
            }
        }

        val captionNone = player.trackSelector.parameters.getRendererDisabled(
            player.captionRendererIndex
        ) || selectedTrack == null
        chromeState.update {
            it.copy(
                captionOptions = availableLanguages,
                showCaption = availableLanguages.isNotEmpty(),
                captionText = if (captionNone) {
                    context.getString(R.string.caption_none)
                } else {
                    selectedTrack?.language ?: ""
                }
            )
        }
    }

    private fun onCaptionSelected(index: Int) {
        val textRendererIndex = player.captionRendererIndex
        if (index < 0) {
            // caption off
            if (textRendererIndex != Player.RENDERER_UNAVAILABLE) {
                player.trackSelector.setParameters(
                    player.trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true)
                )
            }
            player.prefs.edit()
                .remove(context.getString(R.string.caption_user_set_key)).apply()
            return
        }
        val captionLanguage = chromeState.value.captionOptions.getOrNull(index) ?: return
        if (textRendererIndex != Player.RENDERER_UNAVAILABLE) {
            // DefaultTrackSelector will select for text tracks in the following order.
            // 1. ANY track exactly matching preferred language name
            // 2. ANY track exactly matching preferred language stem
            // 3. ROLE_FLAG_CAPTION track matching preferred language stem
            // 4. ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND track matching preferred language stem
            player.trackSelector.setParameters(
                player.trackSelector.buildUponParameters()
                    .setPreferredTextLanguages(
                        captionLanguage,
                        PlayerHelper.captionLanguageStemOf(captionLanguage)
                    )
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false)
            )
            player.prefs.edit().putString(
                context.getString(R.string.caption_user_set_key),
                captionLanguage
            ).apply()
        }
    }

    override fun onCues(cues: List<Cue>) {
        super.onCues(cues)
        views.subtitleView.setCues(cues)
    }

    private fun setupSubtitleView() {
        val captionScale = PlayerHelper.getCaptionScale(context)
        setupSubtitleView(captionScale)
        val captionStyle = PlayerHelper.getCaptionStyle(context)
        views.subtitleView.setApplyEmbeddedStyles(
            captionStyle == com.google.android.exoplayer2.ui.CaptionStyleCompat.DEFAULT
        )
        views.subtitleView.setStyle(captionStyle)
    }

    protected abstract fun setupSubtitleView(captionScale: Float)

    // ///////////////////////////////////////////////////////////
    // Keys
    // ///////////////////////////////////////////////////////////

    open fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (DeviceUtils.isTv(context) && isControlsVisible) {
                    hideControls(0, 0)
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (isAnyListViewOpen()) {
                    // do not interfere with focus in playlist and play queue etc.
                    return false
                }
                if (player.currentState == Player.STATE_BLOCKED) {
                    return true
                }
                if (isControlsVisible) {
                    hideControls(DEFAULT_CONTROLS_DURATION, DPAD_CONTROLS_HIDE_TIME)
                } else {
                    showControlsThenHide()
                    showSystemUIPartially()
                    return true
                }
            }

            else -> {
                // ignore other keys
            }
        }
        return false
    }

    // ///////////////////////////////////////////////////////////
    // Volume / brightness indicators (driven by the gesture listeners)
    // ///////////////////////////////////////////////////////////

    val volumeIndicatorPercent: Float?
        get() = chromeState.value.volumeIndicator

    val brightnessIndicatorPercent: Float?
        get() = chromeState.value.brightnessIndicator

    fun showVolumeIndicator(percent: Float) {
        chromeState.update { it.copy(volumeIndicator = percent, brightnessIndicator = null) }
    }

    fun showBrightnessIndicator(percent: Float) {
        chromeState.update { it.copy(brightnessIndicator = percent, volumeIndicator = null) }
    }

    fun hideVolumeBrightnessIndicators() {
        chromeState.update { it.copy(volumeIndicator = null, brightnessIndicator = null) }
    }

    fun hideSeekDisplay() {
        chromeState.update { it.copy(seekDisplayText = null, seekPreview = null) }
    }

    fun setClosingOverlayVisible(visible: Boolean) {
        chromeState.update { it.copy(closingOverlayVisible = visible) }
    }

    fun setLoadingVisible(visible: Boolean) {
        chromeState.update { it.copy(blocked = visible) }
    }

    // ///////////////////////////////////////////////////////////
    // Resize and video size
    // ///////////////////////////////////////////////////////////

    protected fun setResizeMode(resizeMode: Int) {
        views.surfaceView.resizeMode = resizeMode
        chromeState.update {
            it.copy(resizeText = PlayerHelper.resizeTypeOf(context, resizeMode))
        }
    }

    fun onResizeClicked() {
        setResizeMode(
            PlayerHelper.nextResizeModeAndSaveToPrefs(player, views.surfaceView.resizeMode)
        )
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        // starting with ExoPlayer 2.19.0, the VideoSize will report a width and height of 0
        // if the renderer is disabled; in that case skip updating the aspect ratio
        if (videoSize.width == 0 || videoSize.height == 0) {
            return
        }
        views.surfaceView.setAspectRatio(videoSize.width.toFloat() / videoSize.height)
    }

    // ///////////////////////////////////////////////////////////
    // SurfaceHolderCallback helpers
    // ///////////////////////////////////////////////////////////

    /**
     * Connects the video surface to the exo player. This can be called anytime without the risk
     * for issues to occur, since the player will run just fine when no surface is connected.
     */
    fun setupVideoSurfaceIfNeeded() {
        if (!surfaceIsSetup && player.exoPlayer != null && views.root.parent != null) {
            // make sure there is nothing left over from previous calls
            clearVideoSurface()

            surfaceHolderCallback = SurfaceHolderCallback(context, player.exoPlayer)
            views.surfaceView.holder.addCallback(surfaceHolderCallback)

            // ensure player is using an unreleased surface, which the surfaceView might not be
            // when starting playback on background or during player switching
            if (views.surfaceView.holder.surface.isValid) {
                // initially set the surface manually otherwise onRenderedFirstFrame() will not
                // be called
                player.exoPlayer.setVideoSurfaceHolder(views.surfaceView.holder)
            }

            surfaceIsSetup = true
        }
    }

    private fun clearVideoSurface() {
        surfaceHolderCallback?.let {
            views.surfaceView.holder.removeCallback(it)
            it.release()
            surfaceHolderCallback = null
        }
        player.exoPlayer?.clearVideoSurface()
        surfaceIsSetup = false
    }

    companion object {
        private val TAG = VideoPlayerUi::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG

        const val DEFAULT_CONTROLS_DURATION = 300L // 300 millis
        const val DEFAULT_CONTROLS_HIDE_TIME = 2000L // 2 seconds
        const val DPAD_CONTROLS_HIDE_TIME = 7000L // 7 seconds
        const val SEEK_OVERLAY_DURATION = 450L // 450 millis

        @JvmField
        val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }
}
