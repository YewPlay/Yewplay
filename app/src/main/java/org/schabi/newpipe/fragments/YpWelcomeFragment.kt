/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.home.WelcomeImages
import io.github.yewplay.feature.home.WelcomeScreen
import io.github.yewplay.feature.home.WelcomeStrings
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.SubscriptionsImportExportHelper
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.NavigationHelper

/**
 * YewPlay: the welcome screen, shown on the first start only (proposal, p. 9). "Get started"
 * opens the home; "Import subscriptions from NewPipe" opens the NewPipe export file picker.
 */
class YpWelcomeFragment : BaseFragment() {
    private lateinit var importHelper: SubscriptionsImportExportHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // registers for the result of the file picker, so it must be created here
        importHelper = SubscriptionsImportExportHelper(this)
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
                YpTheme(themeKey, nightThemeKey) {
                    WelcomeScreen(
                        strings = WelcomeStrings(
                            appName = stringResource(R.string.app_name),
                            title = stringResource(R.string.yp_welcome_title),
                            text = stringResource(R.string.yp_welcome_text),
                            start = stringResource(R.string.yp_welcome_start),
                            import = stringResource(R.string.yp_welcome_import)
                        ),
                        images = WelcomeImages(
                            logo = painterResource(R.drawable.ic_yewplay_logo)
                        ),
                        onStart = ::start,
                        onImport = { importHelper.onImportPreviousSelected() }
                    )
                }
            }
        }
    }

    private fun start() {
        markShown(requireContext())
        NavigationHelper.openMainFragment(fm)
    }

    companion object {
        private const val KEY_SHOWN = "yp_welcome_shown"

        /** Whether the welcome screen still has to be shown (first start). */
        @JvmStatic
        fun shouldShow(context: Context): Boolean = !PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_SHOWN, false)

        private fun markShown(context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean(KEY_SHOWN, true) }
        }
    }
}
