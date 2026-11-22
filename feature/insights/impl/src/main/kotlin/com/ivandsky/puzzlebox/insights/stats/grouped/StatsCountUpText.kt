package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A headline number with the page's motion: on arrival its numbers roll up from 0 over [MotionTokens.CountUpDuration]
 * (M03, [countUpText]), and when [text] changes with a Since or game change they roll from the old value to the new
 * one over the same time (M05), from what is on screen if a count is still running. Reduced motion shows the value at
 * once and swaps it without counting (M08). With no [StatsMotion] provided, or once [key] has arrived (scrolled away
 * and back), it is a plain [Text].
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
    val reduced = rememberReducedMotion()
    val inspect = entranceSettled()
    val counts = remember { !reduced && !inspect && stats.claimArrival(key) }
    val progress = remember { Animatable(if (counts) 0f else 1f) }
    var target by remember { mutableStateOf(text) }
    var from by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        if (text != target) {
            from = countUpText(target, progress.value, from)
            target = text
            progress.snapTo(if (reduced || inspect) 1f else 0f)
        }
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(MotionTokens.CountUpDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    Text(countUpText(target, progress.value, from), modifier, color = color, style = style)
}
