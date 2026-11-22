package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A headline number with the page's motion: on arrival its numbers roll up from 0 over [MotionTokens.CountUpDuration]
 * (M03, [countUpText]), and when [text] changes with a Since or game change it morphs in place (M05, [MorphCell]).
 * Reduced motion shows the value at once and swaps it without a morph (M08). With no [StatsMotion] provided, or
 * once [key] has arrived (scrolled away and back), it is a plain [Text].
 */
@Composable
internal fun StatsCountUpText(
    text: String,
    key: Any,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val stats = LocalStatsMotion.current
    if (stats == null) {
        Text(text, modifier, color = color, style = style)
        return
    }
    val instant = rememberReducedMotion() || entranceSettled()
    val counts = remember { !instant && stats.claimArrival(key) }
    val progress = remember { Animatable(if (counts) 0f else 1f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(MotionTokens.CountUpDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    MorphCell(text, modifier) { shown ->
        Text(countUpText(shown, progress.value), color = color, style = style)
    }
}
