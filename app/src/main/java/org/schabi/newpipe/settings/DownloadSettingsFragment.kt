/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.nononsenseapps.filepicker.Utils
import io.github.yewplay.core.designsystem.YpDialogCard
import java.io.IOException
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.util.FilePickerActivityHelper

/**
 * YewPlay: downloads, in Compose: ask where to save or use the folders for video and audio,
 * file names, retries and network.
 */
class DownloadSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_downloads_title

    private val videoPathLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { onPathPicked(it, getString(R.string.download_path_video_key)) }
    private val audioPathLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { onPathPicked(it, getString(R.string.download_path_audio_key)) }

    override fun YpPrefBuilder.buildItems() {
        val safForced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        switch(
            key = R.string.downloads_storage_ask,
            title = R.string.downloads_storage_ask_title,
            summary = if (safForced) R.string.downloads_storage_ask_summary_no_saf_notice else R.string.downloads_storage_ask_summary,
            default = false
        )
        items += YpPref.Switch(
            key = s(R.string.storage_use_saf),
            title = s(R.string.downloads_storage_use_saf_title),
            summary = s(if (safForced) R.string.downloads_storage_use_saf_summary_api_29 else R.string.downloads_storage_use_saf_summary),
            default = true,
            enabled = !safForced,
            onChange = { useSaf ->
                if (!useSaf) {
                    NewPipeSettings.saveDefaultVideoDownloadDirectory(requireContext())
                    NewPipeSettings.saveDefaultAudioDownloadDirectory(requireContext())
                } else {
                    prefs.edit {
                        putString(s(R.string.download_path_video_key), null)
                        putString(s(R.string.download_path_audio_key), null)
                    }
                }
                true
            }
        )
        val pickPaths = !prefs.getBoolean(s(R.string.downloads_storage_ask), false)
        items += YpPref.Action(
            key = s(R.string.download_path_video_key),
            title = s(R.string.download_path_title),
            summary = pathSummary(s(R.string.download_path_video_key), R.string.download_path_summary),
            enabled = pickPaths,
            onClick = { pickFolder(videoPathLauncher) }
        )
        items += YpPref.Action(
            key = s(R.string.download_path_audio_key),
            title = s(R.string.download_path_audio_title),
            summary = pathSummary(s(R.string.download_path_audio_key), R.string.download_path_audio_summary),
            enabled = pickPaths,
            onClick = { pickFolder(audioPathLauncher) }
        )
        choice(
            key = R.string.settings_file_charset_key,
            title = R.string.settings_file_charset_title,
            entries = R.array.settings_filename_charset_name,
            values = R.array.settings_filename_charset,
            default = R.string.default_file_charset_value
        )
        items += YpPref.Text(
            key = s(R.string.settings_file_replacement_character_key),
            title = s(R.string.settings_file_replacement_character_title),
            default = s(R.string.settings_file_replacement_character_default_value),
            summary = s(R.string.settings_file_replacement_character_summary)
        )
        choice(
            key = R.string.downloads_maximum_retry,
            title = R.string.max_retry_msg,
            entries = R.array.downloads_maximum_retry_list,
            values = R.array.downloads_maximum_retry_list,
            default = R.string.downloads_maximum_retry_default,
            summary = R.string.max_retry_desc
        )
        switch(
            key = R.string.downloads_cross_network,
            title = R.string.pause_downloads_on_mobile,
            summary = R.string.pause_downloads_on_mobile_desc,
            default = false
        )
        switch(
            key = R.string.downloads_queue_limit,
            title = R.string.enable_queue_limit,
            summary = R.string.enable_queue_limit_desc,
            default = true
        )
    }

    /** The chosen folder, or what the setting does when none is chosen yet. */
    private fun pathSummary(key: String, @StringRes fallback: Int): String {
        val uri = Uri.parse(prefs.getString(key, "").orEmpty())
        if (uri == Uri.EMPTY) {
            return getString(fallback)
        }
        return if (ContentResolver.SCHEME_FILE == uri.scheme) uri.path.orEmpty() else uri.toString()
    }

    private fun pickFolder(launcher: ActivityResultLauncher<Intent>) {
        NoFileManagerSafeGuard.launchSafe(launcher, StoredDirectoryHelper.getPicker(requireContext()), TAG, requireContext())
    }

    private fun onPathPicked(result: ActivityResult, key: String) {
        if (result.resultCode != Activity.RESULT_OK) {
            return
        }
        var uri = result.data?.data
        if (uri == null) {
            showMessage(R.string.general_error, R.string.invalid_directory)
            return
        }
        val context = requireContext()
        // the permissions on the old folder are kept: releasing them would make the downloads
        // saved there inaccessible
        if (!FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            // take the permissions on the new folder, and save it only when that worked
            try {
                context.grantUriPermission(context.packageName, uri, StoredDirectoryHelper.PERMISSION_FLAGS)
                val storage = StoredDirectoryHelper(context, uri, null)
                Log.i(TAG, "Acquiring tree success from $uri")
                if (!storage.canWrite()) {
                    throw IOException("No write permissions on $uri")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error acquiring tree from $uri", e)
                showMessage(R.string.general_error, R.string.no_available_dir)
                return
            }
        } else {
            val target = Utils.getFileForUri(uri)
            if (!target.canWrite()) {
                showMessage(R.string.download_to_sdcard_error_title, R.string.download_to_sdcard_error_message)
                return
            }
            uri = Uri.fromFile(target)
        }
        prefs.edit { putString(key, uri.toString()) }
    }

    private fun showMessage(@StringRes title: Int, @StringRes message: Int) {
        YpDialogs.card(requireContext()) { dismiss ->
            YpDialogCard(
                title = stringResource(title),
                message = stringResource(message),
                confirmLabel = stringResource(R.string.ok),
                onConfirm = dismiss
            )
        }.show()
    }

    private companion object {
        const val TAG = "DownloadSettings"
    }
}
