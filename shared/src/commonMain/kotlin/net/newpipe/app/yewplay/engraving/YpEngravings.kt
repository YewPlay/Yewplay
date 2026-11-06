/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.yewplay.engraving

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The paths of an engraving, as traced from the proposal. Each path is kept in pieces (joined
 * before parsing) so that no string constant gets too long for the compiled class.
 */
internal class EngravingData(
    val width: Float,
    val height: Float,
    val lines: List<List<String>>,
    val fruit: List<Pair<Color, List<String>>>
)

/**
 * YewPlay: a yew engraving (proposal, p. 9) in two layers. [lines] is drawn in a single color
 * and is meant to be tinted with the theme (ivory on dark, ink on light); [fruit] keeps its
 * own colors (the red arils).
 */
class YpEngravingArt internal constructor(
    val lines: ImageVector,
    val fruit: ImageVector,
    val aspectRatio: Float
)

/** YewPlay: the yew engravings of the empty states, the welcome screen and "About". */
object YpEngravings {
    val welcome: YpEngravingArt by lazy { build("welcome", WelcomeEngraving) }
    val subscriptions: YpEngravingArt by lazy { build("subscriptions", SubscriptionsEngraving) }
    val downloads: YpEngravingArt by lazy { build("downloads", DownloadsEngraving) }
    val about: YpEngravingArt by lazy { build("about", AboutEngraving) }

    private fun build(name: String, data: EngravingData) = YpEngravingArt(
        lines = vector("${name}_lines", data, data.lines.map { Color.White to it }),
        fruit = vector("${name}_fruit", data, data.fruit),
        aspectRatio = data.width / data.height
    )

    private fun vector(
        name: String,
        data: EngravingData,
        paths: List<Pair<Color, List<String>>>
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = "yp_engraving_$name",
            defaultWidth = data.width.dp,
            defaultHeight = data.height.dp,
            viewportWidth = data.width,
            viewportHeight = data.height
        )
        paths.forEach { (color, path) ->
            builder.addPath(
                pathData = addPathNodes(path.joinToString(separator = "")),
                fill = SolidColor(color)
            )
        }
        return builder.build()
    }
}
