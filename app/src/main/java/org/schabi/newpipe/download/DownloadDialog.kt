/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.collection.SparseArrayCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.evernote.android.state.State
import com.livefront.bridge.Bridge
import com.nononsenseapps.filepicker.Utils
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetTitle
import io.github.yewplay.core.designsystem.YpSlider
import io.github.yewplay.core.designsystem.YpTextField
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.File
import java.io.IOException
import java.util.Locale
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.AudioTrackAdapter.AudioTracksWrapper
import org.schabi.newpipe.util.FilePickerActivityHelper
import org.schabi.newpipe.util.FilenameUtils
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.SecondaryStreamHelper
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder
import us.shandian.giga.service.MissionState
import us.shandian.giga.util.Utility

/**
 * YewPlay: "Download" as a Compose bottom sheet: the file name, video/audio/subtitles, the
 * audio track, the quality (with format and size) and the number of threads. Choosing the
 * folder, checking the file and starting the download work as in NewPipe.
 */
class DownloadDialog() : DialogFragment() {
    private enum class Kind { VIDEO, AUDIO, SUBTITLE }

    @State
    @JvmField
    var currentInfo: StreamInfo? = null

    @State
    @JvmField
    var wrappedVideoStreams: StreamInfoWrapper<VideoStream> = StreamInfoWrapper.empty()

    @State
    @JvmField
    var wrappedSubtitleStreams: StreamInfoWrapper<SubtitlesStream> = StreamInfoWrapper.empty()

    @State
    @JvmField
    var wrappedAudioTracks: AudioTracksWrapper? = null

    private var selectedAudioTrackIndex by mutableIntStateOf(0)

    private var selectedVideoIndex by mutableIntStateOf(0)

    private var selectedAudioIndex by mutableIntStateOf(0)

    private var selectedSubtitleIndex by mutableIntStateOf(0)

    private var selectedKindOrdinal by mutableIntStateOf(-1)

    @State
    @JvmField
    var fileNameState: String? = null

    private var mainStorageAudio: StoredDirectoryHelper? = null
    private var mainStorageVideo: StoredDirectoryHelper? = null
    private var downloadManager: DownloadManager? = null
    private var askForSavePath = false
    private var ctx: Context? = null

    private var secondaryStreams = SparseArrayCompat<SecondaryStreamHelper<AudioStream>>(4)
    private val disposables = CompositeDisposable()

    // file name and MIME type kept while a new folder is picked
    private var filenameTmp: String = ""
    private var mimeTmp: String? = null

    // Compose state: the selection above and [refresh], which changes when the stream sizes
    // arrive, so that the sheet redraws
    private var refresh by mutableIntStateOf(0)
    private var serviceReady by mutableStateOf(false)
    private var fileName by mutableStateOf("")
    private var threads by mutableIntStateOf(DEFAULT_THREADS)

