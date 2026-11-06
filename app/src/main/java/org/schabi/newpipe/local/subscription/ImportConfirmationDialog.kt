/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Dialog
import android.os.Bundle
import androidx.compose.ui.res.stringResource
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import io.github.yewplay.core.designsystem.YpDialogCard
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportWorker
import org.schabi.newpipe.local.yewplay.YpDialogs

/**
 * YewPlay: confirms an import of subscriptions (it can use a lot of data) as a Compose dialog,
 * then starts the import in the background.
 */
class ImportConfirmationDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = YpDialogs.card(requireContext()) { dismiss ->
        YpDialogCard(
            title = stringResource(R.string.import_title),
            message = stringResource(R.string.import_network_expensive_warning),
            confirmLabel = stringResource(R.string.ok),
            onConfirm = {
                startImport()
                dismiss()
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = dismiss
        )
    }

    private fun startImport() {
        val input = BundleCompat.getParcelable(
            requireArguments(),
            INPUT,
            SubscriptionImportInput::class.java
        ) ?: return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(SubscriptionImportWorker::class.java)
            .setInputData(input.toData())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork(SubscriptionImportWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        private const val INPUT = "input"

        @JvmStatic
        fun show(fragment: Fragment, input: SubscriptionImportInput) {
            ImportConfirmationDialog()
                .apply { arguments = Bundle().apply { putParcelable(INPUT, input) } }
                .show(fragment.parentFragmentManager, null)
        }
    }
}
