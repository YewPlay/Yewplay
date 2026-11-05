package org.schabi.newpipe.player.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.PopupPlayerGestureListener
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.util.DeviceUtils

class PopupPlayerUi(player: Player, views: YpPlayerViews) : VideoPlayerUi(player, views) {

    /** The close button in a separate overlay window, shown while the popup is dragged. */
    var closeOverlayView: FrameLayout? = null
        private set
    var closeOverlayButton: FloatingActionButton? = null
        private set

    var isPopupClosing = false
        private set

    var screenWidth = 0
        private set
    var screenHeight = 0
        private set

    var popupLayoutParams: WindowManager.LayoutParams? = null
        private set
    val windowManager: WindowManager? = ContextCompat.getSystemService(
        context,
        WindowManager::class.java
    )

    // ///////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    // ///////////////////////////////////////////////////////////

    override fun setupAfterIntent() {
        super.setupAfterIntent()
        initPopup()
        initPopupCloseOverlay()
    }

    override fun buildGestureListener(): BasePlayerGestureListener {
        return PopupPlayerGestureListener(this)
    }

    private fun initPopup() {
        // popup is already added to windowManager
        if (popupHasParent()) {
            return
        }

        updateScreenSize()

        popupLayoutParams = retrievePopupLayoutParamsFromPrefs()
        popupLayoutParams?.let { params ->
            views.surfaceView.setHeights(params.height, params.height)

            checkPopupPositionBounds()

            windowManager?.addView(views.root, params)
            setupVideoSurfaceIfNeeded() // now there is a parent, we can setup the video surface
        }

        // popup doesn't have aspectRatio selector, using FIT automatically
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }

