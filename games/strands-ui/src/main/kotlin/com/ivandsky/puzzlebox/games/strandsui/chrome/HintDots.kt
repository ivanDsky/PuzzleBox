package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.games.strandsui.StrandsBrand

internal const val HINT_DOTS = 3

private val DotSize = 12.dp
private val DotBorder = 2.dp
private val DotGap = 8.dp

/**
 * The hint's progress as [HINT_DOTS] dots, [filled] of them solid: one per non-theme word
 * toward the next hint. Off the accent face a dot's ring is the outline and its fill the
 * accent's own ink; on it ([lit]) both are [litInk]. A dot's colour crosses over
 * [MotionTokens.DotFillDuration], or lands at once under [reduceMotion].
 */
@Composable
internal fun HintDots(filled: Int, lit: Boolean, litInk: Color, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val ring = if (lit) litInk else MaterialTheme.colorScheme.outline
    val solid = if (lit) litInk else StrandsBrand.accent.colors().onContainer
    Row(modifier.testTag("strands.hint_dots"), horizontalArrangement = Arrangement.spacedBy(DotGap)) {
        repeat(HINT_DOTS) { index ->
            val on = index < filled
            val ringColor by animateColorAsState(ring, colorSpec(reduceMotion), label = "hintDotRing")
            val fillColor by animateColorAsState(if (on) solid else solid.copy(alpha = 0f), colorSpec(reduceMotion), label = "hintDotFill")
            Box(
                Modifier
                    .size(DotSize)
                    .testTag(if (on) "strands.hint_dot.on" else "strands.hint_dot.off")
                    .background(fillColor, CircleShape)
                    .border(DotBorder, ringColor, CircleShape),
            )
        }
    }
}

private fun colorSpec(reduceMotion: Boolean) =
    if (reduceMotion) snap<Color>() else tween<Color>(MotionTokens.DotFillDuration)
