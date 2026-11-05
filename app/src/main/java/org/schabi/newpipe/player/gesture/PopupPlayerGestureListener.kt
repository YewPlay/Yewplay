package org.schabi.newpipe.player.gesture

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.ui.PopupPlayerUi

class PopupPlayerGestureListener(
    private val playerUi: PopupPlayerUi
) : BasePlayerGestureListener(playerUi) {

    private var isMoving = false

    private var initialPopupX: Int = -1
    private var initialPopupY: Int = -1
    private var isResizing = false

    // initial coordinates and distance between fingers
    private var initPointerDistance = -1.0
    private var initFirstPointerX = -1f
    private var initFirstPointerY = -1f
    private var initSecPointerX = -1f
    private var initSecPointerY = -1f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        super.onTouch(v, event)
        if (event.pointerCount == 2 && !isMoving && !isResizing) {
            if (DEBUG) {
                Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.")
            }
            onPopupResizingStart()

            // record coordinates of fingers
            initFirstPointerX = event.getX(0)
            initFirstPointerY = event.getY(0)
            initSecPointerX = event.getX(1)
            initSecPointerY = event.getY(1)
            // record distance between fingers
            initPointerDistance = hypot(
                initFirstPointerX - initSecPointerX.toDouble(),
                initFirstPointerY - initSecPointerY.toDouble()
            )

            isResizing = true
        }
        if (event.action == MotionEvent.ACTION_MOVE && !isMoving && isResizing) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onTouch() ACTION_MOVE > v = [$v], e1.getRaw =" +
                        "[${event.rawX}, ${event.rawY}]"
                )
            }
            return handleMultiDrag(event)
        }
        if (event.action == MotionEvent.ACTION_UP) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onTouch() ACTION_UP > v = [$v], e1.getRaw =" +
                        " [${event.rawX}, ${event.rawY}]"
                )
            }
            if (isMoving) {
                isMoving = false
                onScrollEnd(event)
            }
            if (isResizing) {
                isResizing = false

                initPointerDistance = (-1).toDouble()
                initFirstPointerX = (-1).toFloat()
                initFirstPointerY = (-1).toFloat()
                initSecPointerX = (-1).toFloat()
                initSecPointerY = (-1).toFloat()

                onPopupResizingEnd()
                player.changeState(player.currentState)
            }
            if (!playerUi.isPopupClosing) {
                playerUi.savePopupPositionAndSizeToPrefs()
            }
        }

        v.performClick()
        return true
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (playerUi.isInsideClosingRadius(event)) {
            playerUi.closePopup()
        } else if (!playerUi.isPopupClosing) {
            playerUi.closeOverlayButton?.animate(false, 200)
            playerUi.setClosingOverlayVisible(false)
        }
    }

    private fun handleMultiDrag(event: MotionEvent): Boolean {
        if (initPointerDistance == -1.0 || event.pointerCount != 2) {
            return false
        }

        // get the movements of the fingers
        val firstPointerMove = hypot(
            event.getX(0) - initFirstPointerX.toDouble(),
            event.getY(0) - initFirstPointerY.toDouble()
        )
        val secPointerMove = hypot(
            event.getX(1) - initSecPointerX.toDouble(),
            event.getY(1) - initSecPointerY.toDouble()
        )

        // minimum threshold beyond which pinch gesture will work
        val minimumMove = ViewConfiguration.get(player.context).scaledTouchSlop
        if (max(firstPointerMove, secPointerMove) <= minimumMove) {
            return false
        }

        // calculate current distance between the pointers
        val currentPointerDistance = hypot(
            event.getX(0) - event.getX(1).toDouble(),
            event.getY(0) - event.getY(1).toDouble()
        )

        val params = playerUi.popupLayoutParams ?: return false
        val popupWidth = params.width.toDouble()
        // change co-ordinates of popup so the center stays at the same position
        val newWidth = popupWidth * currentPointerDistance / initPointerDistance
        initPointerDistance = currentPointerDistance
        params.x += ((popupWidth - newWidth) / 2.0).toInt()

        playerUi.checkPopupPositionBounds()
        playerUi.updateScreenSize()
        playerUi.changePopupSize(min(playerUi.screenWidth.toDouble(), newWidth).toInt())
        return true
    }

    private fun onPopupResizingStart() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingStart called")
        }
        playerUi.setLoadingVisible(false)
        playerUi.hideControls(0, 0)
        views.fastSeekOverlay.animate(false, 0)
        playerUi.hideSeekDisplay()
    }

    private fun onPopupResizingEnd() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingEnd called")
        }
    }

    override fun onLongPress(e: MotionEvent) {
        playerUi.updateScreenSize()
        playerUi.checkPopupPositionBounds()
        playerUi.changePopupSize(playerUi.screenWidth)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return if (player.popupPlayerSelected()) {
            val absVelocityX = abs(velocityX)
            val absVelocityY = abs(velocityY)
            if (absVelocityX.coerceAtLeast(absVelocityY) > TOSS_FLING_VELOCITY) {
                if (absVelocityX > TOSS_FLING_VELOCITY) {
                    playerUi.popupLayoutParams.x = velocityX.toInt()
                }
                if (absVelocityY > TOSS_FLING_VELOCITY) {
                    playerUi.popupLayoutParams.y = velocityY.toInt()
                }
                playerUi.checkPopupPositionBounds()
                playerUi.popupLayoutParams?.let { params ->
                    playerUi.windowManager?.updateViewLayout(views.root, params)
                }
                return true
            }
            return false
        } else {
            true
        }
    }

    override fun onDownNotDoubleTapping(e: MotionEvent): Boolean {
        // Fix popup position when the user touch it, it may have the wrong one
        // because the soft input is visible (the draggable area is currently resized).
        playerUi.updateScreenSize()
        playerUi.checkPopupPositionBounds()
        playerUi.popupLayoutParams?.let {
            initialPopupX = it.x
            initialPopupY = it.y
        }
        return true // we want `super.onDown(e)` to be called
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
        }

        if (isDoubleTapping) {
            return true
        }
        if (player.exoPlayerIsNull()) {
            return false
        }

        onSingleTap()
        return true
    }

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null) {
            return false
        }

        if (isResizing) {
            return super.onScroll(initialEvent, movingEvent, distanceX, distanceY)
        }

        if (!isMoving) {
            playerUi.closeOverlayButton?.animate(true, 200)
        }

        isMoving = true

        val params = playerUi.popupLayoutParams ?: return false
        val diffX = (movingEvent.rawX - initialEvent.rawX)
        val posX = (initialPopupX + diffX).coerceIn(
            0f,
            (playerUi.screenWidth - params.width).toFloat()
                .coerceAtLeast(0f)
        )
        val diffY = (movingEvent.rawY - initialEvent.rawY)
        val posY = (initialPopupY + diffY).coerceIn(
            0f,
            (playerUi.screenHeight - params.height).toFloat()
                .coerceAtLeast(0f)
        )

        params.x = posX.toInt()
        params.y = posY.toInt()

        // -- Determine if the ClosingOverlayView (red X) has to be shown or hidden --
        playerUi.setClosingOverlayVisible(playerUi.isInsideClosingRadius(movingEvent))

        playerUi.windowManager?.updateViewLayout(views.root, params)
        return true
    }

    override fun getDisplayPortion(e: MotionEvent): DisplayPortion {
        val width = playerUi.popupLayoutParams?.width ?: views.root.width
        return when {
            e.x < width / 3.0 -> DisplayPortion.LEFT
            e.x > width * 2.0 / 3.0 -> DisplayPortion.RIGHT
            else -> DisplayPortion.MIDDLE
        }
    }

    override fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion {
        val width = playerUi.popupLayoutParams?.width ?: views.root.width
        return when {
            e.x < width / 2.0 -> DisplayPortion.LEFT_HALF
            else -> DisplayPortion.RIGHT_HALF
        }
    }

    companion object {
        private val TAG = PopupPlayerGestureListener::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG
        private const val TOSS_FLING_VELOCITY = 2500
    }
}
