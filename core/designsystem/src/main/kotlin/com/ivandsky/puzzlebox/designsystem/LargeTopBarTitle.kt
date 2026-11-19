package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

/**
 * The large bar's title on one line: the display size while it fits, shrinking to the title size when the actions
 * leave it less room, then cut with an ellipsis. Its line keeps the height of a display-size line, so the bar never
 * changes height and every tab's title sits where Today's plain one does.
 */
@Composable
fun LargeTopBarTitle(text: String, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    // Measured, not the style's lineHeight: the rendered line (font padding included) is what Today's plain title takes.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val line = remember(measurer, type.displaySmall, density) {
        with(density) { measurer.measure(" ", type.displaySmall, maxLines = 1).size.height.toDp() }
    }
    // A shrunk title sits centered in the display line, level with the actions.
    Box(modifier.heightIn(min = line), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = type.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = type.titleLarge.fontSize, maxFontSize = type.displaySmall.fontSize),
        )
    }
}
