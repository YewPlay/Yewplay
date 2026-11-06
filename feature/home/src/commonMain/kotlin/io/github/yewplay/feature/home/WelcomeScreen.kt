/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpEngraving
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import net.newpipe.app.yewplay.engraving.YpEngravings

/** Texts of the welcome screen, from the app's strings. */
data class WelcomeStrings(
    val appName: String,
    val title: String,
    val text: String,
    val start: String,
    val import: String
)

/** Images of the welcome screen, from the app's drawables. */
data class WelcomeImages(val logo: Painter)

/**
 * Welcome (proposal, p. 9): the big yew branch on top, the promise of the app in two lines,
 * "Get started" and "Import subscriptions from NewPipe". Shown on the first start only.
 */
@Composable
fun WelcomeScreen(
    strings: WelcomeStrings,
    images: WelcomeImages,
    onStart: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // edge to edge: the branch leaves the screen on both sides, as in the proposal
        YpEngraving(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(YpEngravings.welcome.aspectRatio),
            art = YpEngravings.welcome,
            contentScale = ContentScale.FillWidth
        )
        Column(modifier = Modifier.padding(horizontal = YpScreenMargin)) {
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(modifier = Modifier.size(32.dp), painter = images.logo, contentDescription = null)
                Text(text = strings.appName, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                modifier = Modifier.padding(top = 18.dp),
                text = strings.title,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                modifier = Modifier.padding(top = 10.dp),
                text = strings.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            YpPillButton(
                modifier = Modifier.fillMaxWidth(),
                label = strings.start,
                primary = true,
                onClick = onStart
            )
            YpPillButton(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 24.dp)
                    .fillMaxWidth(),
                label = strings.import,
                primary = false,
                onClick = onImport
            )
        }
    }
}
