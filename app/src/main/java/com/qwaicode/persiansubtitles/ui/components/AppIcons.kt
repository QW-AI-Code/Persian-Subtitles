package com.qwaicode.persiansubtitles.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons the Material set does not get right for a right-to-left app.
 */
object AppIcons {

    /**
     * "Checked list" — used for the translation progress card and the review button.
     *
     * The app used `Icons.AutoMirrored.Filled.FactCheck`. In a right-to-left layout
     * an auto-mirrored icon is flipped as a whole, and so was the check mark inside
     * it: it came out reversed (long stroke on the left), which reads as a mistake.
     * The plain `FactCheck` would keep the tick but put the lines on the wrong side
     * for Persian. This one is drawn for RTL: the lines sit on the right, where a
     * Persian list starts, and the tick on the left is a normal, unmirrored ✓.
     * `autoMirror` is off on purpose, so no layout direction can flip it again.
     */
    val FactCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.FactCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).addPath(
            pathData = addPathNodes(
                // The card, then the three lines and the tick as cut-outs (even-odd).
                "M4,3H20A2,2 0 0 1 22,5V19A2,2 0 0 1 20,21H4A2,2 0 0 1 2,19V5A2,2 0 0 1 4,3Z" +
                    "M14,7H19V9H14Z" +
                    "M14,11H19V13H14Z" +
                    "M14,15H19V17H14Z" +
                    "M4.6,12.3L6.0,10.9L7.9,12.8L11.3,9.4L12.7,10.8L7.9,15.6Z"
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        ).build()
    }
}
