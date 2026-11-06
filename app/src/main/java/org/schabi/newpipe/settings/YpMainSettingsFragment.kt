/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.settings.SettingsBar
import io.github.yewplay.feature.settings.SettingsEntry
import io.github.yewplay.feature.settings.SettingsGroup
import io.github.yewplay.feature.settings.SettingsScreen
import kotlin.reflect.KClass
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.ReleaseVersionUtil

/**
 * YewPlay: the settings home in Compose (proposal, page 17). Each entry opens the existing
 * preference screen; "Updates" only shows in release builds and "Debug" only in debug builds.
 */
class YpMainSettingsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ReleaseVersionUtil.isReleaseApk) {
            // nothing to update from: make sure the update check stays off
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putBoolean(getString(R.string.update_app_key), false)
            }
        }
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
                    SettingsScreen(
                        bar = SettingsBar(
                            title = stringResource(R.string.settings),
                            back = painterResource(R.drawable.yp_ic_arrow_left),
                            backDescription = stringResource(
                                androidx.appcompat.R.string.abc_action_bar_up_description
                            ),
                            search = painterResource(R.drawable.yp_ic_magnifying_glass),
                            searchDescription = stringResource(R.string.yp_settings_search),
                            chevron = painterResource(R.drawable.yp_ic_caret_right)
                        ),
                        groups = groups(),
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onSearch = { (activity as? SettingsActivity)?.setSearchActive(true) }
                    )
                }
            }
        }
    }

    @Composable
    private fun groups(): List<SettingsGroup> {
        val others = buildList {
            if (ReleaseVersionUtil.isReleaseApk) {
                add(
                    entry(
                        R.drawable.yp_ic_cloud_arrow_down,
                        R.string.settings_category_updates_title,
                        null,
                        UpdateSettingsFragment::class
                    )
                )
            }
            if (MainActivity.DEBUG) {
                add(
                    entry(
                        R.drawable.yp_ic_bug,
                        R.string.settings_category_debug_title,
                        null,
                        DebugSettingsFragment::class
                    )
                )
            }
        }
        return listOfNotNull(
            SettingsGroup(
                stringResource(R.string.yp_settings_group_playback),
                listOf(
                    entry(
                        R.drawable.yp_ic_play_circle,
                        R.string.settings_category_video_audio_title,
                        R.string.yp_settings_video_audio_summary,
                        VideoAudioSettingsFragment::class
                    ),
                    entry(
                        R.drawable.yp_ic_download_simple,
                        R.string.settings_category_downloads_title,
                        R.string.yp_settings_downloads_summary,
                        DownloadSettingsFragment::class
                    )
                )
            ),
            SettingsGroup(
                stringResource(R.string.yp_settings_group_personalization),
                listOf(
                    entry(
                        R.drawable.yp_ic_palette,
                        R.string.settings_category_appearance_title,
                        R.string.yp_settings_appearance_summary,
                        AppearanceSettingsFragment::class
                    ),
                    entry(
                        R.drawable.yp_ic_globe_set,
                        R.string.content,
                        R.string.yp_settings_content_summary,
                        ContentSettingsFragment::class
                    ),
                    entry(
                        R.drawable.yp_ic_bell,
                        R.string.notifications,
                        R.string.yp_settings_notifications_summary,
                        NotificationsSettingsFragment::class
                    )
                )
            ),
            SettingsGroup(
                stringResource(R.string.yp_settings_group_your_data),
                listOf(
                    entry(
                        R.drawable.yp_ic_clock_ccw,
                        R.string.settings_category_history_title,
                        R.string.yp_settings_history_summary,
                        HistorySettingsFragment::class
                    ),
                    entry(
                        R.drawable.yp_ic_database,
                        R.string.settings_category_backup_restore_title,
                        R.string.yp_settings_backup_summary,
                        BackupRestoreSettingsFragment::class
                    )
                )
            ),
            if (others.isEmpty()) null else SettingsGroup(null, others)
        )
    }

    @Composable
    private fun entry(
        icon: Int,
        title: Int,
        description: Int?,
        fragment: KClass<out Fragment>
    ) = SettingsEntry(
        icon = painterResource(icon),
        title = stringResource(title),
        description = description?.let { stringResource(it) },
        onClick = { (activity as? SettingsActivity)?.openSettingsFragment(fragment.java.name) }
    )
}
