package org.schabi.newpipe.player.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import androidx.media3.ui.SubtitleView
import org.schabi.newpipe.R
import org.schabi.newpipe.views.ExpandableSurfaceView
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay

/**
 * The view hierarchy of the video player surface, built in code (it replaces `player.xml`).
 * Only the video surface itself and the views driven imperatively by the player and the gesture
 * listeners remain views; every piece of playback chrome is rendered by [controlsView] in
 * Compose.
 */
class YpPlayerViews(context: Context) {
    @JvmField
    val root: RelativeLayout = RelativeLayout(context).apply {
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        setBackgroundColor(Color.BLACK)
        gravity = android.view.Gravity.CENTER
        fitsSystemWindows = true
        visibility = View.GONE
    }

    @JvmField
    val surfaceView: ExpandableSurfaceView = ExpandableSurfaceView(context, null).apply {
        id = View.generateViewId()
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
    }

    @JvmField
    val surfaceForeground: View = View(context).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            addRule(RelativeLayout.ALIGN_BOTTOM, surfaceView.id)
        }
    }

    @JvmField
    val subtitleView: SubtitleView = SubtitleView(context).apply {
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
    }

    @JvmField
    val endScreen: ImageView = ImageView(context).apply {
        visibility = View.GONE
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    @JvmField
    val fastSeekOverlay: PlayerFastSeekOverlay = PlayerFastSeekOverlay(context, null).apply {
        alpha = 0f
        visibility = View.INVISIBLE // required for the first appearance fading correctly
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    /** Hosts every Compose control: chrome, shadows, menus, indicators, loading and panels. */
    @JvmField
    val controlsView: ComposeView = ComposeView(context).apply {
        // FocusAwareCoordinator and the bottom-sheet touch helpers look the chrome up by this id
        id = R.id.playbackControlRoot
        layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    init {
        root.addView(surfaceView)
        root.addView(surfaceForeground)
        root.addView(subtitleView)
        root.addView(endScreen)
        root.addView(fastSeekOverlay)
        root.addView(controlsView)

        // The fast seek overlay should not be padded by window insets: instead it covers the
        // whole player and its center coincides with the center of the other controls.
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val params = fastSeekOverlay.layoutParams as RelativeLayout.LayoutParams
            params.leftMargin = -v.paddingRight
            params.topMargin = -v.paddingBottom
            params.rightMargin = -v.paddingLeft
            params.bottomMargin = -v.paddingTop
            fastSeekOverlay.layoutParams = params
        }
    }
}
