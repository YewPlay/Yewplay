/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpDialogCard
import org.schabi.newpipe.NewVersionWorker
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment

/** YewPlay: updates, in Compose: check automatically, or check now. */
class UpdateSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_updates_title

    override fun YpPrefBuilder.buildItems() {
        switch(
            key = R.string.update_app_key,
            title = R.string.updates_setting_title,
            summary = R.string.updates_setting_description,
            default = false,
            onChange = { check ->
                if (check) {
                    NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true)
                }
                true
            }
        )
        action(title = R.string.manual_update_title, summary = R.string.manual_update_description, key = R.string.manual_update_key) {
            Toast.makeText(context, R.string.checking_updates_toast, Toast.LENGTH_SHORT).show()
            NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true)
        }
    }

    companion object {
        /** Asks once whether YewPlay may check for updates by itself. */
        @JvmStatic
        fun askForConsentToUpdateChecks(context: Context) {
            YpDialogs.card(context) { dismiss ->
                YpDialogCard(
                    title = stringResource(R.string.check_for_updates),
                    message = stringResource(R.string.auto_update_check_description),
                    confirmLabel = stringResource(R.string.yes),
                    onConfirm = {
                        dismiss()
                        setAutoUpdateCheckEnabled(context, true)
                    },
                    dismissLabel = stringResource(R.string.no),
                    onDismiss = {
                        dismiss()
                        // explicitly false, since the default was true in previous versions
                        setAutoUpdateCheckEnabled(context, false)
                    }
                )
            }.show()
        }

        private fun setAutoUpdateCheckEnabled(context: Context, enabled: Boolean) {
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putBoolean(context.getString(R.string.update_app_key), enabled)
                putBoolean(context.getString(R.string.update_check_consent_key), true)
            }
        }

        @JvmStatic
        fun wasUserAskedForConsent(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.update_check_consent_key), false)
    }
}
