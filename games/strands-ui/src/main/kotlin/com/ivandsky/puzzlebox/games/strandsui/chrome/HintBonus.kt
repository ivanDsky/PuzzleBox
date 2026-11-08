package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsBrand
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import com.ivandsky.puzzlebox.games.strandsui.board.keyframes
import kotlinx.coroutines.delay

private val PillHeight = 26.dp
private val PillRing = 2.dp
private val PillGap = 10.dp

/**
 * "+1": each non-theme word rises beside the Hint button, [MotionTokens.BonusRise] over
 * [MotionTokens.BonusRiseDuration], and fades. It takes no room: it hangs [PillGap] left of
 * the Hint button's edge, in the hint row and never over the board. Spending a hint lowers
 * [HintUi.count] only, which is not a word. [reduceMotion] holds it still
 * for [MotionTokens.BonusReducedHold] instead.
 */
@Composable
internal fun HintBonus(hint: HintUi, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val words = hint.count * HINT_DOTS + hint.meterThirds
    val last = remember { Seen(words) }
    val rise = remember { Animatable(1f) }
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(words, reduceMotion) {
        val gained = words > last.words
        last.words = words
        if (!gained) return@LaunchedEffect
        showing = true
        if (reduceMotion) {
            delay(MotionTokens.BonusReducedHold.toLong())
        } else {
            rise.snapTo(0f)
            rise.animateTo(1f, tween(MotionTokens.BonusRiseDuration, easing = LinearEasing))
        }
        showing = false
    }
    if (!showing) return
    val accent = StrandsBrand.accent.colors()
    val lift = with(LocalDensity.current) { MotionTokens.BonusRise.toPx() }
    Box(
        modifier
            .testTag("strands.hint_bonus")
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val gap = PillGap.roundToPx()
                layout(0, placeable.height) { placeable.place(-placeable.width - gap, 0) }
            }
            .graphicsLayer {
                if (reduceMotion) return@graphicsLayer
                val progress = rise.value
                alpha = keyframes(progress, StrandsMotion.BonusAt, StrandsMotion.BonusAlpha, LinearEasing)
                val scale = keyframes(progress, StrandsMotion.BonusAt, StrandsMotion.BonusScale, LinearEasing)
                scaleX = scale
                scaleY = scale
                translationY = -keyframes(progress, StrandsMotion.BonusAt, StrandsMotion.BonusLift, LinearEasing) * lift
            }
            .defaultMinSize(minHeight = PillHeight)
            .background(accent.container, CircleShape)
            .border(PillRing, accent.accent, CircleShape)
            .padding(horizontal = 10.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = StrandsCopy.HINT_BONUS,
            style = MaterialTheme.typography.labelLarge.merge(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = { accent.onContainer },
        )
    }
}

/** The word total last seen, so a recomposition that changes nothing is not a gain. Not state: nothing reads it. */
private class Seen(var words: Int)
