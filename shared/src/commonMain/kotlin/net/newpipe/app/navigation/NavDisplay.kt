/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import net.newpipe.app.theme.LocalReduceMotion
import net.newpipe.app.theme.MotionDurations
import net.newpipe.app.theme.MotionEasings
import net.newpipe.app.theme.motionSpec
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf

/**
 * Navigation display for compose screens
 * @param startDestination Starting destination for the app
 * @param navigator Navigator to help with navigation, injected by Koin
 * @param onCloseRequest Callback to close the app
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun NavDisplay(
    startDestination: Destination,
    onCloseRequest: () -> Unit,
    navigator: Navigator = koinInject {
        parametersOf(startDestination, onCloseRequest)
    }
) {
    val backstack = rememberSerializable(serializer = SnapshotStateListSerializer<Destination>()) {
        navigator.backstack
    }

    val reduceMotion = LocalReduceMotion.current
    NavDisplay(
        backStack = backstack,
        onBack = { navigator.navigateUp() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        // YewPlay Motion Design System: forward = slide-in from the end edge,
        // back = mirror. Collapses to a plain crossfade when reduce-motion is on.
        transitionSpec = {
            slideInHorizontally(
                motionSpec(reduceMotion, MotionDurations.MEDIUM, MotionEasings.StandardDecelerate)
            ) { it / 4 } + fadeIn(motionSpec(reduceMotion, MotionDurations.MEDIUM))
        },
        popTransitionSpec = {
            slideInHorizontally(
                motionSpec(reduceMotion, MotionDurations.MEDIUM, MotionEasings.StandardDecelerate)
            ) { -it / 4 } + fadeIn(motionSpec(reduceMotion, MotionDurations.MEDIUM))
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(
                motionSpec(reduceMotion, MotionDurations.MEDIUM, MotionEasings.StandardDecelerate)
            ) { -it / 4 } + fadeIn(motionSpec(reduceMotion, MotionDurations.MEDIUM))
        },
        entryProvider = koinEntryProvider()
    )
}
