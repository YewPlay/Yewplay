/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.ui.fragment

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.HandlerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.nononsenseapps.filepicker.Utils
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.downloads.DownloadItem
import io.github.yewplay.feature.downloads.DownloadKind
import io.github.yewplay.feature.downloads.DownloadsActions
import io.github.yewplay.feature.downloads.DownloadsIcons
import io.github.yewplay.feature.downloads.DownloadsMenuEntry
import io.github.yewplay.feature.downloads.DownloadsScreen
import io.github.yewplay.feature.downloads.DownloadsStrings
import io.github.yewplay.feature.downloads.DownloadsUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.FilePickerActivityHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder
import us.shandian.giga.ui.common.Deleter
import us.shandian.giga.util.Utility

/**
 * YewPlay: Downloads (proposal, p. 15 and 19) in Compose. Downloads are still handled by the
 * giga download manager and its service; every action of the previous list is kept: pause,
 * resume, queue, retry, recover, errors, delete with undo, share, checksum and source.
 */
class YpDownloadsFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private val disposables = CompositeDisposable()
    private val uiState = MutableStateFlow(DownloadsUiState())

    private var binder: DownloadManagerBinder? = null
    private var manager: DownloadManager? = null
    private var iterator: DownloadManager.MissionIterator? = null
    private var deleter: Deleter? = null
    private var selectedTab = 0
    private var resumed = false

    /** Missions currently on screen, by key. */
    private var missions: Map<String, Mission> = emptyMap()

    /** Speed measure of each download in progress, as before (average of the last samples). */
    private val speeds = HashMap<DownloadMission, SpeedMeter>()

    /** Frame (or cover), duration and height of the finished files, read once. */
    private val media = HashMap<String, MediaInfo>()
    private val mediaLoading = HashSet<String>()

    /** Finished downloads hidden while "clear and delete files" can still be undone. */
    private val hidden = ArrayList<Mission>()
    private var clearSnackbar: Snackbar? = null
    private var recoverTarget: DownloadMission? = null

    private val saveAsLauncher = registerForActivityResult(StartActivityForResult(), ::onSaveAsResult)

    private val eventCallback = Handler.Callback {
        refresh()
        false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as DownloadManagerBinder
            this@YpDownloadsFragment.binder = binder
            binder.clearDownloadNotifications()
            val manager = binder.downloadManager
            this@YpDownloadsFragment.manager = manager
            val iterator = manager.iterator
            this@YpDownloadsFragment.iterator = iterator
            val view = view ?: return
            deleter = Deleter(view, view.context, ::refresh, manager, iterator, handler)
            if (resumed) {
                binder.addMissionEventListener(eventCallback)
                binder.enableNotifications(false)
            }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // bound with the view: the "undo" snackbars of deletions need it
        requireContext().bindService(
            Intent(requireContext(), DownloadManagerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by uiState.collectAsState()
                YpTheme(themeKey, nightThemeKey) {
                    DownloadsScreen(
                        state = state,
                        menu = listOf(
                            DownloadsMenuEntry(stringResource(R.string.start_downloads)) {
                                manager?.startAllMissions()
                                refresh()
                            },
                            DownloadsMenuEntry(stringResource(R.string.pause_downloads)) {
                                manager?.pauseAllMissions(false)
                                refresh()
                            },
                            DownloadsMenuEntry(
                                stringResource(R.string.clear_download_history),
                                ::showClearHistoryPrompt
                            )
                        ),
                        strings = DownloadsStrings(
                            title = stringResource(R.string.downloads_title),
                            back = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            tabs = listOf(
                                stringResource(R.string.yp_feed_all),
                                stringResource(R.string.yp_downloads_tab_videos),
                                stringResource(R.string.yp_downloads_tab_audios)
                            ),
                            downloading = stringResource(R.string.yp_downloads_downloading),
                            finished = stringResource(R.string.yp_downloads_finished),
                            pauseAll = stringResource(R.string.yp_downloads_pause_all),
                            resumeAll = stringResource(R.string.yp_downloads_resume_all),
                            itemCount = { count ->
                                resources.getQuantityString(R.plurals.yp_downloads_items, count, count)
                            },
                            empty = stringResource(R.string.yp_downloads_empty),
                            emptyDetail = stringResource(R.string.yp_downloads_empty_detail),
                            howToDownload = stringResource(R.string.yp_downloads_how)
                        ),
                        icons = DownloadsIcons(
                            back = painterResource(R.drawable.ic_arrow_back),
                            more = painterResource(R.drawable.ic_more_vert),
                            pause = painterResource(R.drawable.ic_pause),
                            play = painterResource(R.drawable.ic_play_arrow),
                            video = painterResource(R.drawable.ic_movie),
                            audio = painterResource(R.drawable.ic_headset),
                            other = painterResource(R.drawable.ic_subtitles),
                            download = painterResource(R.drawable.ic_file_download)
                        ),
                        actions = DownloadsActions(
                            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                            onTabSelect = { tab ->
                                selectedTab = tab
                                refresh()
                            },
                            onPauseAll = {
                                manager?.pauseAllMissions(false)
                                refresh()
                            },
                            onResumeAll = {
                                manager?.startAllMissions()
                                refresh()
                            },
                            onItemClick = ::onItemClick,
                            onItemToggle = ::onItemToggle,
                            onItemMenu = ::showItemMenu,
                            onHowToDownload = {
                                AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.yp_downloads_how)
                                    .setMessage(R.string.yp_downloads_how_text)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        )
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        deleter?.resume()
        binder?.addMissionEventListener(eventCallback)
        binder?.enableNotifications(false)
        speeds.values.forEach { it.reset() }
        scheduleUpdate(0)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        handler.removeCallbacksAndMessages(UPDATER)
        binder?.removeMissionEventListener(eventCallback)
        deleter?.pause()
        binder?.enableNotifications(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        deleter?.dispose()
        deleter = null
        binder?.let {
            it.removeMissionEventListener(eventCallback)
            it.enableNotifications(true)
        }
        // the connection stays registered until unbound, even before it connects
        requireContext().unbindService(connection)
        binder = null
        manager = null
        iterator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    /** Refreshes the progress of the downloads every second while the screen is shown. */
    private fun scheduleUpdate(delay: Long) {
        handler.removeCallbacksAndMessages(UPDATER)
        HandlerCompat.postDelayed(
            handler,
            {
                refresh()
                if (resumed) {
                    scheduleUpdate(UPDATE_INTERVAL)
                }
            },
            UPDATER,
            delay
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* State                                                                                     */
    /* ---------------------------------------------------------------------------------------- */

    private fun refresh() {
        val iterator = iterator ?: return
        val manager = manager ?: return
        if (!isAdded) {
            return
        }
        iterator.start()
        iterator.end()

        val pending = ArrayList<DownloadItem>()
        val finished = ArrayList<DownloadItem>()
        val onScreen = HashMap<String, Mission>()
        for (position in 0 until iterator.oldListSize) {
            val mission = iterator.getItem(position).mission ?: continue
            val kind = kindOf(mission)
            if (!matchesTab(kind)) {
                continue
            }
            val key = keyOf(mission)
            onScreen[key] = mission
            if (mission is DownloadMission) {
                pending.add(pendingItem(key, mission, kind))
            } else {
                finished.add(finishedItem(key, mission, kind))
            }
        }
        missions = onScreen
        speeds.keys.retainAll(onScreen.values.filterIsInstance<DownloadMission>().toSet())

        val running = iterator.hasValidPendingMissions()
        val used = manager.finishedBytes
        val free = try {
            StatFs(Environment.getExternalStorageDirectory().path).availableBytes
        } catch (e: IllegalArgumentException) {
            0L
        }
        uiState.value = DownloadsUiState(
            usedText = getString(R.string.yp_storage_used, Utility.formatBytes(used)),
            freeText = getString(R.string.yp_storage_free, Utility.formatBytes(free)),
            usedFraction = if (used + free <= 0) 0f else used.toFloat() / (used + free),
            selectedTab = selectedTab,
            pending = pending,
            finished = finished,
            canPauseAll = running[0],
            canResumeAll = !running[0] && running[1],
            isLoading = false
        )
    }

    private fun kindOf(mission: Mission): DownloadKind = when (Utility.getFileType(mission.kind, mission.storage.name)) {
        Utility.FileType.VIDEO -> DownloadKind.VIDEO
        Utility.FileType.MUSIC -> DownloadKind.AUDIO
        else -> DownloadKind.OTHER
    }

    private fun matchesTab(kind: DownloadKind) = when (selectedTab) {
        1 -> kind == DownloadKind.VIDEO
        2 -> kind == DownloadKind.AUDIO
        else -> true
    }

    private fun keyOf(mission: Mission): String = "${mission.timestamp}:${mission.storage.name}"

    private fun pendingItem(key: String, mission: DownloadMission, kind: DownloadKind): DownloadItem {
        val length = mission.getLength()
        val done = mission.done
        val hasError = mission.errCode != DownloadMission.ERROR_NOTHING
        val fraction = if (mission.unknownLength || length <= 0) null else done.toFloat() / length
        val sizes = if (mission.unknownLength || length <= 0) {
            Utility.formatBytes(done)
        } else {
            getString(R.string.yp_downloads_progress, Utility.formatBytes(done), Utility.formatBytes(length))
        }
        val state = when {
            hasError -> getString(R.string.msg_error)
            mission.isPsFailed -> null
            !mission.running -> getString(if (mission.enqueued) R.string.queued else R.string.paused)
            mission.isPsRunning -> getString(R.string.post_processing)
            mission.isRecovering -> getString(R.string.recovering)
            else -> null
        }
        val detail = if (state != null) {
            speeds[mission]?.reset()
            state.replaceFirstChar { it.uppercase() } + ", " + sizes
        } else {
            val meter = speeds.getOrPut(mission) { SpeedMeter() }
            val speed = meter.measure(done)
            if (speed == null) sizes else "$sizes, ${Utility.formatSpeed(speed.toDouble())}"
        }
        val togglable = !mission.hasInvalidStorage() && !mission.isPsRunning &&
            (mission.running || (!mission.isPsFailed && mission.urls.isNotEmpty()))
        return DownloadItem(
            key = key,
            title = mission.storage.name,
            kind = kind,
            progress = if (hasError) fraction ?: 1f else fraction?.coerceIn(0f, 1f),
            percentText = fraction?.let { "${(it * 100).toInt()}%" },
            detail = detail,
            running = mission.running && !hasError,
            hasError = hasError,
            canToggle = togglable
        )
    }

    private fun finishedItem(key: String, mission: Mission, kind: DownloadKind): DownloadItem {
        val info = media[key]
        if (info == null) {
            loadMedia(key, mission, kind)
        }
        val kindLabel = getString(
            when (kind) {
                DownloadKind.VIDEO -> R.string.yp_downloads_kind_video
                DownloadKind.AUDIO -> R.string.yp_downloads_kind_audio
                DownloadKind.OTHER -> R.string.yp_downloads_kind_subtitle
            }
        )
        val format = Utility.getFileExt(mission.storage.name)?.removePrefix(".")?.uppercase(Locale.ROOT)
        val quality = info?.height?.takeIf { it > 0 && kind == DownloadKind.VIDEO }?.let { "${it}p" }
        val detail = listOfNotNull(kindLabel, format, quality).joinToString(" ") +
            ", " + Utility.formatBytes(mission.length)
        return DownloadItem(
            key = key,
            title = mission.storage.name,
            kind = kind,
            thumbnail = info?.thumbnail,
            durationText = info?.durationSeconds?.takeIf { it > 0 }?.let { Localization.getDurationString(it) },
            detail = detail,
            date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(Date(mission.timestamp))
        )
    }

    /** Reads a frame (or the cover), the duration and the height of a finished file, once. */
    private fun loadMedia(key: String, mission: Mission, kind: DownloadKind) {
        if (kind == DownloadKind.OTHER || !mediaLoading.add(key)) {
            return
        }
        val context = requireContext().applicationContext
        disposables.add(
            Single.fromCallable { readMedia(context, mission, kind) }
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { info ->
                        media[key] = info
                        refresh()
                    },
                    { media[key] = MediaInfo(null, 0, 0) }
                )
        )
    }

    private fun readMedia(context: Context, mission: Mission, kind: DownloadKind): MediaInfo {
        val retriever = MediaMetadataRetriever()
        try {
            val storage = mission.storage
            if (storage.isDirect) {
                retriever.setDataSource(File(URI.create(storage.uri.toString())).absolutePath)
            } else {
                retriever.setDataSource(context, storage.uri)
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.div(1000) ?: 0L
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val frame: Bitmap? = if (kind == DownloadKind.VIDEO) {
                retriever.getFrameAtTime(FRAME_TIME_US)
            } else {
                retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            return MediaInfo(frame?.let(::scaleDown), duration, height)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= THUMBNAIL_WIDTH) {
            return bitmap
        }
        val height = bitmap.height * THUMBNAIL_WIDTH / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH, height, true)
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Item actions, as in the previous list                                                     */
    /* ---------------------------------------------------------------------------------------- */

    private fun onItemClick(item: DownloadItem) {
        when (val mission = missions[item.key]) {
            is FinishedMission -> openFile(mission)
            is DownloadMission -> if (item.hasError) showError(mission) else showItemMenu(item)
            else -> Unit
        }
    }

    private fun onItemToggle(item: DownloadItem) {
        val mission = missions[item.key] as? DownloadMission ?: return
        val manager = manager ?: return
        if (mission.running) {
            manager.pauseMission(mission)
        } else {
            manager.resumeMission(mission)
        }
        refresh()
    }

    private fun showItemMenu(item: DownloadItem) {
        val mission = missions[item.key] ?: return
        val entries = ArrayList<Pair<Int, () -> Unit>>()
        if (mission is DownloadMission) {
            if (mission.hasInvalidStorage()) {
                entries.add(R.string.retry to { retry(mission) })
                entries.add(R.string.delete_file to { delete(mission, true) })
                entries.add(R.string.show_error to { showError(mission) })
            } else if (mission.isPsRunning) {
                if (mission.errCode == DownloadMission.ERROR_INSUFFICIENT_STORAGE ||
                    mission.errCode == DownloadMission.ERROR_POSTPROCESSING_HOLD
                ) {
                    entries.add(R.string.retry to { retry(mission) })
                    entries.add(R.string.cancel to { mission.psContinue(false) })
                    entries.add(R.string.show_error to { showError(mission) })
                }
            } else if (mission.running) {
                entries.add(R.string.pause to { manager?.pauseMission(mission) })
            } else {
                val canStart = !mission.isPsFailed && mission.urls.isNotEmpty()
                if (canStart) {
                    entries.add(R.string.start to { manager?.resumeMission(mission) })
                    val queueLabel = if (mission.enqueued) R.string.yp_downloads_dequeue else R.string.enqueue
                    entries.add(queueLabel to { mission.setEnqueued(!mission.enqueued) })
                }
                if (mission.errCode != DownloadMission.ERROR_NOTHING) {
                    entries.add(R.string.show_error to { showError(mission) })
                }
                entries.add(R.string.delete_file to { delete(mission, true) })
            }
        } else {
            entries.add(R.string.share to { shareFile(mission) })
            entries.add(R.string.delete_file to { delete(mission, true) })
            entries.add(R.string.md5 to { checksum(mission, R.id.md5) })
            entries.add(R.string.sha1 to { checksum(mission, R.id.sha1) })
        }
        entries.add(R.string.delete_entry to { delete(mission, false) })
        if (!mission.source.isNullOrEmpty()) {
            entries.add(R.string.show_info to { openSource(mission) })
        }
        AlertDialog.Builder(requireContext())
            .setTitle(mission.storage.name)
            .setItems(entries.map { getString(it.first) }.toTypedArray()) { _, which ->
                entries[which].second()
                refresh()
            }
            .show()
    }

    private fun delete(mission: Mission, alsoDeleteFile: Boolean) {
        deleter?.append(mission, alsoDeleteFile)
        refresh()
    }

    private fun retry(mission: DownloadMission) {
        if (mission.isPsRunning) {
            mission.psContinue(true)
        } else {
            manager?.tryRecover(mission)
            if (mission.storage.isInvalid) {
                pickNewFile(mission)
            } else {
                recover(mission)
            }
        }
    }

    private fun recover(mission: DownloadMission) {
        mission.errObject = null
        mission.resetState(true, false, DownloadMission.ERROR_NOTHING)
        manager?.resumeMission(mission)
        refresh()
    }

    /** The file of a download is gone: ask where to save it again, as before. */
    private fun pickNewFile(mission: DownloadMission) {
        recoverTarget = mission
        val context = requireContext()
        val initialPath = if (NewPipeSettings.useStorageAccessFramework(context)) {
            null
        } else {
            val dir = if (DownloadManager.TAG_AUDIO == mission.storage.type) {
                NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC)
            } else {
                NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES)
            }
            Uri.parse(dir.absolutePath)
        }
        NoFileManagerSafeGuard.launchSafe(
            saveAsLauncher,
            StoredFileHelper.getNewPicker(context, mission.storage.name, mission.storage.type, initialPath),
            TAG,
            context
        )
    }

    private fun onSaveAsResult(result: ActivityResult) {
        val mission = recoverTarget ?: return
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        try {
            var fileUri = data.data ?: return
            if (fileUri.authority != null && FilePickerActivityHelper.isOwnFileUri(requireContext(), fileUri)) {
                fileUri = Uri.fromFile(Utils.getFileForUri(fileUri))
            }
            mission.storage = StoredFileHelper(requireContext(), null, fileUri, mission.storage.tag)
            recover(mission)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun showError(mission: DownloadMission) {
        val message: Int = when (mission.errCode) {
            416 -> R.string.error_http_unsupported_range

            404 -> R.string.error_http_not_found

            DownloadMission.ERROR_NOTHING -> return

            DownloadMission.ERROR_FILE_CREATION -> R.string.error_file_creation

            DownloadMission.ERROR_HTTP_NO_CONTENT -> R.string.error_http_no_content

            DownloadMission.ERROR_PATH_CREATION -> R.string.error_path_creation

            DownloadMission.ERROR_PERMISSION_DENIED -> R.string.permission_denied

            DownloadMission.ERROR_SSL_EXCEPTION -> R.string.error_ssl_exception

            DownloadMission.ERROR_UNKNOWN_HOST -> R.string.error_unknown_host

            DownloadMission.ERROR_CONNECT_HOST -> R.string.error_connect_host

            DownloadMission.ERROR_POSTPROCESSING_STOPPED -> R.string.error_postprocessing_stopped

            DownloadMission.ERROR_POSTPROCESSING, DownloadMission.ERROR_POSTPROCESSING_HOLD -> {
                report(mission, UserAction.DOWNLOAD_POSTPROCESSING, R.string.error_postprocessing_failed)
                return
            }

            DownloadMission.ERROR_INSUFFICIENT_STORAGE -> R.string.error_insufficient_storage_left

            DownloadMission.ERROR_UNKNOWN_EXCEPTION -> if (mission.errObject != null) {
                report(mission, UserAction.DOWNLOAD_FAILED, R.string.general_error)
                return
            } else {
                R.string.msg_error
            }

            DownloadMission.ERROR_PROGRESS_LOST -> R.string.error_progress_lost

            DownloadMission.ERROR_TIMEOUT -> R.string.error_timeout

            DownloadMission.ERROR_RESOURCE_GONE -> R.string.error_download_resource_gone

            else -> {
                if (mission.errCode !in 100 until 600 && mission.errObject != null) {
                    report(mission, UserAction.DOWNLOAD_FAILED, R.string.general_error)
                    return
                }
                0
            }
        }
        val builder = AlertDialog.Builder(requireContext())
        if (message == 0) {
            builder.setMessage(
                if (mission.errCode in 100 until 600) "HTTP ${mission.errCode}" else "(not_decelerated_error_code)"
            )
        } else {
            builder.setMessage(message)
        }
        if (mission.errObject != null && mission.errCode !in 100 until 600) {
            builder.setPositiveButton(R.string.error_report_title) { _, _ ->
                report(mission, UserAction.DOWNLOAD_FAILED, if (message == 0) R.string.general_error else message)
            }
        }
        builder.setNegativeButton(R.string.ok, null)
            .setTitle(mission.storage.name)
            .show()
    }

    private fun report(mission: DownloadMission, action: UserAction, reason: Int) {
        val request = StringBuilder(mission.source.orEmpty()).append(" [")
        mission.recoveryInfo?.forEach { request.append(' ').append(it.toString()).append(' ') }
        request.append("]")
        val service = runCatching { NewPipe.getServiceByUrl(mission.source).serviceId }.getOrNull()
        ErrorUtil.createNotification(
            requireContext(),
            ErrorInfo(
                mission.errObject?.let { ErrorInfo.throwableToStringList(it) } ?: emptyArray(),
                action,
                request.toString(),
                service,
                reason
            )
        )
    }

    private fun openFile(mission: Mission) {
        if (isMissing(mission)) {
            return
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(shareableUri(mission), mimeType(mission))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        val chooser = Intent.createChooser(view, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        ShareUtils.openIntentInApp(requireContext(), chooser)
    }

    private fun shareFile(mission: Mission) {
        if (isMissing(mission)) {
            return
        }
        val share = Intent(Intent.ACTION_SEND)
            .setType(mimeType(mission))
            .putExtra(Intent.EXTRA_STREAM, shareableUri(mission))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(share, null)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            chooser.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_dialog_title))
        }
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(chooser)
    }

    private fun shareableUri(mission: Mission): Uri = if (mission.storage.isDirect) {
        FileProvider.getUriForFile(
            requireContext(),
            BuildConfig.APPLICATION_ID + ".provider",
            File(URI.create(mission.storage.uri.toString()))
        )
    } else {
        mission.storage.uri
    }

    private fun mimeType(mission: Mission): String {
        if (!mission.storage.isInvalid) {
            val type = mission.storage.type
            if (!type.isNullOrEmpty() && type != StoredFileHelper.DEFAULT_MIME) {
                return type
            }
        }
        val ext = Utility.getFileExt(mission.storage.name) ?: return DEFAULT_MIME_TYPE
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1)) ?: DEFAULT_MIME_TYPE
    }

    private fun isMissing(mission: Mission): Boolean {
        if (mission.storage.existsAsFile()) {
            return false
        }
        Toast.makeText(requireContext(), R.string.missing_file, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun checksum(mission: Mission, algorithm: Int) {
        val context = requireContext()
        val storage = mission.storage
        if (!storage.existsAsFile()) {
            Toast.makeText(context, R.string.missing_file, Toast.LENGTH_SHORT).show()
            delete(mission, true)
            return
        }
        val notifications = ContextCompat.getSystemService(context, NotificationManager::class.java)
        notifications?.notify(
            HASH_NOTIFICATION_ID,
            NotificationCompat.Builder(context, context.getString(R.string.hash_channel_id))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_yewplay)
                .setContentTitle(context.getString(R.string.msg_calculating_hash))
                .setContentText(context.getString(R.string.msg_wait))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build()
        )
        disposables.add(
            Single.fromCallable { Utility.checksum(storage, algorithm) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        ShareUtils.copyToClipboard(context, result)
                        notifications?.cancel(HASH_NOTIFICATION_ID)
                    },
                    { notifications?.cancel(HASH_NOTIFICATION_ID) }
                )
        )
    }

    private fun openSource(mission: Mission) {
        try {
            val intent = NavigationHelper.getIntentByLink(requireContext(), mission.source)
            intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_SHORT).show()
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Clear history, as before                                                                  */
    /* ---------------------------------------------------------------------------------------- */

    private fun showClearHistoryPrompt() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_download_history)
            .setMessage(R.string.confirm_prompt)
            // the buttons are used out of their purpose to get a good order, as before
            .setNegativeButton(R.string.clear_download_history) { _, _ -> clearFinished(false) }
            .setNeutralButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_downloaded_files) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_downloaded_files_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ -> clearFinished(true) }
                    .show()
            }
            .show()
    }

    private fun clearFinished(deleteFiles: Boolean) {
        val iterator = iterator ?: return
        val manager = manager ?: return
        if (!deleteFiles) {
            manager.forgetFinishedDownloads()
            refresh()
            return
        }
        if (!iterator.hasFinishedMissions() || hidden.isNotEmpty()) {
            return
        }
        for (position in 0 until iterator.oldListSize) {
            val mission = iterator.getItem(position).mission as? FinishedMission ?: continue
            iterator.hide(mission)
            hidden.add(mission)
        }
        refresh()
        val snackbar = Snackbar.make(
            requireView(),
            Localization.deletedDownloadCount(requireContext(), hidden.size),
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.setAction(R.string.undo) {
            hidden.forEach { iterator.unHide(it) }
            hidden.clear()
            handler.removeCallbacksAndMessages(DELETE)
            refresh()
        }
        snackbar.show()
        clearSnackbar = snackbar
        HandlerCompat.postDelayed(handler, ::deleteHidden, DELETE, UNDO_TIMEOUT)
    }

    private fun deleteHidden() {
        clearSnackbar?.dismiss()
        val context = context ?: return
        hidden.forEach { mission ->
            manager?.deleteMission(mission, true)
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mission.storage.uri))
        }
        hidden.clear()
        refresh()
    }

    /** Average speed of the last samples, as the previous list measured it. */
    private class SpeedMeter {
        private var lastTime = -1L
        private var lastDone = 0L
        private val samples = FloatArray(3)
        private var index = -1

        fun reset() {
            lastTime = -1L
            index = -1
        }

        /** @return bytes per second, or null until there are two samples */
        fun measure(done: Long): Float? {
            val now = System.currentTimeMillis()
            if (lastTime < 0 || done < lastDone) {
                lastTime = now
                lastDone = done
                return null
            }
            val deltaTime = now - lastTime
            val deltaDone = done - lastDone
            if (deltaDone <= 0 || deltaTime <= 0) {
                return if (index < 0) null else samples.average().toFloat()
            }
            val speed = deltaDone * 1000f / deltaTime
            if (index < 0) {
                samples.fill(speed)
                index = 0
            }
            val average = (speed + samples.sum()) / (samples.size + 1)
            samples[index] = speed
            index = (index + 1) % samples.size
            lastTime = now
            lastDone = done
            return average
        }
    }

    private class MediaInfo(val thumbnail: Bitmap?, val durationSeconds: Long, val height: Int)

    private companion object {
        const val TAG = "YpDownloadsFragment"
        const val UPDATER = "updater"
        const val DELETE = "deleteFinishedDownloads"
        const val UPDATE_INTERVAL = 1000L
        const val UNDO_TIMEOUT = 5000L
        const val HASH_NOTIFICATION_ID = 123790
        const val DEFAULT_MIME_TYPE = "*/*"
        const val THUMBNAIL_WIDTH = 320
        const val FRAME_TIME_US = 1_000_000L
    }
}
