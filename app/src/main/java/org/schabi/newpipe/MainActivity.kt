/*
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.github.yewplay.core.designsystem.YpTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Objects
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.LibraryFragment
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.YpWelcomeFragment
import org.schabi.newpipe.fragments.detail.YpVideoDetailFragment
import org.schabi.newpipe.fragments.list.channel.YpChannelFragment
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.playlist.YpPlaylistFragment
import org.schabi.newpipe.fragments.list.search.YpSearchFragment
import org.schabi.newpipe.local.bookmark.YpBookmarksFragment
import org.schabi.newpipe.local.feed.FeedFragment
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.history.YpHistoryFragment
import org.schabi.newpipe.local.playlist.YpLocalPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.local.subscription.SubscriptionsImportFragment
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.event.OnKeyDownListener
import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.settings.UpdateSettingsFragment
import org.schabi.newpipe.settings.migration.MigrationManager
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.KEY_LINK_TYPE
import org.schabi.newpipe.util.KEY_MAIN_PAGE_CHANGE
import org.schabi.newpipe.util.KEY_OPEN_SEARCH
import org.schabi.newpipe.util.KEY_SEARCH_STRING
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ReleaseVersionUtil
import org.schabi.newpipe.util.SerializedCache
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.views.FocusAwareCoordinator
import org.schabi.newpipe.views.FocusOverlayView

/**
 * YewPlay: the activity shell in Kotlin. The view tree of activity_main.xml is built in code:
 * a [FocusAwareCoordinator] with the fragment holder, the toolbar, the player bottom sheet and
 * a [ComposeView] hosting [YpBottomBar]. The side drawer was locked closed and unreachable, so
 * it was removed together with its menu-building code; the four destinations of the bottom bar
 * cover the navigation.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rootCoordinator: FocusAwareCoordinator
    private lateinit var fragmentHolder: FragmentContainerView
    private lateinit var toolbarContainer: LinearLayout
    private lateinit var toolbar: Toolbar
    private lateinit var playerHolder: FragmentContainerView
    private lateinit var bottomBarView: ComposeView

    /** YewPlay: state of the Compose bottom bar, synced from the back stack and the sheet. */
    private val bottomBarDestination = mutableStateOf<YpNavDestination?>(YpNavDestination.HOME)
    private val bottomBarAttached = mutableStateOf(false)
    private val bottomBarGone = mutableStateOf(false)

    /** YewPlay: top margin of the fragment holder below the toolbar, read once. */
    private var toolbarMarginTop = -1
    private var bottomBarHiddenForScreen = false

    private var broadcastReceiver: BroadcastReceiver? = null

    private lateinit var sharedPreferences: SharedPreferences

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    ////////////////////////////////////////////////////////////////////////// */

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        }

        Localization.migrateAppLanguageSettingIfNecessary(applicationContext)
        ThemeHelper.setDayNightMode(this)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))

        // Fixes text color turning black in dark/black mode:
        // https://github.com/TeamNewPipe/NewPipe/issues/12016
        // For further reference see: https://issuetracker.google.com/issues/37124582
        if (DeviceUtils.supportsWebView()) {
            try {
                WebView(this)
            } catch (e: Throwable) {
                if (DEBUG) {
                    Log.e(TAG, "Failed to create WebView", e)
                }
            }
        }

        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        buildContentView()
        setContentView(rootCoordinator)

        if (supportFragmentManager.backStackEntryCount == 0) {
            initFragments()
        }

        setSupportActionBar(toolbar)
        setupBottomNavigation()
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this)
        }
        openMiniPlayerUponPlayerStarted()

        if (PermissionHelper.checkPostNotificationsPermission(
                this,
                PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE
            )
        ) {
            // Schedule worker for checking for new streams and creating corresponding
            // notifications if this is enabled by the user.
            NotificationWorker.initialize(this)
        }
        if (!UpdateSettingsFragment.wasUserAskedForConsent(this) &&
            !App.instance.isFirstRun &&
            ReleaseVersionUtil.isReleaseApk
        ) {
            UpdateSettingsFragment.askForConsentToUpdateChecks(this)
        }

        // ReleaseVersionUtil.isReleaseApk will be true only for main official build
        // We want every release build (nightly, nightly-refactor) to show the popup
        if (!DEBUG) {
            showKeepAndroidDialog()
            showApi23RequirementDialog()
        }

        MigrationManager.showUserInfoIfPresent(this)
    }

    /**
     * Builds the view tree of the former activity_main.xml: fragment holder, toolbar, player
     * bottom sheet and the Compose bottom bar inside a [FocusAwareCoordinator].
     */
    private fun buildContentView() {
        val actionBarSize = resolveActionBarSize()

        rootCoordinator = FocusAwareCoordinator(this)

        fragmentHolder = FragmentContainerView(this).apply {
            id = R.id.fragment_holder
            clipToPadding = false
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.yp_bottom_bar_space))
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = actionBarSize
            }
        }

        toolbarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        toolbar = Toolbar(androidx.appcompat.view.ContextThemeWrapper(this, R.style.ToolbarTheme))
            .apply {
                id = R.id.toolbar
                // AppCompat centers the title vertically by default; the legacy XML gravity
                // (center_vertical) has no programmatic counterpart and is redundant
                minimumHeight = actionBarSize
                setBackgroundColor(resolveAttrColor(androidx.appcompat.R.attr.colorPrimary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    actionBarSize
                )
            }
        toolbarContainer.addView(toolbar)

        playerHolder = FragmentContainerView(this).apply {
            id = R.id.fragment_player_holder
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                behavior = CustomBottomSheetBehavior(this@MainActivity, null)
            }
        }

        bottomBarView = ComposeView(this).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setContent {
                YpTheme(
                    themeKey = YpThemePrefs.themeKey(this@MainActivity),
                    nightThemeKey = YpThemePrefs.nightThemeKey(this@MainActivity)
                ) {
                    YpBottomBar(
                        selected = bottomBarDestination.value,
                        attachedToMiniPlayer = bottomBarAttached.value,
                        hidden = bottomBarGone.value,
                        onDestination = ::navigateToBottomDestination
                    )
                }
            }
        }

        rootCoordinator.addView(fragmentHolder)
        rootCoordinator.addView(toolbarContainer)
        rootCoordinator.addView(playerHolder)
        rootCoordinator.addView(bottomBarView)

        BottomSheetBehavior.from(playerHolder).apply {
            isHideable = true
            peekHeight = 0
        }
    }

    private fun resolveActionBarSize(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)
        return TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
    }

    private fun resolveAttrColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) {
            ContextCompat.getColor(this, tv.resourceId)
        } else {
            tv.data
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val app = App.instance

        if (sharedPreferences.getBoolean(app.getString(R.string.update_app_key), false) &&
            sharedPreferences.getBoolean(app.getString(R.string.update_check_consent_key), false)
        ) {
            // Start the worker which is checking all conditions
            // and eventually searching for a new version.
            NewVersionWorker.enqueueNewVersionCheckingWork(app, false)
        }
    }

    override fun onStart() {
        super.onStart()
        sharedPreferences.edit().putBoolean(KEY_IS_IN_BACKGROUND, false).apply()
        Log.d(TAG, "App moved to foreground")
    }

    override fun onStop() {
        super.onStop()
        sharedPreferences.edit().putBoolean(KEY_IS_IN_BACKGROUND, true).apply()
        Log.d(TAG, "App moved to background")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            StateSaver.clearStateFiles()
        }
        broadcastReceiver?.let {
            unregisterReceiver(it)
            broadcastReceiver = null
        }
    }

    override fun onResume() {
        // Change the date format to match the selected language on resume
        Localization.initPrettyTime(Localization.resolvePrettyTime())
        super.onResume()

        if (sharedPreferences.getBoolean(KEY_THEME_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "Theme has changed, recreating activity...")
            }
            sharedPreferences.edit().putBoolean(KEY_THEME_CHANGE, false).apply()
            ActivityCompat.recreate(this)
        }

        if (sharedPreferences.getBoolean(KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "main page has changed, recreating main fragment...")
            }
            sharedPreferences.edit().putBoolean(KEY_MAIN_PAGE_CHANGE, false).apply()
            NavigationHelper.openMainActivity(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (DEBUG) {
            Log.d(TAG, "onNewIntent() called with: intent = [$intent]")
        }
        // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
        // to not destroy the already created backstack
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            return
        }

        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_player_holder)
        return if (fragment is OnKeyDownListener && !bottomSheetHiddenOrCollapsed()) {
            // Provide keyDown event to fragment which then sends this event
            // to the main player service
            fragment.onKeyDown(keyCode) || super.onKeyDown(keyCode, event)
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called")
        }

        // In case bottomSheet is not visible on the screen or collapsed we can assume that the
        // user interacts with a fragment inside fragment_holder so all back presses should be
        // handled by it
        if (bottomSheetHiddenOrCollapsed()) {
            val fm = supportFragmentManager
            val fragment = fm.findFragmentById(R.id.fragment_holder)
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragment is BackPressable) {
                if (fragment.onBackPressed()) {
                    return
                }
            } else if (fragment is CommentRepliesFragment) {
                // Expand DetailsFragment if CommentRepliesFragment was opened
                // and no other CommentRepliesFragments are on top of the back stack
                // to show the top level comments again.
                openDetailFragmentFromCommentReplies(fm, false)
            }
        } else {
            val fragmentPlayer = supportFragmentManager
                .findFragmentById(R.id.fragment_player_holder)
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragmentPlayer is BackPressable) {
                if (!fragmentPlayer.onBackPressed()) {
                    BottomSheetBehavior.from(playerHolder).state =
                        BottomSheetBehavior.STATE_COLLAPSED
                }
                return
            }
        }

        if (supportFragmentManager.backStackEntryCount == 1) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return
            }
        }
        when (requestCode) {
            PermissionHelper.DOWNLOADS_REQUEST_CODE ->
                NavigationHelper.openDownloads(this)

            PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE -> {
                val fragment = supportFragmentManager
                    .findFragmentById(R.id.fragment_player_holder)
                if (fragment is YpVideoDetailFragment) {
                    fragment.openDownloadDialog()
                }
            }

            PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE ->
                NotificationWorker.initialize(this)
        }
    }

    /**
     * Implement the following diagram behavior for the up button:
     * <pre>
     *              +---------------+
     *              |  Main Screen  +----+
     *              +-------+-------+    |
     *                      |            |
     *                      ▲ Up         | Search Button
     *                      |            |
     *                 +----+-----+      |
     *    +------------+  Search  |◄-----+
     *    |            +----+-----+
     *    |   Open          |
     *    |  something      ▲ Up
     *    |                 |
     *    |    +------------+-------------+
     *    |    |                          |
     *    |    |  Video    <->  Channel   |
     *    +---►|  Channel  <->  Playlist  |
     *         |  Video    <->  ....      |
     *         |                          |
     *         +--------------------------+
     * </pre>
     */
    private fun onHomeButtonPressed() {
        val fm = supportFragmentManager
        val fragment = fm.findFragmentById(R.id.fragment_holder)

        if (fragment is CommentRepliesFragment) {
            // Expand DetailsFragment if CommentRepliesFragment was opened
            // and no other CommentRepliesFragments are on top of the back stack
            // to show the top level comments again.
            openDetailFragmentFromCommentReplies(fm, true)
        } else if (!NavigationHelper.tryGotoSearchFragment(fm)) {
            // If search fragment wasn't found in the backstack go to the main fragment
            NavigationHelper.gotoMainFragment(fm)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [$menu]")
        }
        super.onCreateOptionsMenu(menu)

        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        updateDrawerNavigation()

        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onOptionsItemSelected() called with: item = [$item]")
        }

        if (item.itemId == android.R.id.home) {
            onHomeButtonPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */

    private fun initFragments() {
        if (DEBUG) {
            Log.d(TAG, "initFragments() called")
        }
        StateSaver.clearStateFiles()
        if (intent != null && intent.hasExtra(KEY_LINK_TYPE)) {
            // When user watch a video inside popup and then tries to open the video in main
            // player while the app is closed he will see a blank fragment on place of kiosk.
            // Let's open it first
            if (supportFragmentManager.backStackEntryCount == 0) {
                NavigationHelper.openMainFragment(supportFragmentManager)
            }

            handleIntent(intent)
        } else if (YpWelcomeFragment.shouldShow(this)) {
            // YewPlay: the welcome screen on the first start; "Get started" opens the home
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, YpWelcomeFragment())
                .commit()
        } else {
            NavigationHelper.gotoMainFragment(supportFragmentManager)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */

    private fun updateDrawerNavigation() {
        val actionBar = supportActionBar ?: return

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
        // the home sets its own subtitle (service and region); clear it everywhere else
        actionBar.subtitle = null
        if (isBottomNavigationRoot(fragment)) {
            // YewPlay: the four destinations of the bottom bar have no "up" arrow
            actionBar.setDisplayHomeAsUpEnabled(false)
            toolbar.setNavigationOnClickListener(null)
        } else {
            actionBar.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener { onHomeButtonPressed() }
        }
        syncBottomNavigation(fragment)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // YewPlay: floating bottom bar
    ////////////////////////////////////////////////////////////////////////// */

    /**
     * YewPlay: screens with their own Compose top bar (Home, Feed, Search, Channel, ...) hide
     * the activity's toolbar and the space kept for it while they are shown.
     *
     * @param fragment the fragment now shown
     */
    private fun updateToolbarForFragment(fragment: Fragment?) {
        val ownTopBar = fragment is MainFragment ||
            fragment is FeedFragment ||
            fragment is YpSearchFragment ||
            fragment is YpChannelFragment ||
            fragment is YpPlaylistFragment ||
            fragment is YpLocalPlaylistFragment ||
            fragment is YpHistoryFragment ||
            fragment is YpBookmarksFragment ||
            fragment is SubscriptionFragment ||
            fragment is SubscriptionsImportFragment ||
            fragment is CommentRepliesFragment ||
            fragment is YpWelcomeFragment
        // the welcome screen is shown full screen, without the bottom bar
        bottomBarHiddenForScreen = fragment is YpWelcomeFragment
        updateBottomBarVisibility()
        val paddingBottom = if (bottomBarHiddenForScreen) {
            0
        } else {
            resources.getDimensionPixelSize(R.dimen.yp_bottom_bar_space)
        }
        if (fragmentHolder.paddingBottom != paddingBottom) {
            fragmentHolder.setPadding(
                fragmentHolder.paddingLeft,
                fragmentHolder.paddingTop,
                fragmentHolder.paddingRight,
                paddingBottom
            )
        }
        val params = fragmentHolder.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (toolbarMarginTop < 0) {
            toolbarMarginTop = params.topMargin
        }
        val marginTop = if (ownTopBar) 0 else toolbarMarginTop
        toolbarContainer.visibility = if (ownTopBar) View.GONE else View.VISIBLE
        if (params.topMargin != marginTop) {
            params.topMargin = marginTop
            fragmentHolder.layoutParams = params
        }
    }

    private fun updateBottomBarVisibility() {
        bottomBarGone.value = keyboardOpen || bottomBarHiddenForScreen
    }

    /** YewPlay: updated by the global layout listener while the keyboard opens/closes. */
    private var keyboardOpen = false

    private fun setupBottomNavigation() {
        supportFragmentManager.addOnBackStackChangedListener {
            syncBottomNavigation(supportFragmentManager.findFragmentById(R.id.fragment_holder))
        }
        // YewPlay: also sync when a screen of the holder is actually resumed; back gestures with
        // preview (Android 14+) can finish a transition without the listener above
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f.id == R.id.fragment_holder) {
                        syncBottomNavigation(f)
                    }
                }
            },
            false
        )

        // hide the bar while the keyboard is open (e.g. while typing a search)
        rootCoordinator.viewTreeObserver.addOnGlobalLayoutListener {
            val insets = ViewCompat.getRootWindowInsets(rootCoordinator)
            val open = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())
            if (open != keyboardOpen) {
                keyboardOpen = open
                updateBottomBarVisibility()
            }
        }

        val behavior = BottomSheetBehavior.from(playerHolder)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING ||
                    newState == BottomSheetBehavior.STATE_SETTLING
                ) {
                    return // onSlide keeps the bar position in sync while moving
                }
                updateBottomNavigationForSheet(
                    newState,
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) 1f else 0f
                )
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updateBottomNavigationForSheet(behavior.state, slideOffset)
            }
        })
        rootCoordinator.post {
            updateBottomNavigationForSheet(
                behavior.state,
                if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) 1f else 0f
            )
        }
    }

    /**
     * Hides the bottom bar while the player is expanded and joins it with the mini player when
     * the player is collapsed, so that both look like a single floating capsule.
     *
     * @param state       the bottom sheet state
     * @param slideOffset the bottom sheet slide offset (0 = collapsed, 1 = expanded)
     */
    private fun updateBottomNavigationForSheet(state: Int, slideOffset: Float) {
        val space = resources.getDimension(R.dimen.yp_bottom_bar_space)
        bottomBarView.translationY = slideOffset.coerceIn(0f, 1f) * space
        bottomBarAttached.value = state != BottomSheetBehavior.STATE_HIDDEN &&
            BottomSheetBehavior.from(playerHolder).peekHeight > 0
    }

    private fun navigateToBottomDestination(destination: YpNavDestination) {
        val fm = supportFragmentManager
        val behavior = BottomSheetBehavior.from(playerHolder)
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        try {
            NavigationHelper.gotoMainFragment(fm)
            when (destination) {
                YpNavDestination.SEARCH -> NavigationHelper.openSearchFragment(
                    fm,
                    ServiceHelper.getSelectedServiceId(this),
                    ""
                )

                YpNavDestination.SUBSCRIPTIONS -> NavigationHelper.openFeedFragment(fm)

                YpNavDestination.LIBRARY -> NavigationHelper.openLibraryFragment(fm)

                YpNavDestination.HOME -> { // home is where gotoMainFragment already landed
                }
            }
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening bottom bar destination", e)
        }
    }

    private fun syncBottomNavigation(fragment: Fragment?) {
        updateToolbarForFragment(fragment)
        val destination = when (fragment) {
            is MainFragment -> YpNavDestination.HOME

            is YpSearchFragment -> YpNavDestination.SEARCH

            is FeedFragment, is SubscriptionFragment -> YpNavDestination.SUBSCRIPTIONS

            is LibraryFragment, is YpHistoryFragment, is YpBookmarksFragment,
            is YpLocalPlaylistFragment -> YpNavDestination.LIBRARY

            // channels, playlists, etc. keep the destination they were opened from
            else -> return
        }
        bottomBarDestination.value = destination
    }

    private fun handleIntent(intent: Intent) {
        try {
            if (DEBUG) {
                Log.d(TAG, "handleIntent() called with: intent = [$intent]")
            }

            if (intent.hasExtra(KEY_LINK_TYPE)) {
                val url = intent.getStringExtra(KEY_URL)
                val serviceId = intent.getIntExtra(KEY_SERVICE_ID, 0)
                val title = intent.getStringExtra(KEY_TITLE) ?: ""

                val linkType = intent.getSerializableExtra(KEY_LINK_TYPE)
                    as StreamingService.LinkType
                when (linkType) {
                    StreamingService.LinkType.STREAM -> {
                        val intentCacheKey = intent.getStringExtra(Player.PLAY_QUEUE_KEY)
                        val playQueue = if (intentCacheKey != null) {
                            SerializedCache.getInstance().take(intentCacheKey, PlayQueue::class.java)
                        } else {
                            null
                        }

                        val switchingPlayers = intent.getBooleanExtra(
                            YpVideoDetailFragment.KEY_SWITCHING_PLAYERS,
                            false
                        )
                        NavigationHelper.openVideoDetailFragment(
                            applicationContext,
                            supportFragmentManager,
                            serviceId,
                            url,
                            title,
                            playQueue,
                            switchingPlayers
                        )
                    }

                    StreamingService.LinkType.CHANNEL ->
                        NavigationHelper.openChannelFragment(
                            supportFragmentManager,
                            serviceId,
                            url,
                            title
                        )

                    StreamingService.LinkType.PLAYLIST ->
                        NavigationHelper.openPlaylistFragment(
                            supportFragmentManager,
                            serviceId,
                            url,
                            title
                        )

                    else -> { // LinkType.NONE: nothing to open
                    }
                }
            } else if (intent.hasExtra(KEY_OPEN_SEARCH)) {
                val searchString = intent.getStringExtra(KEY_SEARCH_STRING) ?: ""
                val serviceId = intent.getIntExtra(KEY_SERVICE_ID, 0)
                NavigationHelper.openSearchFragment(
                    supportFragmentManager,
                    serviceId,
                    searchString
                )
            } else {
                NavigationHelper.gotoMainFragment(supportFragmentManager)
            }
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Handling intent", e)
        }
    }

    private fun openMiniPlayerIfMissing() {
        val fragmentPlayer = supportFragmentManager
            .findFragmentById(R.id.fragment_player_holder)
        if (fragmentPlayer == null) {
            // We still don't have a fragment attached to the activity. It can happen when a user
            // started popup or background players without opening a stream inside the fragment.
            // Adding it in a collapsed state (only mini player will be visible).
            NavigationHelper.showMiniPlayer(supportFragmentManager)
        }
    }

    private fun openMiniPlayerUponPlayerStarted() {
        if (intent.getSerializableExtra(KEY_LINK_TYPE) == StreamingService.LinkType.STREAM) {
            // handleIntent() already takes care of opening video detail fragment
            // due to an intent containing a STREAM link
            return
        }

        if (PlayerHolder.getInstance().isPlayerOpen) {
            // if the player is already open, no need for a broadcast receiver
            openMiniPlayerIfMissing()
        } else {
            // listen for player start intent being sent around
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (Objects.equals(
                            intent.action,
                            YpVideoDetailFragment.ACTION_PLAYER_STARTED
                        ) && PlayerHolder.getInstance().isPlayerOpen
                    ) {
                        openMiniPlayerIfMissing()
                        // At this point the player is added 100%, we can unregister. Other actions
                        // are useless since the fragment will not be removed after that.
                        broadcastReceiver?.let { unregisterReceiver(it) }
                        broadcastReceiver = null
                    }
                }
            }
            broadcastReceiver = receiver
            val intentFilter = IntentFilter()
            intentFilter.addAction(YpVideoDetailFragment.ACTION_PLAYER_STARTED)
            ContextCompat.registerReceiver(
                this,
                receiver,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )

            // If the PlayerHolder is not bound yet, but the service is running, try to bind to it.
            // Once the connection is established, the ACTION_PLAYER_STARTED will be sent.
            PlayerHolder.getInstance().tryBindIfNeeded(this)
        }
    }

    private fun openDetailFragmentFromCommentReplies(
        fm: FragmentManager,
        popBackStack: Boolean
    ) {
        // obtain the name of the fragment under the replies fragment that's going to be popped
        val fragmentUnderEntryName = if (fm.backStackEntryCount < 2) {
            null
        } else {
            fm.getBackStackEntryAt(fm.backStackEntryCount - 2).name
        }

        // the root comment is the comment for which the user opened the replies page
        val repliesFragment = fm.findFragmentByTag(CommentRepliesFragment.TAG)
            as? CommentRepliesFragment
        val rootComment = repliesFragment?.commentsInfoItem

        // sometimes this function pops the backstack, other times it's handled by the system
        if (popBackStack) {
            fm.popBackStackImmediate()
        }

        // only expand the bottom sheet back if there are no more nested comment replies fragments
        // stacked under the one that is currently being popped
        if (CommentRepliesFragment.TAG == fragmentUnderEntryName) {
            return
        }

        val behavior = BottomSheetBehavior.from(playerHolder)
        // do not return to the comment if the details fragment was closed
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            return
        }

        // scroll to the root comment once the bottom sheet expansion animation is finished
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    val detailFragment = fm.findFragmentById(R.id.fragment_player_holder)
                    if (detailFragment is YpVideoDetailFragment && rootComment != null) {
                        // should always be the case
                        detailFragment.scrollToComment(rootComment)
                    }
                    behavior.removeBottomSheetCallback(this)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // not needed, listener is removed once the sheet is expanded
            }
        })

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun bottomSheetHiddenOrCollapsed(): Boolean {
        val sheetState = BottomSheetBehavior.from(playerHolder).state
        return sheetState == BottomSheetBehavior.STATE_HIDDEN ||
            sheetState == BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun showKeepAndroidDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lastCheckKey = getString(R.string.kao_last_checked_key)
        val lastCheck = Instant.ofEpochMilli(prefs.getLong(lastCheckKey, 0))
        val now = Instant.now()

        if (lastCheck.plus(30, ChronoUnit.DAYS).isBefore(now)) {
            val detailsUrl = keepAndroidOpenDetailsUrl

            val dialog = AlertDialog.Builder(this)
                .setTitle("Keep Android Open")
                .setCancelable(false)
                .setMessage(R.string.kao_dialog_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit().putLong(lastCheckKey, now.toEpochMilli()).apply()
                }
                .setNeutralButton(R.string.kao_dialog_more_info, null)
                .show()

            // If we use setNeutralButton/setNegativeButton, dialog will close after pressing the
            // buttons, but we want it to close only when positive button is pressed
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener { ShareUtils.openUrlInBrowser(this, detailsUrl) }
        }
    }

    private val keepAndroidOpenDetailsUrl: String
        get() {
            val supportedLanguages = listOf(
                "fr", "de", "ca", "es", "id", "it", "pl", "pt", "cs", "sk", "fa", "ar", "tr",
                "el", "th", "ru", "uk", "ko", "zh", "ja"
            )
            val kaoBaseUrl = "https://keepandroidopen.org/"
            val locale = Localization.getAppLocale()
            return if (supportedLanguages.contains(locale.language)) {
                if ("zh" == locale.language) {
                    kaoBaseUrl + if ("TW" == locale.country) "zh-TW" else "zh-CN"
                } else {
                    kaoBaseUrl + locale.language
                }
            } else {
                kaoBaseUrl
            }
        }

    private fun showApi23RequirementDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return // only show dialog on the devices that will stop being supported
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val shownKey = getString(R.string.api23_requirement_dialog_shown_key)
        if (prefs.getBoolean(shownKey, false)) {
            return // dialog was already shown in the past, no need to show it again
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.api23_requirement_dialog_title)
            .setCancelable(false)
            .setMessage(R.string.api23_requirement_dialog_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putBoolean(shownKey, true).apply()
            }
            .setNegativeButton(R.string.api23_requirement_dialog_blogpost, null)
            .show()

        // If we use setNegativeButton, dialog will close after pressing the button,
        // but we want it to close only when positive button is pressed
        val blogpostUrl = "https://newpipe.net/blog/pinned/announcement/drop-android-5/"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener { ShareUtils.openUrlInBrowser(this, blogpostUrl) }
    }

    companion object {
        private const val TAG = "MainActivity"

        @SuppressWarnings("ConstantConditions")
        @JvmField
        val DEBUG: Boolean = BuildConfig.BUILD_TYPE != "release"

        const val KEY_IS_IN_BACKGROUND = "is_in_background"

        private fun isBottomNavigationRoot(fragment: Fragment?): Boolean = fragment is MainFragment ||
            fragment is YpSearchFragment ||
            fragment is FeedFragment ||
            fragment is LibraryFragment
    }
}
