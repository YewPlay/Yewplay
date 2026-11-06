/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Intent
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.settings.compose.YpPref
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment

/** YewPlay: the debug options (debug builds only), in Compose. */
class DebugSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_debug_title

    private val leakCanary: DebugSettingsBVDLeakCanaryAPI? by lazy {
        runCatching {
            Class.forName(DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS)
                .getDeclaredConstructor()
                .newInstance() as DebugSettingsBVDLeakCanaryAPI
        }.getOrNull()
    }

    override fun YpPrefBuilder.buildItems() {
        val canary = leakCanary
        items += YpPref.Switch(
            key = s(R.string.allow_heap_dumping_key),
            title = s(R.string.leakcanary),
            summary = s(if (canary != null) R.string.enable_leak_canary_summary else R.string.leak_canary_not_available),
            default = false,
            enabled = canary != null
        )
        action(
            title = R.string.show_memory_leaks,
            key = R.string.show_memory_leaks_key,
            summary = if (canary != null) 0 else R.string.leak_canary_not_available,
            enabled = canary != null
        ) { canary?.let { startActivity(it.newLeakDisplayActivityIntent) } }
        switch(
            key = R.string.allow_disposed_exceptions_key,
            title = R.string.enable_disposed_exceptions_title,
            summary = R.string.enable_disposed_exceptions_summary,
            default = false
        )
        switch(
            key = R.string.show_original_time_ago_key,
            title = R.string.show_original_time_ago_title,
            summary = R.string.show_original_time_ago_summary,
            default = false
        )
        switch(
            key = R.string.show_crash_the_player_key,
            title = R.string.show_crash_the_player_title,
            summary = R.string.show_crash_the_player_summary,
            default = false
        )
        action(title = R.string.check_new_streams, key = R.string.check_new_streams_key) {
            NotificationWorker.runNow(requireContext())
        }
        action(title = R.string.crash_the_app, key = R.string.crash_the_app_key) {
            throw RuntimeException(DUMMY)
        }
        action(title = R.string.show_error_snackbar, key = R.string.show_error_snackbar_key) {
            ErrorUtil.showUiErrorSnackbar(this@DebugSettingsFragment, DUMMY, RuntimeException(DUMMY))
        }
        action(title = R.string.create_error_notification, key = R.string.create_error_notification_key) {
            ErrorUtil.createNotification(
                requireContext(),
                ErrorInfo(RuntimeException(DUMMY), UserAction.UI_ERROR, DUMMY)
            )
        }
    }

    /** Implemented in the debug builds only, where LeakCanary is available. */
    interface DebugSettingsBVDLeakCanaryAPI {
        val newLeakDisplayActivityIntent: Intent

        companion object {
            const val IMPL_CLASS = "org.schabi.newpipe.settings.DebugSettingsBVDLeakCanary"
        }
    }

    private companion object {
        const val DUMMY = "Dummy"
    }
}
