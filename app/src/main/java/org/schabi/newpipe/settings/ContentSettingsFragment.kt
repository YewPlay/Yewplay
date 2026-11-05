/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil3.SingletonImageLoader
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.settings.tabs.ChooseTabsFragment
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

/**
 * YewPlay: content, in Compose: languages and country, the tabs of the home, PeerTube, what
 * is shown, thumbnails and the feed.
 */
class ContentSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.content

    override fun onDestroy() {
        super.onDestroy()
        // the language and the country of the content may have changed
        val context = requireContext()
        NewPipe.setupLocalization(
            Localization.getPreferredLocalization(context),
            Localization.getPreferredContentCountry(context)
        )
        PlayerHelper.resetFormat()
    }

    override fun YpPrefBuilder.buildItems() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ keeps the language of each app in the system settings
            val locale = AppCompatDelegate.getApplicationLocales()[0]
            action(
                title = R.string.app_language_title,
                key = R.string.app_language_android_13_and_up_key,
                summaryText = locale?.displayName ?: s(R.string.systems_language)
            ) {
                startActivity(
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        .setData(Uri.fromParts("package", requireContext().packageName, null))
                )
            }
        } else {
            choice(
                key = R.string.app_language_key,
                title = R.string.app_language_title,
                entries = R.array.app_language_name,
                values = R.array.app_language_code,
                default = R.string.default_localization_key,
                onChange = { language ->
                    val tag = if (language == s(R.string.default_localization_key)) null else language
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    true
                }
            )
        }
        choice(
            key = R.string.content_language_key,
            title = R.string.content_language_title,
            entries = R.array.language_names,
            values = R.array.language_codes,
            default = R.string.default_localization_key
        )
        choice(
            key = R.string.content_country_key,
            title = R.string.default_content_country_title,
            entries = R.array.country_names,
            values = R.array.country_codes,
            default = R.string.default_localization_key
        )
        action(
            title = R.string.main_page_content,
            summary = R.string.main_page_content_summary,
            key = R.string.main_page_content_key
        ) { openScreen(ChooseTabsFragment::class.java) }
        multiChoice(
            key = R.string.show_channel_tabs_key,
            title = R.string.show_channel_tabs,
            entries = R.array.show_channel_tabs_description_list,
            values = R.array.show_channel_tabs_value_list,
            summary = R.string.show_channel_tabs_summary
        )
        action(
            title = R.string.peertube_instance_url_title,
            summary = R.string.peertube_instance_url_summary,
            key = R.string.peertube_instance_setup_key
        ) { openScreen(PeertubeInstanceListFragment::class.java) }
        switch(
            key = R.string.show_age_restricted_content,
            title = R.string.show_age_restricted_content_title,
            summary = R.string.show_age_restricted_content_summary,
            default = false
        )
        switch(
            key = R.string.youtube_restricted_mode_enabled,
            title = R.string.youtube_restricted_mode_enabled_title,
            summary = R.string.youtube_restricted_mode_enabled_summary,
            default = false,
            onChange = { enabled ->
                DownloaderImpl.getInstance().updateYoutubeRestrictedModeCookies(enabled)
                true
            }
        )
        multiChoice(
            key = R.string.show_search_suggestions_key,
            title = R.string.show_search_suggestions_title,
            entries = R.array.show_search_suggestions_description_list,
            values = R.array.show_search_suggestions_value_list,
            summary = R.string.show_search_suggestions_summary
        )
        choice(
            key = R.string.image_quality_key,
            title = R.string.image_quality_title,
            entries = R.array.image_quality_description,
            values = R.array.image_quality_values,
            default = R.string.image_quality_default,
            summary = R.string.image_quality_summary,
            onChange = { value ->
                val context = requireContext()
                ImageStrategy.setPreferredImageQuality(PreferredImageQuality.fromPreferenceKey(context, value))
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                Toast.makeText(context, R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT).show()
                true
            }
        )
        switch(key = R.string.show_comments_key, title = R.string.show_comments_title, summary = R.string.show_comments_summary, default = true)
        switch(key = R.string.show_next_video_key, title = R.string.show_next_and_similar_title, default = true)
        switch(
            key = R.string.show_description_key,
            title = R.string.show_description_title,
            summary = R.string.show_description_summary,
            default = true
        )
        switch(key = R.string.show_meta_info_key, title = R.string.show_meta_info_title, summary = R.string.show_meta_info_summary, default = true)
        category(R.string.settings_category_feed_title)
        durationChoice(
            key = R.string.feed_update_threshold_key,
            title = R.string.feed_update_threshold_title,
            entries = R.array.feed_update_threshold_options,
            values = R.array.feed_update_threshold_values,
            default = R.string.feed_update_threshold_default_value,
            summary = R.string.feed_update_threshold_summary
        )
        switch(
            key = R.string.feed_use_dedicated_fetch_method_key,
            title = R.string.feed_use_dedicated_fetch_method_title,
            summary = R.string.feed_use_dedicated_fetch_method_summary,
            default = false
        )
        multiChoice(
            key = R.string.feed_fetch_channel_tabs_key,
            title = R.string.feed_fetch_channel_tabs,
            entries = R.array.feed_fetch_channel_tabs_description_list,
            values = R.array.feed_fetch_channel_tabs_value_list,
            summary = R.string.feed_fetch_channel_tabs_summary
        )
    }
}
