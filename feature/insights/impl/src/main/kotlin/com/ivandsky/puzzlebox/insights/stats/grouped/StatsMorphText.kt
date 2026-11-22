package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ivandsky.puzzlebox.designsystem.MorphCell

/**
 * Text that morphs in place when it changes with a Since or game change (M05, [MorphCell]: the old text fades and
 * blurs out as the new one comes in). Reduced motion swaps it at once (M08). With no [StatsMotion] provided it is a
 * plain [Text].
 */
@Composable
internal fun StatsMorphText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    if (LocalStatsMotion.current == null) {
        Text(text, modifier, color = color, style = style)
    } else {
        MorphCell(text, modifier) { shown -> Text(shown, color = color, style = style) }
    }
}
