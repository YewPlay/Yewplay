/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.kiosk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.home.KioskActions
import io.github.yewplay.feature.home.KioskScreen
import io.github.yewplay.feature.home.KioskStrings
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: a kiosk of a service (live, music, gaming...) in Compose, as a tab of the home or
 * on its own page.
 */
class YpKioskFragment : BaseFragment() {
    private lateinit var viewModel: YpKioskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpKioskViewModel::class.java]
        val args = arguments ?: Bundle()
        viewModel.init(args.getInt(KEY_SERVICE_ID), args.getString(KEY_KIOSK_ID))
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
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.errors.collect { ErrorUtil.showSnackbar(this@YpKioskFragment, it) }
                }
                YpTheme(themeKey, nightThemeKey) {
                    KioskScreen(
                        state = state,
                        strings = KioskStrings(
                            empty = stringResource(R.string.no_videos),
                            emptyDetail = stringResource(R.string.empty_list_subtitle),
                            error = YpErrorLabels(
                                retry = stringResource(R.string.retry),
                                openInBrowser = stringResource(R.string.open_in_browser)
                            )
                        ),
                        moreIcon = painterResource(R.drawable.ic_more_vert),
                        actions = KioskActions(
                            onItemClick = ::openVideo,
                            onItemMenu = ::showItemMenu,
                            onLoadMore = viewModel::loadMore,
                            onRefresh = viewModel::refresh,
                            onRetry = viewModel::retry,
                            onErrorAction = ::onErrorAction,
                            onErrorOpenInBrowser = {
                                viewModel.lastError?.openInBrowserUrl?.let {
                                    ShareUtils.openUrlInBrowser(requireContext(), it)
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        if (!useAsFrontPage) {
            // on its own page the kiosk keeps the app bar with its name, as before
            setTitle(viewModel.name)
        }
    }

    private fun openVideo(index: Int) {
        val item = viewModel.streamItemAt(index) ?: return
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            fm,
            item.serviceId,
            item.url,
            item.name,
            null,
            false
        )
    }

    private fun showItemMenu(index: Int) {
        val item = viewModel.streamItemAt(index) ?: return
        try {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, item).create().show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item)
        }
    }

    private fun onErrorAction() {
        val error = viewModel.lastError ?: return
        val recaptchaUrl = error.recaptchaUrl
        if (recaptchaUrl != null) {
            val intent = Intent(requireContext(), ReCaptchaActivity::class.java)
            intent.putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, recaptchaUrl)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, ReCaptchaActivity.RECAPTCHA_REQUEST)
        } else {
            ErrorUtil.openActivity(requireContext(), error)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST && resultCode == Activity.RESULT_OK) {
            viewModel.retry()
        }
    }

    companion object {
        private const val KEY_KIOSK_ID = "yp_kiosk_id"

        /** A kiosk of a service. */
        @JvmStatic
        fun getInstance(serviceId: Int, kioskId: String): YpKioskFragment = YpKioskFragment().apply {
            arguments = Bundle().apply {
                putInt(KEY_SERVICE_ID, serviceId)
                putString(KEY_KIOSK_ID, kioskId)
            }
        }

        /** The default kiosk of the selected service; follows it when the service changes. */
        @JvmStatic
        fun getDefaultInstance(): YpKioskFragment = YpKioskFragment()
    }
}
