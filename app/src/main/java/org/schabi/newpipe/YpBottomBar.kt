package org.schabi.newpipe

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yewplay.core.designsystem.ypPressFeedback
import net.newpipe.app.theme.LocalReduceMotion
import net.newpipe.app.theme.MotionDurations
import net.newpipe.app.theme.MotionEasings
import net.newpipe.app.theme.motionSpec

/** YewPlay: the four fixed destinations of the floating bottom bar. */
enum class YpNavDestination {
    HOME,
    SEARCH,
    SUBSCRIPTIONS,
    LIBRARY
}

@DrawableRes
private fun YpNavDestination.icon(selected: Boolean): Int = when (this) {
    YpNavDestination.HOME ->
        if (selected) R.drawable.yp_ic_nav_home_fill else R.drawable.yp_ic_nav_home

    YpNavDestination.SEARCH ->
        if (selected) R.drawable.yp_ic_nav_search_fill else R.drawable.yp_ic_nav_search

    YpNavDestination.SUBSCRIPTIONS ->
        if (selected) R.drawable.yp_ic_nav_subscriptions_fill else R.drawable.yp_ic_nav_subscriptions

    YpNavDestination.LIBRARY ->
        if (selected) R.drawable.yp_ic_nav_library_fill else R.drawable.yp_ic_nav_library
}

@StringRes
private fun YpNavDestination.label(): Int = when (this) {
    YpNavDestination.HOME -> R.string.yp_nav_home
    YpNavDestination.SEARCH -> R.string.yp_nav_search
    YpNavDestination.SUBSCRIPTIONS -> R.string.yp_nav_subscriptions
    YpNavDestination.LIBRARY -> R.string.yp_nav_library
}

/**
 * YewPlay: floating bottom bar (replaces the BottomNavigationView of activity_main.xml).
 * A capsule with the four destinations; while the mini player is visible only the bottom
 * corners stay rounded so both look like a single floating piece. The slide offset of the
 * player bottom sheet is applied by the activity as translationY on the hosting view.
 *
 * @param selected the destination matching the visible screen, or null on detail screens
 * @param attachedToMiniPlayer whether the mini player sits right above the bar
 * @param hidden true on screens shown full screen (welcome) and while the keyboard is open
 * @param onDestination called when a destination is tapped (also on reselection)
 */
@Composable
fun YpBottomBar(
    selected: YpNavDestination?,
    attachedToMiniPlayer: Boolean,
    hidden: Boolean,
    onDestination: (YpNavDestination) -> Unit
) {
    if (hidden) {
        return
    }
    val corner = 26.dp
    val shape = if (attachedToMiniPlayer) {
        RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
    } else {
        RoundedCornerShape(corner)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YpNavDestination.entries.forEach { destination ->
            val isSelected = destination == selected
            // YewPlay Motion: selection color and icon swap animate with the
            // fast tokens; pressing scales subtly (legacy keeps no ripple).
            val tint by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = motionSpec(
                    LocalReduceMotion.current,
                    MotionDurations.FAST,
                    MotionEasings.Standard
                ),
                label = "navTint"
            )
            val label = stringResource(destination.label())
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .ypPressFeedback(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onDestination(destination) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Crossfade(
                    targetState = destination.icon(isSelected),
                    animationSpec = motionSpec(
                        LocalReduceMotion.current,
                        MotionDurations.FAST,
                        MotionEasings.Standard
                    ),
                    label = "navIcon"
                ) { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = label,
                    color = tint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
