/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.feature.home.HomeHeader
import io.github.yewplay.feature.home.HomeHeaderState
import java.util.Locale
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentMainBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.playlist.YpLocalPlaylistFragment
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper

/**
 * YewPlay: the home (proposal, p. 13). The header (service, region, big title, bell and the
 * tabs with the ruby dot) is drawn in Compose; the tabs themselves are the same fragments as
 * before, so the customizable tabs of NewPipe keep working.
 */
class MainFragment : BaseFragment() {
    private var binding: FragmentMainBinding? = null
    private var pagerAdapter: SelectedTabsPagerAdapter? = null
    private val tabsList = mutableListOf<Tab>()
    private lateinit var tabsManager: TabsManager
    private lateinit var prefs: SharedPreferences
    private var hasTabsChanged = false
    private var youtubeRestrictedModeEnabled = false
    private lateinit var youtubeRestrictedModeEnabledKey: String

    /** True when the home shows the kiosks of the current service as text tabs. */
    private var usingServiceKioskTabs = false

    private val header = mutableStateOf(HomeHeaderState("", null, "", emptyList(), 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabsManager = TabsManager.getManager(requireContext())
        tabsManager.setSavedTabsListener {
            if (isResumed) {
                setupTabs()
            } else {
                hasTabsChanged = true
            }
        }
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled)
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main, container, false)

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        val binding = FragmentMainBinding.bind(rootView)
        this.binding = binding
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)

        binding.ypHomeHeader.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.ypHomeHeader.setContent {
            YpTheme(themeKey, nightThemeKey) {
                HomeHeader(
                    state = header.value,
                    chevron = painterResource(R.drawable.ic_expand_more),
                    bell = painterResource(R.drawable.ic_notifications),
                    bellDescription = stringResource(R.string.fragment_feed_title),
                    onServiceClick = ::showServiceChooser,
                    onBellClick = { NavigationHelper.openFeedFragment(fm) },
                    onTabSelect = { index -> this.binding?.pager?.currentItem = index }
                )
            }
        }
        binding.pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) = updateHeader(position)
        })
        setupTabs()
    }

    override fun onResume() {
        super.onResume()
        val restricted = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false)
        if (youtubeRestrictedModeEnabled != restricted || hasTabsChanged) {
            youtubeRestrictedModeEnabled = restricted
            setupTabs()
        } else {
            updateHeader(binding?.pager?.currentItem ?: 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // keep the adapter: the pager restores its tabs from it when the home comes back
        binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        tabsManager.unsetSavedTabsListener()
        pagerAdapter = null
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Tabs                                                                                      */
    /* ---------------------------------------------------------------------------------------- */

    /**
     * Unless the user customized the tabs (advanced option kept from NewPipe), the home shows
     * the kiosks of the current service ("Live", "Music", "Gaming", ...) as text tabs.
     */
    private fun homeTabs(): List<Tab> {
        usingServiceKioskTabs = false
        if (prefs.contains(getString(R.string.saved_tabs_key))) {
            return tabsManager.tabs
        }
        val kioskTabs = try {
            val serviceId = ServiceHelper.getSelectedServiceId(requireContext())
            val kioskList = NewPipe.getService(serviceId).kioskList
            val available = kioskList.availableKiosks
            // the default kiosk, the known YouTube kiosks in the order of the proposal, the rest
            val ids = LinkedHashSet<String>()
            ids.add(kioskList.defaultKioskId)
            HOME_KIOSK_ORDER.filter { it in available }.forEach { ids.add(it) }
            ids.addAll(available)
            ids.filterNot { serviceId == YOUTUBE_SERVICE_ID && it == YOUTUBE_TRENDING_KIOSK }
                .map { Tab.KioskTab(serviceId, it) }
        } catch (e: Exception) {
            emptyList()
        }
        if (kioskTabs.isEmpty()) {
            return tabsManager.tabs
        }
        usingServiceKioskTabs = true
        return kioskTabs
    }

    private fun tabName(tab: Tab): String {
        if (usingServiceKioskTabs && tab is Tab.KioskTab) {
            when (tab.kioskId) {
                "trending_music" -> return getString(R.string.yp_kiosk_music)
                "trending_gaming" -> return getString(R.string.yp_kiosk_gaming)
                "trending_movies_and_shows" -> return getString(R.string.yp_kiosk_movies)
                "trending_podcasts_episodes" -> return getString(R.string.yp_kiosk_podcasts)
            }
        }
        return tab.getTabName(requireContext())
    }

    private fun setupTabs() {
        val binding = binding ?: return
        tabsList.clear()
        tabsList.addAll(homeTabs())
        val adapter = pagerAdapter?.takeIf { it.sameTabs(tabsList) }
            ?: SelectedTabsPagerAdapter(requireContext(), childFragmentManager, tabsList)
        pagerAdapter = adapter
        binding.pager.adapter = null
        binding.pager.adapter = adapter
        hasTabsChanged = false
        updateHeader(binding.pager.currentItem)
    }

    private fun updateHeader(position: Int) {
        if (!isAdded || tabsList.isEmpty()) {
            return
        }
        val context = requireContext()
        val serviceId = ServiceHelper.getSelectedServiceId(context)
        val serviceName = runCatching { NewPipe.getService(serviceId).serviceInfo.name }
            .getOrDefault("")
        val country = runCatching {
            val code = Localization.getPreferredContentCountry(context).countryCode
            Locale("", code).displayCountry
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        val selected = position.coerceIn(0, tabsList.size - 1)
        header.value = HomeHeaderState(
            serviceName = serviceName,
            region = country,
            title = if (usingServiceKioskTabs) {
                getString(R.string.yp_home_title)
            } else {
                tabsList[selected].getTabName(context)
            },
            tabs = tabsList.map(::tabName),
            selectedTab = selected
        )
    }

    private fun showServiceChooser() {
        val services = NewPipe.getServices()
        val selectedId = ServiceHelper.getSelectedServiceId(requireContext())
        val names = services.map { it.serviceInfo.name }.toTypedArray()
        val checked = services.indexOfFirst { it.serviceId == selectedId }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.yp_choose_service)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                val newId = services[which].serviceId
                if (newId != selectedId) {
                    ServiceHelper.setSelectedServiceId(requireContext(), newId)
                    requireActivity().recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Saves the edits of local playlists shown as tabs (e.g. before the player opens). */
    fun commitPlaylistTabs() {
        pagerAdapter?.localPlaylistFragments?.forEach { it.saveImmediate() }
    }

    private class SelectedTabsPagerAdapter(
        private val context: Context,
        fragmentManager: FragmentManager,
        tabs: List<Tab>
    ) : FragmentStatePagerAdapterMenuWorkaround(
        fragmentManager,
        FragmentStatePagerAdapterMenuWorkaround.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
    ) {
        private val tabs = tabs.toList()

        /**
         * Local playlists shown as tabs: their edits are saved right away when needed, see
         * [YpLocalPlaylistFragment.saveImmediate].
         */
        val localPlaylistFragments = mutableListOf<YpLocalPlaylistFragment>()

        override fun getItem(position: Int): Fragment {
            val tab = tabs[position]
            val fragment = try {
                tab.getFragment(context)
            } catch (t: Throwable) {
                return BlankFragment(
                    ErrorInfo(
                        t,
                        UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab ${tab.javaClass.simpleName}:${tab.getTabName(context)}"
                    )
                )
            }
            if (fragment is BaseFragment) {
                fragment.useAsFrontPage(true)
            }
            if (fragment is YpLocalPlaylistFragment) {
                localPlaylistFragments.add(fragment)
            }
            return fragment
        }

        // reloads all the fragments when notifyDataSetChanged is called
        override fun getItemPosition(item: Any): Int = PagerAdapter.POSITION_NONE

        override fun getCount(): Int = tabs.size

        fun sameTabs(other: List<Tab>): Boolean = tabs == other
    }

    private companion object {
        const val YOUTUBE_SERVICE_ID = 0
        const val YOUTUBE_TRENDING_KIOSK = "Trending"
        val HOME_KIOSK_ORDER = listOf(
            "trending_music",
            "trending_gaming",
            "trending_movies_and_shows",
            "trending_podcasts_episodes",
            "live"
        )
    }
}
