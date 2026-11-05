/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import io.github.yewplay.core.designsystem.YpDialogCard
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.util.InfoCache

/** YewPlay: history and cache, in Compose: what to keep, and what to clear. */
class HistorySettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_history_title

    private lateinit var recordManager: HistoryRecordManager
    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordManager = HistoryRecordManager(requireContext())
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    override fun YpPrefBuilder.buildItems() {
        switch(
            key = R.string.enable_watch_history_key,
            title = R.string.enable_watch_history_title,
            summary = R.string.enable_watch_history_summary,
            default = true
        )
        switch(
            key = R.string.enable_playback_resume_key,
            title = R.string.enable_playback_resume_title,
            summary = R.string.enable_playback_resume_summary,
            default = true,
            dependency = R.string.enable_watch_history_key
        )
        switch(
            key = R.string.enable_playback_state_lists_key,
            title = R.string.enable_playback_state_lists_title,
            summary = R.string.enable_playback_state_lists_summary,
            default = true,
            dependency = R.string.enable_watch_history_key
        )
        switch(
            key = R.string.enable_search_history_key,
            title = R.string.enable_search_history_title,
            summary = R.string.enable_search_history_summary,
            default = true
        )
        category(R.string.settings_category_clear_data_title)
        action(
            title = R.string.metadata_cache_wipe_title,
            summary = R.string.metadata_cache_wipe_summary,
            key = R.string.metadata_cache_wipe_key
        ) {
            InfoCache.getInstance().clearCache()
            Toast.makeText(requireContext(), R.string.metadata_cache_wipe_complete_notice, Toast.LENGTH_SHORT).show()
        }
        action(
            title = R.string.clear_views_history_title,
            summary = R.string.clear_views_history_summary,
            key = R.string.clear_views_history_key
        ) { openDeleteWatchHistoryDialog(requireContext(), recordManager, disposables) }
        action(
            title = R.string.clear_playback_states_title,
            summary = R.string.clear_playback_states_summary,
            key = R.string.clear_playback_states_key
        ) { openDeletePlaybackStatesDialog(requireContext(), recordManager, disposables) }
        action(
            title = R.string.clear_search_history_title,
            summary = R.string.clear_search_history_summary,
            key = R.string.clear_search_history_key
        ) { openDeleteSearchHistoryDialog(requireContext(), recordManager, disposables) }
        val cookiesKey = s(R.string.recaptcha_cookies_key)
        items += YpPref.Action(
            key = s(R.string.clear_cookie_key),
            title = s(R.string.clear_cookie_title),
            summary = s(R.string.clear_cookie_summary),
            enabled = !prefs.getString(cookiesKey, "").isNullOrEmpty(),
            onClick = {
                prefs.edit { putString(cookiesKey, "") }
                DownloaderImpl.getInstance().setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "")
                Toast.makeText(requireContext(), R.string.recaptcha_cookies_cleared, Toast.LENGTH_SHORT).show()
            }
        )
    }

    companion object {
        @JvmStatic
        fun openDeleteWatchHistoryDialog(
            context: Context,
            recordManager: HistoryRecordManager,
            disposables: CompositeDisposable
        ) = confirmDelete(context, R.string.delete_view_history_alert) {
            disposables.add(deletePlaybackStates(context, recordManager))
            disposables.add(
                runDelete(context, recordManager.deleteWholeStreamHistory(), R.string.watch_history_deleted, "Delete from history")
            )
            disposables.add(runDelete(context, recordManager.removeOrphanedRecords(), 0, "Clear orphaned records"))
        }

        @JvmStatic
        fun openDeletePlaybackStatesDialog(
            context: Context,
            recordManager: HistoryRecordManager,
            disposables: CompositeDisposable
        ) = confirmDelete(context, R.string.delete_playback_states_alert) {
            disposables.add(deletePlaybackStates(context, recordManager))
        }

        @JvmStatic
        fun openDeleteSearchHistoryDialog(
            context: Context,
            recordManager: HistoryRecordManager,
            disposables: CompositeDisposable
        ) = confirmDelete(context, R.string.delete_search_history_alert) {
            disposables.add(
                runDelete(context, recordManager.deleteCompleteSearchHistory(), R.string.search_history_deleted, "Delete search history")
            )
        }

        private fun deletePlaybackStates(context: Context, recordManager: HistoryRecordManager): Disposable = runDelete(
            context,
            recordManager.deleteCompleteStreamStateHistory(),
            R.string.watch_history_states_deleted,
            "Delete playback states"
        )

        private fun runDelete(context: Context, action: Single<Int>, @StringRes done: Int, request: String): Disposable = action
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    if (done != 0) {
                        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                    }
                },
                { ErrorUtil.openActivity(context, ErrorInfo(it, UserAction.DELETE_FROM_HISTORY, request)) }
            )

        private fun confirmDelete(context: Context, @StringRes title: Int, onDelete: () -> Unit) {
            YpDialogs.card(context) { dismiss ->
                YpDialogCard(
                    title = stringResource(title),
                    confirmLabel = stringResource(R.string.delete),
                    onConfirm = {
                        dismiss()
                        onDelete()
                    },
                    dismissLabel = stringResource(R.string.cancel),
                    onDismiss = dismiss
                )
            }.show()
        }
    }
}