    private val requestDownloadSaveAsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::requestDownloadSaveAsResult)
    private val requestDownloadPickAudioFolderLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) {
            requestDownloadPickFolderResult(it, getString(R.string.download_path_audio_key), DownloadManager.TAG_AUDIO)
        }
    private val requestDownloadPickVideoFolderLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) {
            requestDownloadPickFolderResult(it, getString(R.string.download_path_video_key), DownloadManager.TAG_VIDEO)
        }

    /**
     * A download dialog with the video, audio and subtitle streams of [info]. Video and
     * video-only streams go in a single list, sorted by resolution, with the default
     * resolution selected.
     */
    constructor(context: Context, info: StreamInfo) : this() {
        currentInfo = info
        val audioStreams = ListHelper.getStreamsOfSpecifiedDelivery(info.audioStreams, DeliveryMethod.PROGRESSIVE_HTTP)
        val groupedAudioStreams = ListHelper.getGroupedAudioStreams(context, audioStreams)
        val tracks = AudioTracksWrapper(groupedAudioStreams, context)
        wrappedAudioTracks = tracks
        selectedAudioTrackIndex = ListHelper.getDefaultAudioTrackGroup(context, groupedAudioStreams)

        // TODO: adapt this code when the downloader supports other stream deliveries
        val videoStreams = ListHelper.getSortedStreamVideosList(
            context,
            ListHelper.getStreamsOfSpecifiedDelivery(info.videoStreams, DeliveryMethod.PROGRESSIVE_HTTP),
            ListHelper.getStreamsOfSpecifiedDelivery(info.videoOnlyStreams, DeliveryMethod.PROGRESSIVE_HTTP),
            false,
            // with several languages, prefer streams without audio to allow choosing the language
            tracks.size() > 1
        )
        wrappedVideoStreams = StreamInfoWrapper(videoStreams, context)
        wrappedSubtitleStreams = StreamInfoWrapper(
            ListHelper.getStreamsOfSpecifiedDelivery(info.subtitles, DeliveryMethod.PROGRESSIVE_HTTP),
            context
        )
        selectedVideoIndex = ListHelper.getDefaultResolutionIndex(context, videoStreams)
    }

    // ---- Lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        }
        if (!PermissionHelper.checkStoragePermissions(activity, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            dismiss()
            return
        }
        val context = requireContext()
        ctx = context
        Bridge.restoreInstanceState(this, savedInstanceState)
        savedInstanceState?.getIntArray(KEY_SELECTION)?.let {
            selectedKindOrdinal = it[0]
            selectedAudioTrackIndex = it[1]
            selectedVideoIndex = it[2]
            selectedAudioIndex = it[3]
            selectedSubtitleIndex = it[4]
        }
        if (currentInfo == null) {
            dismiss()
            return
        }
        updateSecondaryStreams()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        threads = prefs.getInt(getString(R.string.default_download_threads), DEFAULT_THREADS)
        fileName = fileNameState ?: FilenameUtils.createFilename(context, currentInfo!!.name)
        if (savedInstanceState == null) {
            selectedAudioIndex = ListHelper.getDefaultAudioFormat(context, wrappedAudioStreams().streamsList)
            selectedSubtitleIndex = subtitleIndexByLocale(wrappedSubtitleStreams.streamsList)
        }
        if (selectedKindOrdinal < 0) {
            selectedKindOrdinal = defaultKind()?.ordinal ?: run {
                Toast.makeText(context, R.string.no_streams_available_download, Toast.LENGTH_SHORT).show()
                dismiss()
                return
            }
        }

        val intent = Intent(context, DownloadManagerService::class.java)
        context.startService(intent)
        context.bindService(
            intent,
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val binder = service as DownloadManagerBinder
                    mainStorageAudio = binder.mainStorageAudio
                    mainStorageVideo = binder.mainStorageVideo
                    downloadManager = binder.downloadManager
                    askForSavePath = binder.askForSavePath()
                    serviceReady = true
                    context.unbindService(this)
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            },
            Context.BIND_AUTO_CREATE
        )
        fetchStreamsSize()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fileNameState = fileName
        Bridge.saveInstanceState(this, outState)
        outState.putIntArray(
            KEY_SELECTION,
            intArrayOf(
                selectedKindOrdinal,
                selectedAudioTrackIndex,
                selectedVideoIndex,
                selectedAudioIndex,
                selectedSubtitleIndex
            )
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = ctx ?: return super.onCreateDialog(savedInstanceState)
        return YpDialogs.sheet(context) { dismiss ->
            YpSheet {
                DownloadContent(onCancel = dismiss)
            }
        }
    }

    // ---- Streams ----

    private val selectedKind: Kind get() = Kind.entries[selectedKindOrdinal.coerceAtLeast(0)]

    private fun invalidate() {
        refresh++
    }

    private fun wrappedAudioStreams(): StreamInfoWrapper<AudioStream> {
        val tracks = wrappedAudioTracks ?: return StreamInfoWrapper.empty()
        if (selectedAudioTrackIndex < 0 || selectedAudioTrackIndex >= tracks.size()) {
            return StreamInfoWrapper.empty()
        }
        return tracks.tracksList[selectedAudioTrackIndex]
    }

    /** Pairs each video-only stream with the audio stream of the selected track. */
    private fun updateSecondaryStreams() {
        val audioStreams = wrappedAudioStreams()
        val secondary = SparseArrayCompat<SecondaryStreamHelper<AudioStream>>(4)
        val videoStreams = wrappedVideoStreams.streamsList
        wrappedVideoStreams.resetInfo()
        videoStreams.forEachIndexed { i, video ->
            if (!video.isVideoOnly) {
                return@forEachIndexed
            }
            val audio = SecondaryStreamHelper.getAudioStreamFor(requireContext(), audioStreams.streamsList, video)
            if (audio != null) {
                secondary.append(i, SecondaryStreamHelper(audioStreams, audio))
            } else if (DEBUG) {
                Log.w(TAG, "No audio stream candidates for video format " + (video.format?.name ?: "unknown"))
            }
        }
        secondaryStreams = secondary
    }

    private fun fetchStreamsSize() {
        disposables.clear()
        val info = currentInfo
        listOf(
            wrappedVideoStreams to "Downloading video stream size",
            wrappedAudioStreams() to "Downloading audio stream size",
            wrappedSubtitleStreams to "Downloading subtitle stream size"
        ).forEach { (wrapper, request) ->
            disposables.add(
                StreamInfoWrapper.fetchMoreInfoForWrapper(wrapper).subscribe(
                    { invalidate() },
                    { error ->
                        ErrorUtil.showSnackbar(
                            requireContext(),
                            ErrorInfo(error, UserAction.DOWNLOAD_OPEN_DIALOG, request, info)
                        )
                    }
                )
            )
        }
    }

    private fun defaultKind(): Kind? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val last = prefs.getString(
            getString(R.string.last_used_download_type),
            getString(R.string.last_download_type_video_key)
        )
        val hasVideo = wrappedVideoStreams.streamsList.isNotEmpty()
        val hasAudio = wrappedAudioStreams().streamsList.isNotEmpty()
        val hasSubtitles = wrappedSubtitleStreams.streamsList.isNotEmpty()
        return when {
            hasVideo && last == getString(R.string.last_download_type_video_key) -> Kind.VIDEO
            hasAudio && last == getString(R.string.last_download_type_audio_key) -> Kind.AUDIO
            hasSubtitles && last == getString(R.string.last_download_type_subtitle_key) -> Kind.SUBTITLE
            hasVideo -> Kind.VIDEO
            hasAudio -> Kind.AUDIO
            hasSubtitles -> Kind.SUBTITLE
            else -> null
        }
    }

    private fun subtitleIndexByLocale(streams: List<SubtitlesStream>): Int {
        val preferred = NewPipe.getPreferredLocalization()
        var candidate = 0
        streams.forEachIndexed { i, stream ->
            val locale = stream.locale
            val languageEquals = preferred.languageCode != null &&
                locale.language == Locale(preferred.languageCode).language
            val countryEquals = locale.country == preferred.countryCode
            if (languageEquals) {
                if (countryEquals) {
                    return i
                }
                candidate = i
            }
        }
        return candidate
    }

    private fun selectKind(kind: Kind) {
        selectedKindOrdinal = kind.ordinal
        updateFileNameForSelection()
        invalidate()
    }

    private fun selectAudioTrack(index: Int) {
        if (index != selectedAudioTrackIndex) {
            selectedAudioTrackIndex = index
            updateSecondaryStreams()
            fetchStreamsSize()
            invalidate()
        }
    }

    private fun selectQuality(index: Int) {
        when (selectedKind) {
            Kind.VIDEO -> selectedVideoIndex = index
            Kind.AUDIO -> selectedAudioIndex = index
            Kind.SUBTITLE -> selectedSubtitleIndex = index
        }
        updateFileNameForSelection()
        invalidate()
    }

    /** Updates the file name, unless the user has edited it. */
    private fun updateFileNameForSelection() {
        val base = FilenameUtils.createFilename(requireContext(), currentInfo!!.name)
        val previous = fileName
        val editedByUser = previous.isNotEmpty() && previous != base &&
            !previous.startsWith(getString(R.string.caption_file_name, base, ""))
        if (editedByUser) {
            return
        }
        fileName = when (selectedKind) {
            Kind.VIDEO, Kind.AUDIO -> base

            Kind.SUBTITLE -> {
                val language = wrappedSubtitleStreams.streamsList.getOrNull(selectedSubtitleIndex)?.languageTag
                getString(R.string.caption_file_name, base, language)
            }
        }
    }

    /** Quality, format and size of each choice of the selected kind. */
    private fun qualityOptions(context: Context): List<Triple<String, String, String?>> = when (selectedKind) {
        Kind.VIDEO -> wrappedVideoStreams.streamsList.mapIndexed { i, stream ->
            val noSound = stream.isVideoOnly && secondaryStreams.get(i) == null
            val quality = stream.resolution + if (noSound) " · " + context.getString(R.string.yp_download_no_sound) else ""
            Triple(quality, formatName(context, wrappedVideoStreams.getFormat(i)), sizeText(wrappedVideoStreams, i))
        }

        Kind.AUDIO -> {
            val wrapper = wrappedAudioStreams()
            wrapper.streamsList.mapIndexed { i, stream ->
                val quality = if (stream.averageBitrate > 0) {
                    "${stream.averageBitrate}kbps"
                } else {
                    context.getString(R.string.unknown_quality)
                }
                Triple(quality, formatName(context, wrapper.getFormat(i)), sizeText(wrapper, i))
            }
        }

        Kind.SUBTITLE -> wrappedSubtitleStreams.streamsList.mapIndexed { i, stream ->
            var quality = stream.displayLanguageName
            if (stream.isAutoGenerated) {
                quality += " (" + context.getString(R.string.caption_auto_generated) + ")"
            }
            Triple(quality, stream.languageTag, sizeText(wrappedSubtitleStreams, i))
        }
    }

    private fun formatName(context: Context, format: MediaFormat?): String = when (format) {
        null -> context.getString(R.string.unknown_format)
        MediaFormat.WEBMA_OPUS -> "opus"
        else -> format.getName()
    }

    private fun <T : Stream> sizeText(wrapper: StreamInfoWrapper<T>, position: Int): String? {
        if (wrapper.getSizeInBytes(position) <= 0) {
            return null
        }
        val secondary = if (wrapper === wrappedVideoStreams) secondaryStreams.get(position) else null
        return if (secondary != null) {
            Utility.formatBytes(secondary.sizeInBytes + wrapper.getSizeInBytes(position))
        } else {
            wrapper.getFormattedSize(position)
        }
    }

    // ---- UI ----

    @Composable
    private fun DownloadContent(onCancel: () -> Unit) {
        val context = requireContext()
        val tracks = wrappedAudioTracks
        val selectedQuality = when (selectedKind) {
            Kind.VIDEO -> selectedVideoIndex
            Kind.AUDIO -> selectedAudioIndex
            Kind.SUBTITLE -> selectedSubtitleIndex
        }
        val selectedVideoOnly = wrappedVideoStreams.streamsList.getOrNull(selectedVideoIndex)?.isVideoOnly == true
        val showTracks = tracks != null && tracks.size() > 1 &&
            (selectedKind == Kind.AUDIO || (selectedKind == Kind.VIDEO && selectedVideoOnly))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            YpSheetTitle(stringResource(R.string.download_dialog_title))
            YpTextField(
                value = fileName,
                onValueChange = { fileName = it },
                placeholder = stringResource(R.string.msg_name),
                horizontalPadding = YpScreenMargin
            )
            Row(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (wrappedVideoStreams.streamsList.isNotEmpty()) {
                    KindChip(stringResource(R.string.video), selectedKind == Kind.VIDEO) { selectKind(Kind.VIDEO) }
                }
                if (wrappedAudioStreams().streamsList.isNotEmpty()) {
                    KindChip(stringResource(R.string.audio), selectedKind == Kind.AUDIO) { selectKind(Kind.AUDIO) }
                }
                if (wrappedSubtitleStreams.streamsList.isNotEmpty()) {
                    KindChip(stringResource(R.string.caption_setting_title), selectedKind == Kind.SUBTITLE) {
                        selectKind(Kind.SUBTITLE)
                    }
                }
            }
            if (showTracks) {
                SectionLabel(stringResource(R.string.yp_download_audio_track))
                Row(
                    modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tracks!!.tracksList.forEachIndexed { index, wrapper ->
                        val first = wrapper.streamsList.firstOrNull() ?: return@forEachIndexed
                        KindChip(Localization.audioTrackName(context, first), index == selectedAudioTrackIndex) {
                            selectAudioTrack(index)
                        }
                    }
                }
            } else if (selectedKind == Kind.VIDEO && tracks != null && tracks.size() > 1) {
                Text(
                    modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 4.dp),
                    text = stringResource(R.string.audio_track_present_in_video),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionLabel(stringResource(R.string.yp_download_quality))
            Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                // [refresh] changes when the stream sizes arrive
                val options = remember(refresh, selectedKindOrdinal, selectedAudioTrackIndex) { qualityOptions(context) }
                options.forEachIndexed { index, (quality, format, size) ->
                    QualityRow(quality, format, size, index == selectedQuality) { selectQuality(index) }
                }
            }
            if (selectedKind != Kind.SUBTITLE) {
                SectionLabel(stringResource(R.string.msg_threads) + " · " + threads)
                YpSlider(
                    modifier = Modifier.padding(horizontal = YpScreenMargin),
                    value = threads.toFloat(),
                    valueRange = 1f..MAX_THREADS.toFloat(),
                    steps = MAX_THREADS - 2,
                    onValueChange = { value ->
                        threads = value.toInt()
                        PreferenceManager.getDefaultSharedPreferences(context).edit {
                            putInt(getString(R.string.default_download_threads), threads)
                        }
                    }
                )
            }
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 6.dp),
                text = stringResource(R.string.streams_not_yet_supported_removed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YpScreenMargin, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                YpPillButton(label = stringResource(R.string.cancel), primary = false, onClick = onCancel)
                YpPillButton(
                    label = stringResource(R.string.download),
                    primary = true,
                    enabled = serviceReady,
                    onClick = ::prepareSelectedDownload
                )
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            modifier = Modifier.padding(start = YpScreenMargin, end = YpScreenMargin, top = 10.dp, bottom = 4.dp),
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    @Composable
    private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else colors.surfaceContainerHighest)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun QualityRow(quality: String, format: String, size: String?, selected: Boolean, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = YpScreenMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        modifier = Modifier.size(12.dp),
                        painter = painterResource(R.drawable.ic_done),
                        contentDescription = null,
                        tint = colors.onPrimary
                    )
                }
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                text = quality,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceContainerHighest)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = format,
                style = MaterialTheme.typography.labelSmall
            )
            if (size != null) {
                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = size,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }

    // ---- Messages ----

    private fun showFailedDialog(@StringRes message: Int) {
        val context = ctx ?: return
        YpDialogs.card(context) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.general_error),
                message = stringResource(message),
                confirmLabel = stringResource(R.string.ok),
                onConfirm = dismiss
            )
        }.show()
    }

    private fun askToContinue(@StringRes message: Int, @StringRes button: Int?, onConfirm: () -> Unit) {
        val context = ctx ?: return
        YpDialogs.card(context) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.download_dialog_title),
                message = stringResource(message),
                confirmLabel = button?.let { stringResource(it) },
                onConfirm = {
                    dismiss()
                    onConfirm()
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            )
        }.show()
    }

    // ---- Activity results ----

    private fun requestDownloadSaveAsResult(result: ActivityResult) {
        val context = ctx ?: return
        if (result.resultCode != Activity.RESULT_OK) {
            return
        }
        val uri = result.data?.data
        if (uri == null) {
            showFailedDialog(R.string.general_error)
            return
        }
        if (FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            val file = Utils.getFileForUri(uri)
            checkSelectedDownload(null, Uri.fromFile(file), file.name, StoredFileHelper.DEFAULT_MIME)
            return
        }
        val docFile = DocumentFile.fromSingleUri(context, uri)
        if (docFile == null) {
            showFailedDialog(R.string.general_error)
            return
        }
        // check if the selected file was previously used
        checkSelectedDownload(null, uri, docFile.name, docFile.type)
    }

    private fun requestDownloadPickFolderResult(result: ActivityResult, key: String, tag: String) {
        val context = ctx ?: return
        if (result.resultCode != Activity.RESULT_OK) {
            return
        }
        var uri = result.data?.data
        if (uri == null) {
            showFailedDialog(R.string.general_error)
            return
        }
        if (FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            uri = Uri.fromFile(Utils.getFileForUri(uri))
        } else {
            context.grantUriPermission(context.packageName, uri, StoredDirectoryHelper.PERMISSION_FLAGS)
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit { putString(key, uri.toString()) }
        try {
            val mainStorage = StoredDirectoryHelper(context, uri, tag)
            checkSelectedDownload(mainStorage, mainStorage.findFile(filenameTmp), filenameTmp, mimeTmp)
        } catch (e: IOException) {
            showFailedDialog(R.string.general_error)
        }
    }

    // ---- Download ----

    private fun nameForFile(): String {
        val name = fileName.trim()
        return FilenameUtils.createFilename(requireContext(), name.ifEmpty { currentInfo!!.name })
    }

    private fun launchDirectoryPicker(launcher: ActivityResultLauncher<Intent>) {
        val context = ctx ?: return
        NoFileManagerSafeGuard.launchSafe(launcher, StoredDirectoryHelper.getPicker(context), TAG, context)
    }

    private fun prepareSelectedDownload() {
        val context = ctx ?: return
        val mainStorage: StoredDirectoryHelper?
        val format: MediaFormat?
        val selectedMediaType: String
        val size: Long

        // first the file name and the output folder (if possible), then the long file checks
        filenameTmp = nameForFile() + "."
        mimeTmp = null
        when (selectedKind) {
            Kind.AUDIO -> {
                selectedMediaType = getString(R.string.last_download_type_audio_key)
                mainStorage = mainStorageAudio
                format = wrappedAudioStreams().streamsList[selectedAudioIndex].format
                size = wrappedAudioStreams().getSizeInBytes(selectedAudioIndex)
                if (format == MediaFormat.WEBMA_OPUS) {
                    mimeTmp = "audio/ogg"
                    filenameTmp += "opus"
                } else if (format != null) {
                    mimeTmp = format.mimeType
                    filenameTmp += format.suffix
                }
            }

            Kind.VIDEO -> {
                selectedMediaType = getString(R.string.last_download_type_video_key)
                mainStorage = mainStorageVideo
                format = wrappedVideoStreams.streamsList[selectedVideoIndex].format
                size = wrappedVideoStreams.getSizeInBytes(selectedVideoIndex)
                if (format != null) {
                    mimeTmp = format.mimeType
                    filenameTmp += format.suffix
                }
            }

            Kind.SUBTITLE -> {
                selectedMediaType = getString(R.string.last_download_type_subtitle_key)
                mainStorage = mainStorageVideo // subtitle and video files go together
                format = wrappedSubtitleStreams.streamsList[selectedSubtitleIndex].format
                size = wrappedSubtitleStreams.getSizeInBytes(selectedSubtitleIndex)
                if (format != null) {
                    mimeTmp = format.mimeType
                }
                if (format == MediaFormat.TTML) {
                    filenameTmp += MediaFormat.SRT.suffix
                } else if (format != null) {
                    filenameTmp += format.suffix
                }
            }
        }

        val needsFolder = mainStorage == null ||
            mainStorage.isDirect == NewPipeSettings.useStorageAccessFramework(context) ||
            mainStorage.isInvalidSafStorage
        if (!askForSavePath && needsFolder) {
            // pick a new download folder when: none is set, it uses SAF while SAF is off (or the
            // other way round), or the user revoked the access to it
            Toast.makeText(context, getString(R.string.no_dir_yet), Toast.LENGTH_LONG).show()
            if (selectedKind == Kind.AUDIO) {
                launchDirectoryPicker(requestDownloadPickAudioFolderLauncher)
            } else {
                launchDirectoryPicker(requestDownloadPickVideoFolderLauncher)
            }
            return
        }

        if (askForSavePath) {
            val initialPath = if (NewPipeSettings.useStorageAccessFramework(context)) {
                null
            } else {
                val initialSavePath: File = if (selectedKind == Kind.AUDIO) {
                    NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC)
                } else {
                    NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES)
                }
                Uri.parse(initialSavePath.absolutePath)
            }
            NoFileManagerSafeGuard.launchSafe(
                requestDownloadSaveAsLauncher,
                StoredFileHelper.getNewPicker(context, filenameTmp, mimeTmp ?: StoredFileHelper.DEFAULT_MIME, initialPath),
                TAG,
                context
            )
            return
        }

        // check the free storage space
        if (mainStorage!!.freeStorageSpace <= size) {
            Toast.makeText(context, getString(R.string.error_insufficient_storage), Toast.LENGTH_LONG).show()
            // take the user to the storage settings
            val storageSettings = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            if (storageSettings.resolveActivity(context.packageManager) != null) {
                startActivity(storageSettings)
            }
            return
        }

        // check for an existing file with the same name
        checkSelectedDownload(mainStorage, mainStorage.findFile(filenameTmp), filenameTmp, mimeTmp)

        // remember the last media type downloaded by the user
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(getString(R.string.last_used_download_type), selectedMediaType)
        }
    }

    private fun checkSelectedDownload(
        mainStorage: StoredDirectoryHelper?,
        targetFile: Uri?,
        filename: String?,
        mime: String?
    ) {
        val context = ctx ?: return
        val manager = downloadManager ?: return
        var storage: StoredFileHelper
        try {
            storage = when {
                // using SAF on older Android versions
                mainStorage == null -> StoredFileHelper(context, null, targetFile!!, "")

                // the file does not exist, but it is probably used in a pending download
                targetFile == null -> StoredFileHelper(mainStorage.uri, filename, mime, mainStorage.tag)

                // the file name is already used, try to use it
                else -> StoredFileHelper(context, mainStorage.uri, targetFile, mainStorage.tag)
            }
        } catch (e: Exception) {
            ErrorUtil.createNotification(requireContext(), ErrorInfo(e, UserAction.DOWNLOAD_FAILED, "Getting storage"))
            return
        }

        // is there already a download of the same file?
        val state = manager.checkForExistingMission(storage)
        val button: Int
        val message: Int
        when (state) {
            MissionState.Finished -> {
                button = R.string.overwrite
                message = R.string.overwrite_finished_warning
            }

            MissionState.Pending -> {
                button = R.string.overwrite
                message = R.string.download_already_pending
            }

            MissionState.PendingRunning -> {
                button = R.string.generate_unique_name
                message = R.string.download_already_running
            }

            MissionState.None -> {
                if (mainStorage == null) {
                    // SAF on older Android versions, or no save path: overwrite without asking
                    if (!storage.existsAsFile() && !storage.create()) {
                        showFailedDialog(R.string.error_file_creation)
                        return
                    }
                    continueSelectedDownload(storage)
                    return
                } else if (targetFile == null) {
                    // the name is not used by another download and the file does not exist
                    if (!mainStorage.mkdirs()) {
                        showFailedDialog(R.string.error_path_creation)
                        return
                    }
                    val created = mainStorage.createFile(filename, mime)
                    if (created == null || !created.canWrite()) {
                        showFailedDialog(R.string.error_file_creation)
                        return
                    }
                    continueSelectedDownload(created)
                    return
                }
                button = R.string.overwrite
                message = R.string.overwrite_unrelated_warning
            }

            else -> return
        }

        val finalStorage = storage
        if (mainStorage == null) {
            // SAF on older Android versions, or no save path
            val canOverwrite = state == MissionState.Pending || state == MissionState.Finished
            askToContinue(message, if (canOverwrite) button else null) {
                manager.forgetMission(finalStorage)
                continueSelectedDownload(finalStorage)
            }
            return
        }

        askToContinue(message, button) {
            when (state) {
                MissionState.PendingRunning -> {
                    val unique = mainStorage.createUniqueFile(filename, mime)
                    if (unique == null) {
                        showFailedDialog(R.string.error_file_creation)
                    } else {
                        continueSelectedDownload(unique)
                    }
                }

                else -> {
                    if (state == MissionState.Finished || state == MissionState.Pending) {
                        manager.forgetMission(finalStorage)
                    }
                    val taken: StoredFileHelper? = if (targetFile == null) {
                        mainStorage.createFile(filename, mime)
                    } else {
                        try {
                            // try to take (or steal) the file
                            StoredFileHelper(context, mainStorage.uri, targetFile, mainStorage.tag)
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed to take (or steal) the file in $targetFile")
                            null
                        }
                    }
                    if (taken != null && taken.canWrite()) {
                        continueSelectedDownload(taken)
                    } else {
                        showFailedDialog(R.string.error_file_creation)
                    }
                }
            }
        }
    }

    private fun continueSelectedDownload(storage: StoredFileHelper) {
        val context = ctx ?: return
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied)
            return
        }
        // the selected file has to be overwritten: check it simply by its length
        try {
            if (storage.length() > 0) {
                storage.truncate()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to truncate the file: " + storage.uri, e)
            showFailedDialog(R.string.overwrite_failed)
            return
        }

        val selectedStream: Stream
        var secondaryStream: Stream? = null
        val kind: Char
        var threadCount = threads
        var psName: String? = null
        var psArgs: Array<String>? = null
        var nearLength = 0L

        // select the muxer, the subtitle converter, ...
        when (selectedKind) {
            Kind.AUDIO -> {
                kind = 'a'
                selectedStream = wrappedAudioStreams().streamsList[selectedAudioIndex]
                if (selectedStream.format == MediaFormat.M4A) {
                    psName = Postprocessing.ALGORITHM_M4A_NO_DASH
                } else if (selectedStream.format == MediaFormat.WEBMA_OPUS) {
                    psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER
                }
            }

            Kind.VIDEO -> {
                kind = 'v'
                val video = wrappedVideoStreams.streamsList[selectedVideoIndex]
                selectedStream = video
                val secondary = secondaryStreams.get(selectedVideoIndex)
                if (secondary != null) {
                    secondaryStream = secondary.stream
                    psName = if (video.format == MediaFormat.MPEG_4) {
                        Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER
                    } else {
                        Postprocessing.ALGORITHM_WEBM_MUXER
                    }
                    val videoSize = wrappedVideoStreams.getSizeInBytes(video)
                    // only when both sizes are known; the downloader updates it later anyway
                    if (secondary.sizeInBytes > 0 && videoSize > 0) {
                        nearLength = secondary.sizeInBytes + videoSize
                    }
                }
            }

            Kind.SUBTITLE -> {
                threadCount = 1 // subtitles are small: a single thread
                kind = 's'
                selectedStream = wrappedSubtitleStreams.streamsList[selectedSubtitleIndex]
                if (selectedStream.format == MediaFormat.TTML) {
                    psName = Postprocessing.ALGORITHM_TTML_CONVERTER
                    psArgs = arrayOf(selectedStream.format!!.suffix, "false") // ignore empty frames
                }
            }
        }

        val urls: Array<String>
        val recoveryInfo: List<MissionRecoveryInfo>
        if (secondaryStream == null) {
            urls = arrayOf(selectedStream.content)
            recoveryInfo = listOf(MissionRecoveryInfo(selectedStream))
        } else {
            require(secondaryStream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) {
                "Unsupported stream delivery format" + secondaryStream.deliveryMethod
            }
            urls = arrayOf(selectedStream.content, secondaryStream.content)
            recoveryInfo = listOf(MissionRecoveryInfo(selectedStream), MissionRecoveryInfo(secondaryStream))
        }

        DownloadManagerService.startMission(
            context,
            urls,
            storage,
            kind,
            threadCount,
            currentInfo,
            psName,
            psArgs,
            nearLength,
            ArrayList(recoveryInfo)
        )
        Toast.makeText(context, getString(R.string.download_has_started), Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private companion object {
        const val TAG = "DialogFragment"
        val DEBUG = MainActivity.DEBUG
        const val DEFAULT_THREADS = 3
        const val MAX_THREADS = 32
        const val KEY_SELECTION = "selection"
    }
}
