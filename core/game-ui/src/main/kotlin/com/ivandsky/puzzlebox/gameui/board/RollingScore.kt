package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.Animatable
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
import kotlinx.coroutines.delay

/** A row's time in milliseconds and the text [textAt] a time reads as, so a change can count from the old time to the new. */
internal class ScoreTime(val ms: Long, val textAt: (Long) -> String)

// The time shown now, kept so a change that arrives mid-count counts on from where the text is.
private class ScoreMemory(var ms: Long)

/**
 * A row's score text. On a board with the LinkedIn account's motion a new time counts from the old one to the new on
 * the row's glide, after the row's stagger. The text changes only when the shown time does (about once a second of
 * the count), not on every frame; a score without a [time] (a hidden one) or on a board without motion is just [text].
 */
@Composable
internal fun BoardScoreText(text: String, time: ScoreTime?, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val motion = LocalPlaceMotion.current
    if (time == null || motion == null || !motion.animated) {
        Text(text, style = style, color = color, modifier = modifier)
        return
    }
    val memory = remember { ScoreMemory(time.ms) }
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        val from = memory.ms
        if (from != time.ms) {
            delay(motion.delayMs.toLong())
            Animatable(from.toFloat()).animateTo(time.ms.toFloat(), BoardMotion.count) {
                memory.ms = value.toLong()
                shown = time.textAt(memory.ms)
            }
        }
        memory.ms = time.ms
        shown = text
    }
    Text(shown, style = style, color = color, modifier = modifier)
}
