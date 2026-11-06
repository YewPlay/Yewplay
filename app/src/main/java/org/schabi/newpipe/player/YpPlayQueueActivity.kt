/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.android.exoplayer2.PlaybackParameters
import io.github.yewplay.core.designsystem.YpTheme
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * YewPlay: the "Ouvir" screen, in Kotlin. Everything is drawn in Compose by [YpListenScreen];
 * this activity keeps the player service binding and the legacy dialogs (integration layer).
 */
class YpPlayQueueActivity : AppCompatActivity(), PlayerEventListener, PlaybackParameterDialog.Callback {

    private var player: Player? = null
    private var serviceBound = false
    private lateinit var serviceConnection: ServiceConnection

    private val uiState = MutableStateFlow(YpListenState())

    /** Work that tints the screen with the color of the cover. */
    private val coverColorDisposables = CompositeDisposable()
    private var lastCoverUrl: String? = null
    private var queueEventsDisposable: Disposable? = null
    private var lastProgressMillis = 0

    /** Stable keys for the queue rows, one per PlayQueueItem instance. */
    private val queueItemKeys = WeakHashMap<PlayQueueItem, String>()
    private var nextQueueItemKey = 0L

    // Activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))

        serviceConnection = createServiceConnection()

        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            YpTheme(
                themeKey = YpThemePrefs.themeKey(this),
                nightThemeKey = YpThemePrefs.nightThemeKey(this)
            ) {
                val state by uiState.collectAsState()
                YpListenScreen(
                    state = state,
                    labels = YpListenLabels(
                        back = stringResource(
                            androidx.appcompat.R.string.abc_action_bar_up_description
                        ),
                        play = stringResource(R.string.play),
                        pause = stringResource(R.string.pause),
                        replay = stringResource(R.string.replay),
                        previousStream = stringResource(R.string.previous_stream),
                        nextStream = stringResource(R.string.next_stream),
                        rewind = stringResource(R.string.rewind),
                        forward = stringResource(R.string.forward),
                        repeat = stringResource(R.string.notification_action_repeat),
                        shuffle = stringResource(R.string.notification_action_shuffle),
                        live = stringResource(R.string.duration_live),
                        playbackSpeed = stringResource(R.string.playback_speed_control),
                        sleepTimer = stringResource(R.string.yp_sleep_timer),
                        sleepTimerTitle = stringResource(R.string.yp_sleep_timer_title),
                        sleepTimerOff = stringResource(R.string.yp_sleep_timer_off),
                        sleepTimerMinutes = { minutes ->
                            getString(R.string.yp_sleep_timer_minutes, minutes)
                        },
                        share = stringResource(R.string.share),
                        queue = stringResource(R.string.title_activity_play_queue),
                        addToPlaylist = stringResource(R.string.add_to_playlist),
                        mute = stringResource(R.string.mute),
                        unmute = stringResource(R.string.unmute),
                        settings = stringResource(R.string.settings),
                        systemAudio = stringResource(R.string.play_queue_audio_settings),
                        switchToMain = stringResource(R.string.switch_to_main),
                        switchToPopup = stringResource(R.string.switch_to_popup),
                        switchToBackground = stringResource(R.string.switch_to_background),
                        audioTrack = stringResource(R.string.audio_track),
                        remove = stringResource(R.string.play_queue_remove),
                        streamDetails = stringResource(R.string.play_queue_stream_detail),
                        channelDetails = stringResource(R.string.show_channel_details),
                        download = stringResource(R.string.download)
                    ),
                    icons = YpListenIcons(
                        back = painterResource(R.drawable.ic_arrow_back),
                        play = painterResource(R.drawable.ic_play_arrow),
                        pause = painterResource(R.drawable.ic_pause),
                        replay = painterResource(R.drawable.ic_replay),
                        previous = painterResource(R.drawable.ic_previous),
                        next = painterResource(R.drawable.ic_next),
                        rewind = painterResource(
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_rewind
                        ),
                        forward = painterResource(
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_fastforward
                        ),
                        repeatOff = painterResource(
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_off
                        ),
                        repeatOne = painterResource(
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_one
                        ),
                        repeatAll = painterResource(
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_all
                        ),
                        shuffle = painterResource(R.drawable.ic_shuffle),
                        timer = painterResource(R.drawable.yp_ic_timer),
                        share = painterResource(R.drawable.ic_share),
                        queue = painterResource(R.drawable.ic_playlist_play),
                        playlistAdd = painterResource(R.drawable.ic_playlist_add),
                        volumeOn = painterResource(R.drawable.ic_volume_up),
                        volumeOff = painterResource(R.drawable.ic_volume_off),
                        dragHandle = painterResource(R.drawable.ic_drag_handle),
                        moreVert = painterResource(R.drawable.ic_more_vert)
                    ),
                    actions = YpListenActions(
                        onBack = { finish() },
                        onAppendPlayQueueToPlaylist = {
                            player?.let {
                                PlaylistDialog.showForPlayQueue(it, supportFragmentManager)
                            }
                        },
                        onOpenPlaybackParameters = { openPlaybackParameterDialog() },
                        onSelectAudioTrack = { index -> onAudioTrackClick(index) },
                        onToggleMute = {
                            player?.let { p ->
                                p.toggleMute()
                                uiState.update { it.copy(muted = p.isMuted) }
                            }
                        },
                        onOpenSettings = { NavigationHelper.openSettings(this) },
                        onOpenSystemAudio = {
                            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                        },
                        onSwitchToMain = {
                            player?.playQueue?.let { queue ->
                                player?.setRecovery()
                                NavigationHelper.playOnMainPlayer(this, queue, true)
                            }
                        },
                        onSwitchToPopup = {
                            player?.playQueue?.let { queue ->
                                if (PermissionHelper.isPopupEnabledElseAsk(this)) {
                                    player?.setRecovery()
                                    NavigationHelper.playOnPopupPlayer(this, queue, true)
                                }
                            }
                        },
                        onSwitchToBackground = {
                            player?.playQueue?.let { queue ->
                                player?.setRecovery()
                                NavigationHelper.playOnBackgroundPlayer(this, queue, true)
                            }
                        },
                        onRepeat = { player?.cycleNextRepeatMode() },
                        onPrevious = { player?.playPrevious() },
                        onFastRewind = { player?.fastRewind() },
                        onPlayPause = { player?.playPause() },
                        onFastForward = { player?.fastForward() },
                        onNext = { player?.playNext() },
                        onShuffle = { player?.toggleShuffleModeEnabled() },
                        onSeek = { fraction ->
                            player?.seekTo((fraction * uiState.value.durationMillis).toLong())
                        },
                        onLiveSync = { player?.seekToDefault() },
                        onSleepTimer = { minutes -> onSleepTimer(minutes) },
                        onShare = {
                            player?.let { p ->
                                ShareUtils.shareText(this, p.videoTitle, p.videoUrlAtCurrentTime)
                            }
                        },
                        onToggleQueue = {
                            uiState.update { s ->
                                s.copy(
                                    showQueue = !s.showQueue,
                                    queueScrollSignal = if (s.showQueue) {
                                        s.queueScrollSignal
                                    } else {
                                        s.queueScrollSignal + 1
                                    }
                                )
                            }
                        },
                        onMetadataClick = {
                            uiState.update { it.copy(queueScrollSignal = it.queueScrollSignal + 1) }
                        },
                        onQueueItemClick = { index ->
                            player?.playQueue?.streams?.getOrNull(index)?.let { item ->
                                player?.selectQueueItem(item)
                            }
                        },
                        onQueueItemAction = { index, action -> onQueueItemAction(index, action) },
                        onQueueMove = { from, to -> player?.playQueue?.move(from, to) },
                        onQueueMoveFinished = { player?.playQueue?.let(::refreshQueue) },
                        onQueueRemove = { index -> player?.playQueue?.remove(index) },
                        onQueueEndReached = {
                            player?.playQueue?.let { queue ->
                                if (!queue.isComplete) {
                                    queue.fetch()
                                }
                            }
                        }
                    )
                )
            }
        }

        bindPlayerService()
    }

    override fun onDestroy() {
        super.onDestroy()
        coverColorDisposables.clear()
        unbind()
    }

    // Service connection (kept as the legacy bound it; PlayerHolder will replace it with the
    // new player, see the note of the original activity)

    private fun bindPlayerService() {
        val bindIntent = Intent(this, PlayerService::class.java)
        bindIntent.setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION)
        val success = bindService(bindIntent, serviceConnection, BIND_AUTO_CREATE)
        if (!success) {
            unbindService(serviceConnection)
        }
        serviceBound = success
    }

    private fun unbind() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            player?.removeActivityListener(this)
            queueEventsDisposable?.dispose()
            queueEventsDisposable = null
            player = null
            uiState.update {
                it.copy(queueItems = emptyList(), queueIndex = -1, queueComplete = true)
            }
        }
    }

    private fun createServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(TAG, "Player service is disconnected")
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.d(TAG, "Player service is connected")

                if (service is PlayerService.LocalBinder) {
                    player = service.service.player
                }

                val p = player
                if (p == null || p.playQueue == null || p.exoPlayerIsNull()) {
                    unbind()
                } else {
                    onQueueUpdate(p.playQueue)
                    rebuildAudioTrackState()
                    uiState.update {
                        it.copy(
                            sleepTimerActive = YpSleepTimer.isActive(),
                            showPopupSwitch = !p.popupPlayerSelected(),
                            showBackgroundSwitch = !p.audioPlayerSelected()
                        )
                    }
                    p.setActivityListener(this@YpPlayQueueActivity)
                }
            }
        }
    }

    // Player events

    override fun onQueueUpdate(queue: PlayQueue?) {
        queueEventsDisposable?.dispose()
        queueEventsDisposable = null
        if (queue == null) {
            uiState.update {
                it.copy(queueItems = emptyList(), queueIndex = -1, queueComplete = true)
            }
        } else {
            refreshQueue(queue)
            queue.broadcastReceiver?.let { receiver ->
                queueEventsDisposable = receiver
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ refreshQueue(queue) }, { })
            }
        }
    }

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters
    ) {
        uiState.update {
            it.copy(
                playState = when (state) {
                    Player.STATE_PLAYING -> YpListenPlayState.PLAYING
                    Player.STATE_PAUSED -> YpListenPlayState.PAUSED
                    Player.STATE_COMPLETED -> YpListenPlayState.COMPLETED
                    else -> YpListenPlayState.BUFFERING
                },
                repeatMode = when (repeatMode) {
                    com.google.android.exoplayer2.Player.REPEAT_MODE_ONE -> YpListenRepeatMode.ONE
                    com.google.android.exoplayer2.Player.REPEAT_MODE_ALL -> YpListenRepeatMode.ALL
                    else -> YpListenRepeatMode.OFF
                },
                shuffled = shuffled,
                speedText = formatSpeed(parameters.speed.toDouble()),
                muted = player?.isMuted ?: false,
                showPopupSwitch = player?.popupPlayerSelected() == false,
                showBackgroundSwitch = player?.audioPlayerSelected() == false
            )
        }
    }

    override fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int) {
        lastProgressMillis = currentProgress
        uiState.update {
            it.copy(
                progressMillis = currentProgress,
                durationMillis = duration,
                bufferPercent = bufferPercent,
                liveSyncEnabled = player?.isLiveEdge == false,
                queueTimeText = queueTimeText(currentProgress)
            )
        }
    }

    override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
        if (info != null) {
            uiState.update {
                it.copy(
                    title = info.name.orEmpty(),
                    artist = info.uploaderName.orEmpty(),
                    coverUrl = ImageStrategy.choosePreferredImage(info.thumbnails),
                    isLive = info.streamType == StreamType.LIVE_STREAM ||
                        info.streamType == StreamType.AUDIO_LIVE_STREAM,
                    queueScrollSignal = it.queueScrollSignal + 1
                )
            }
            updateCoverBackground(info)
        }
    }

    override fun onAudioTrackUpdate() {
        rebuildAudioTrackState()
    }

    override fun onServiceStopped() {
        unbind()
        finish()
    }

    // Queue state

    private fun keyFor(item: PlayQueueItem): String = queueItemKeys.getOrPut(item) { "q${nextQueueItemKey++}" }

    private fun refreshQueue(queue: PlayQueue) {
        val items = mapPlayQueueEntries(queue, ::keyFor)
        uiState.update {
            it.copy(
                queueItems = items,
                queueIndex = queue.index,
                queueComplete = queue.isComplete,
                queueTimeText = queueTimeText(lastProgressMillis)
            )
        }
    }

    /** "16:20 / 48:30": the queue progress over its total duration, like the legacy screen. */
    private fun queueTimeText(currentProgressMillis: Int): String? {
        val queue = player?.playQueue ?: return null
        return playQueueTimeText(queue, currentProgressMillis)
    }

    private fun onQueueItemAction(index: Int, action: YpQueueItemAction) {
        val queue = player?.playQueue ?: return
        val item = queue.streams.getOrNull(index) ?: return
        when (action) {
            YpQueueItemAction.REMOVE -> queue.remove(queue.indexOf(item))

            YpQueueItemAction.DETAILS -> NavigationHelper.openVideoDetail(
                this,
                item.serviceId,
                item.url,
                item.title,
                null,
                false
            )

            YpQueueItemAction.APPEND_PLAYLIST -> PlaylistDialog.createCorrespondingDialog(
                this,
                listOf(StreamEntity(item))
            ) { dialog ->
                dialog.show(supportFragmentManager, "YpPlayQueueActivity@append_playlist")
            }

            YpQueueItemAction.CHANNEL_DETAILS -> SparseItemUtil.fetchUploaderUrlIfSparse(
                this,
                item.serviceId,
                item.url,
                item.uploaderUrl
            ) { uploaderUrl ->
                NavigationHelper.openChannelFragmentUsingIntent(
                    this,
                    item.serviceId,
                    uploaderUrl,
                    item.uploader
                )
            }

            YpQueueItemAction.SHARE -> ShareUtils.shareText(
                this,
                item.title,
                item.url,
                item.thumbnails
            )

            YpQueueItemAction.DOWNLOAD -> SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                this,
                item.serviceId,
                item.url
            ) { info ->
                DownloadDialog(this, info).show(supportFragmentManager, "downloadDialog")
            }
        }
    }

    // Playback parameters (the legacy dialog is kept)

    private fun openPlaybackParameterDialog() {
        val p = player ?: return
        PlaybackParameterDialog.newInstance(
            p.playbackSpeed.toDouble(),
            p.playbackPitch.toDouble(),
            p.playbackSkipSilence,
            this
        ).show(supportFragmentManager, TAG)
    }

    override fun onPlaybackParameterChanged(
        playbackTempo: Float,
        playbackPitch: Float,
        playbackSkipSilence: Boolean
    ) {
        player?.let { p ->
            p.setPlaybackParameters(playbackTempo, playbackPitch, playbackSkipSilence)
            uiState.update {
                it.copy(speedText = formatSpeed(p.playbackParameters.speed.toDouble()))
            }
        }
    }

    // Sleep timer (YpSleepTimer is kept; it only asks the player to pause)

    private fun onSleepTimer(minutes: Int?) {
        val p = player ?: return
        if (minutes != null) {
            YpSleepTimer.start(p, minutes)
            Toast.makeText(
                this,
                getString(R.string.yp_sleep_timer_set, minutes),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            YpSleepTimer.cancel()
            Toast.makeText(this, R.string.yp_sleep_timer_cancelled, Toast.LENGTH_SHORT).show()
        }
        uiState.update { it.copy(sleepTimerActive = YpSleepTimer.isActive()) }
    }

    // Audio tracks

    private fun rebuildAudioTrackState() {
        val p = player
        val availableStreams = p?.currentMetadata?.maybeAudioTrack?.orElse(null)?.audioStreams
        val selected = p?.selectedAudioStream?.orElse(null)

        if (availableStreams == null || availableStreams.size < 2 || selected == null) {
            uiState.update {
                it.copy(audioTracks = emptyList(), selectedAudioTrack = -1, audioTrackCondensed = "")
            }
            return
        }
        val trackName = Localization.audioTrackName(this, selected)
        val shortName = selected.audioLocale?.language ?: trackName
        uiState.update {
            it.copy(
                audioTracks = availableStreams.map { stream ->
                    Localization.audioTrackName(this, stream)
                },
                selectedAudioTrack = availableStreams.indexOf(selected),
                audioTrackCondensed = shortName.substring(
                    0,
                    kotlin.math.min(shortName.length, 2)
                )
            )
        }
    }

    private fun onAudioTrackClick(itemId: Int) {
        val p = player ?: return
        p.currentMetadata?.maybeAudioTrack?.ifPresent { audioTrack ->
            val availableStreams = audioTrack.audioStreams
            if (audioTrack.selectedAudioStreamIndex == itemId || availableStreams.size <= itemId) {
                return@ifPresent
            }
            p.setAudioTrack(availableStreams[itemId].audioTrackId)
        }
    }

    // Cover background

    /** Tints the top of the screen with the average color of the cover, as the legacy did. */
    private fun updateCoverBackground(info: StreamInfo) {
        val url = ImageStrategy.choosePreferredImage(info.thumbnails)
        if (url == null || url == lastCoverUrl) {
            return
        }
        lastCoverUrl = url
        val appContext = applicationContext
        coverColorDisposables.clear()
        coverColorDisposables.add(
            Maybe.fromCallable {
                val bitmap = CoilHelper.loadBitmapBlocking(appContext, url, 0)
                bitmap?.let(::averageColor)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { color -> uiState.update { it.copy(coverColor = color or -0x1000000) } },
                    { }
                )
        )
    }

    private fun averageColor(source: Bitmap): Int {
        var bitmap = source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        return Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
    }

    companion object {
        private val TAG = YpPlayQueueActivity::class.java.simpleName
    }
}