    private fun initPopupCloseOverlay() {
        // closeOverlayView is already added to windowManager
        if (closeOverlayView != null) {
            return
        }

        val button = FloatingActionButton(context).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = context.getString(R.string.close)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(context, R.color.yp_rubi)
            )
            borderWidth = 0
            visibility = android.view.View.GONE
        }
        val overlay = FrameLayout(context).apply {
            addView(
                button,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = DeviceUtils.dpToPx(24, context)
                }
            )
        }
        closeOverlayButton = button
        closeOverlayView = overlay
        windowManager?.addView(overlay, buildCloseOverlayLayoutParams())
    }

    override fun initPlayback() {
        super.initPlayback()
        // make sure video and text tracks are enabled if the screen is turned on (which should
        // always be the case), in case the user switched from background player to popup player
        player.useVideoAndSubtitles(player.isScreenOn)
    }

    override fun setupElementsVisibility() {
        chromeState.update {
            it.copy(
                popupMode = true,
                showFullScreen = true,
                showResize = false,
                metadataVisible = false,
                showQueue = false,
                showSegments = false,
                showShare = false,
                showKodi = false,
                showOpenInBrowser = false,
                showMute = false,
                closeVisible = false,
                secondaryVisible = true
            )
        }
        super.setupElementsVisibility()
    }

    override fun removeViewFromParent() {
        // view was added by windowManager for the popup player
        windowManager?.removeViewImmediate(views.root)
    }

    override fun destroy() {
        super.destroy()
        removePopupFromView()
    }

    // ///////////////////////////////////////////////////////////
    // Broadcast receiver
    // ///////////////////////////////////////////////////////////

    override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if (Intent.ACTION_CONFIGURATION_CHANGED == intent.action) {
            updateScreenSize()
            popupLayoutParams?.let { changePopupSize(it.width) }
            checkPopupPositionBounds()
        } else if (player.isPlaying || player.isLoading) {
            if (Intent.ACTION_SCREEN_OFF == intent.action) {
                // use only audio source when the screen turns off while the popup player is
                // playing
                player.useVideoAndSubtitles(false)
            } else if (Intent.ACTION_SCREEN_ON == intent.action) {
                // restore video source when the screen turns on and the user was watching
                // video in the popup
                player.useVideoAndSubtitles(true)
            }
        }
    }

    // ///////////////////////////////////////////////////////////
    // Popup position and size
    // ///////////////////////////////////////////////////////////

    /**
     * Check if [popupLayoutParams]' position is within an arbitrary boundary that goes from
     * (0, 0) to (screenWidth, screenHeight). If it's out of these boundaries,
     * [popupLayoutParams]' position is changed.
     */
    fun checkPopupPositionBounds() {
        popupLayoutParams?.let { params ->
            params.x = MathUtils.clamp(params.x, 0, screenWidth - params.width)
            params.y = MathUtils.clamp(params.y, 0, screenHeight - params.height)
        }
    }

    fun updateScreenSize() {
        val manager = windowManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = manager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val windowInsets = windowMetrics.windowInsets
            val insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
            )
            screenWidth = bounds.width() - (insets.left + insets.right)
            screenHeight = bounds.height() - (insets.top + insets.bottom)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    /**
     * Changes the size of the popup based on the width.
     * @param width the new width, height is calculated with
     * [PlayerHelper.getMinimumVideoHeight]
     */
    fun changePopupSize(width: Int) {
        if (anyPopupViewIsNull()) {
            return
        }

        val minimumWidth = context.resources.getDimension(R.dimen.popup_minimum_width)
        val actualWidth = MathUtils.clamp(width, minimumWidth.toInt(), screenWidth)
        val actualHeight = PlayerHelper.getMinimumVideoHeight(width.toFloat()).toInt()

        popupLayoutParams?.let { params ->
            params.width = actualWidth
            params.height = actualHeight
            views.surfaceView.setHeights(params.height, params.height)
            windowManager?.updateViewLayout(views.root, params)
        }
    }

    override fun calculateMaxEndScreenThumbnailHeight(bitmap: Bitmap): Float {
        // no need for the end screen thumbnail to be resized on the popup player: it's only
        // needed for the main player so that it is enlarged correctly inside the fragment
        return bitmap.height.toFloat()
    }

    // ///////////////////////////////////////////////////////////
    // Popup closing
    // ///////////////////////////////////////////////////////////

    fun closePopup() {
        if (isPopupClosing) {
            return
        }
        isPopupClosing = true

        player.saveStreamProgressState()
        windowManager?.removeView(views.root)

        animatePopupOverlayAndFinishService()
    }

    fun removePopupFromView() {
        // wrap in try-catch since it could sometimes generate errors randomly
        try {
            if (popupHasParent()) {
                windowManager?.removeView(views.root)
            }
        } catch (e: IllegalArgumentException) {
            // the view was not attached to the window manager
        }

        try {
            if (closeOverlayView?.parent != null) {
                windowManager?.removeView(closeOverlayView)
            }
        } catch (e: IllegalArgumentException) {
            // the overlay was not attached to the window manager
        }
    }

    private fun animatePopupOverlayAndFinishService() {
        val button = closeOverlayButton ?: return
        val overlay = closeOverlayView ?: return
        val targetTranslationY = (button.rootView.height - button.y).toInt()

        button.animate().setListener(null).cancel()
        button.animate()
            .setInterpolator(AnticipateInterpolator())
            .translationY(targetTranslationY.toFloat())
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    end()
                }

                override fun onAnimationEnd(animation: Animator) {
                    end()
                }

                private fun end() {
                    windowManager?.removeView(overlay)
                    closeOverlayView = null
                    closeOverlayButton = null
                    player.service.destroyPlayerAndStopService()
                }
            }).start()
    }

    // ///////////////////////////////////////////////////////////
    // Playback states
    // ///////////////////////////////////////////////////////////

    private fun changePopupWindowFlags(flags: Int) {
        if (!anyPopupViewIsNull()) {
            popupLayoutParams?.let {
                it.flags = flags
                windowManager?.updateViewLayout(views.root, it)
            }
        }
    }

    override fun onPlaying() {
        super.onPlaying()
        changePopupWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS)
    }

    override fun onPaused() {
        super.onPaused()
        changePopupWindowFlags(IDLE_WINDOW_FLAGS)
    }

    override fun onCompleted() {
        super.onCompleted()
        changePopupWindowFlags(IDLE_WINDOW_FLAGS)
    }

    override fun setupSubtitleView(captionScale: Float) {
        views.subtitleView.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale
        )
    }

    override fun onPlaybackSpeedClicked() {
        // the popup shows the speed menu inline; nothing to do here, the Compose chrome
        // already opens the menu in popup mode
    }

    // ///////////////////////////////////////////////////////////
    // Gestures
    // ///////////////////////////////////////////////////////////

    private fun distanceFromCloseButton(popupMotionEvent: MotionEvent): Int {
        val button = closeOverlayButton ?: return Int.MAX_VALUE
        val params = popupLayoutParams ?: return Int.MAX_VALUE
        val closeOverlayButtonX = button.left + button.width / 2
        val closeOverlayButtonY = button.top + button.height / 2

        val fingerX = params.x + popupMotionEvent.x
        val fingerY = params.y + popupMotionEvent.y

        return kotlin.math.hypot(
            (closeOverlayButtonX - fingerX).toDouble(),
            (closeOverlayButtonY - fingerY).toDouble()
        ).toInt()
    }

    private fun getClosingRadius(): Float {
        val buttonRadius = (closeOverlayButton?.width ?: 0) / 2
        // 20% wider than the button itself
        return buttonRadius * 1.2f
    }

    fun isInsideClosingRadius(popupMotionEvent: MotionEvent): Boolean {
        return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius()
    }

    // ///////////////////////////////////////////////////////////
    // Popup & closing overlay layout params + saving popup position and size
    // ///////////////////////////////////////////////////////////

    /**
     * [screenWidth] and [screenHeight] must have been initialized.
     * @return the popup starting layout params
     */
    fun retrievePopupLayoutParamsFromPrefs(): WindowManager.LayoutParams {
        val prefs: SharedPreferences = player.prefs
        val context: Context = player.context

        val popupRememberSizeAndPos = prefs.getBoolean(
            context.getString(R.string.popup_remember_size_pos_key),
            true
        )
        val defaultSize = context.resources.getDimension(R.dimen.popup_default_width)
        val popupWidth = if (popupRememberSizeAndPos) {
            prefs.getFloat(context.getString(R.string.popup_saved_width_key), defaultSize)
        } else {
            defaultSize
        }
        val popupHeight = PlayerHelper.getMinimumVideoHeight(popupWidth)

        val params = WindowManager.LayoutParams(
            popupWidth.toInt(),
            popupHeight.toInt(),
            popupLayoutParamType(),
            IDLE_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.LEFT or Gravity.TOP
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        val centerX = (screenWidth / 2f - popupWidth / 2f).toInt()
        val centerY = (screenHeight / 2f - popupHeight / 2f).toInt()
        params.x = if (popupRememberSizeAndPos) {
            prefs.getInt(context.getString(R.string.popup_saved_x_key), centerX)
        } else {
            centerX
        }
        params.y = if (popupRememberSizeAndPos) {
            prefs.getInt(context.getString(R.string.popup_saved_y_key), centerY)
        } else {
            centerY
        }

        return params
    }

    fun savePopupPositionAndSizeToPrefs() {
        popupLayoutParams?.let { params ->
            val context: Context = player.context
            player.prefs.edit()
                .putFloat(context.getString(R.string.popup_saved_width_key), params.width.toFloat())
                .putInt(context.getString(R.string.popup_saved_x_key), params.x)
                .putInt(context.getString(R.string.popup_saved_y_key), params.y)
                .apply()
        }
    }

    private fun popupHasParent(): Boolean {
        return views.root.layoutParams is WindowManager.LayoutParams &&
            views.root.parent != null
    }

    private fun anyPopupViewIsNull(): Boolean {
        return popupLayoutParams == null || windowManager == null || views.root.parent == null
    }

    companion object {
        /**
         * Maximum opacity allowed for Android 12 and higher to allow touches on other apps when
         * using the popup player.
         *
         * This value is hardcoded instead of being get dynamically with the method linked in the
         * constant documentation below, because it is not static and popup player layout
         * parameters are generated with static methods.
         *
         * @see WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
         */
        private const val MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER = 0.8f

        const val IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        const val ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        @JvmStatic
        fun buildCloseOverlayLayoutParams(): WindowManager.LayoutParams {
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM

            val closeOverlayLayoutParams = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                popupLayoutParamType(),
                flags,
                PixelFormat.TRANSLUCENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // setting maximum opacity allowed for touch events to other apps for Android 12
                // and higher to prevent non interaction when using other apps with the popup
                closeOverlayLayoutParams.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
            }

            closeOverlayLayoutParams.gravity = Gravity.LEFT or Gravity.TOP
            closeOverlayLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            return closeOverlayLayoutParams
        }

        @JvmStatic
        fun popupLayoutParamType(): Int {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
        }
    }
}
