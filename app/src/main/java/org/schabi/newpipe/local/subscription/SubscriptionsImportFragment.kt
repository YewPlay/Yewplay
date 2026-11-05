/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTextField
import io.github.yewplay.core.designsystem.YpTheme
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.ContentSource
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: import the subscriptions of a service (YouTube, SoundCloud...), in Compose: the
 * instructions of the service, a link to its page, and either the channel address to type or
 * the export file to choose.
 */
class SubscriptionsImportFragment : BaseFragment() {
    private var serviceId = NO_SERVICE_ID
    private var supportedSources: List<ContentSource> = emptyList()
    private var relatedUrl: String? = null
    private var instructions = 0

    private val requestImportFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::requestImportFileResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceId = savedInstanceState?.getInt(KEY_SERVICE_ID) ?: arguments?.getInt(KEY_SERVICE_ID, NO_SERVICE_ID) ?: NO_SERVICE_ID
        setupServiceVariables()
        if (supportedSources.isEmpty() && serviceId != NO_SERVICE_ID) {
            ErrorUtil.showSnackbar(
                requireActivity(),
                ErrorInfo(
                    arrayOf(),
                    UserAction.SUBSCRIPTION_IMPORT_EXPORT,
                    "Service does not support importing subscriptions",
                    serviceId,
                    R.string.general_error
                )
            )
            requireActivity().finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SERVICE_ID, serviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) { ImportScreen() }
            }
        }
    }

    @Composable
    private fun ImportScreen() {
        val byUrl = ContentSource.CHANNEL_URL in supportedSources
        var url by rememberSaveable { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description)
                    )
                }
            }
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin),
                text = stringResource(R.string.import_title) + " · " + serviceName(),
                style = MaterialTheme.typography.headlineLarge
            )
            if (instructions > 0) {
                Text(
                    modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 16.dp),
                    text = if (relatedUrl.isNullOrEmpty()) {
                        stringResource(instructions)
                    } else {
                        stringResource(instructions, relatedUrl!!)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            relatedUrl?.takeIf { it.isNotEmpty() }?.let { link ->
                YpPillButton(
                    modifier = Modifier.padding(horizontal = YpScreenMargin),
                    label = stringResource(R.string.yp_import_open_page),
                    primary = false,
                    onClick = { ShareUtils.openUrlInBrowser(requireContext(), link) }
                )
            }
            if (byUrl) {
                YpTextField(
                    modifier = Modifier.padding(top = 20.dp),
                    value = url,
                    onValueChange = { url = it },
                    placeholder = ServiceHelper.getImportInstructionsHint(serviceId)
                        .takeIf { it > 0 }?.let { stringResource(it) },
                    keyboardType = KeyboardType.Uri,
                    horizontalPadding = YpScreenMargin,
                    onDone = { onImportUrl(url) }
                )
            }
            YpPillButton(
                modifier = Modifier
                    .padding(horizontal = YpScreenMargin, vertical = 20.dp)
                    .fillMaxWidth(),
                label = stringResource(if (byUrl) R.string.import_title else R.string.import_file_title),
                primary = true,
                enabled = !byUrl || url.isNotBlank(),
                onClick = { if (byUrl) onImportUrl(url) else onImportFile() }
            )
        }
    }

    private fun serviceName(): String = try {
        NewPipe.getService(serviceId).serviceInfo.name
    } catch (e: ExtractionException) {
        ""
    }

    private fun onImportUrl(value: String) {
        if (value.isNotBlank()) {
            ImportConfirmationDialog.show(this, SubscriptionImportInput.ChannelUrlMode(serviceId, value.trim()))
        }
    }

    private fun onImportFile() {
        // */*: services export with different MIME types and file extensions
        NoFileManagerSafeGuard.launchSafe(
            requestImportFileLauncher,
            StoredFileHelper.getPicker(requireActivity(), "*/*"),
            TAG,
            context
        )
    }

    private fun requestImportFileResult(result: ActivityResult) {
        val data = result.data?.dataString
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ImportConfirmationDialog.show(this, SubscriptionImportInput.InputStreamMode(serviceId, data))
        }
    }

    private fun setupServiceVariables() {
        if (serviceId != NO_SERVICE_ID) {
            try {
                val extractor = NewPipe.getService(serviceId).subscriptionExtractor
                if (extractor != null) {
                    supportedSources = extractor.supportedSources
                    relatedUrl = extractor.relatedUrl
                    instructions = ServiceHelper.getImportInstructions(serviceId)
                    return
                }
            } catch (ignored: ExtractionException) {
                // unknown service: nothing to import from
            }
        }
        supportedSources = emptyList()
        relatedUrl = null
        instructions = 0
    }

    companion object {
        private const val KEY_SERVICE_ID = "service_id"

        @JvmStatic
        fun getInstance(serviceId: Int): SubscriptionsImportFragment = SubscriptionsImportFragment().apply {
            arguments = Bundle().apply { putInt(KEY_SERVICE_ID, serviceId) }
        }
    }
}
