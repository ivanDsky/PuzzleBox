package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsBrand
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import com.ivandsky.puzzlebox.games.strandsui.board.keyframes

/**
 * The Strands hint control (SC4): a chunky "Hint" game button with three progress dots, one
 * per non-theme word toward the next hint, and a count badge once a hint is banked. A banked
 * hint fills the face with the Strands accent; with none it is the neutral well, but stays
 * pressable, as on NYT: the press opens the A7 "Need a hint?" modal and spends nothing. A new
 * non-theme word fills a dot and rises a "+1" beside the button ([HintBonus]); the third lights
 * the button and swells it ([rememberHintFace]). TalkBack hears "Hint" and the state: how many
 * hints are banked and how many words toward the next.
 */
@Composable
internal fun StrandsHintControl(
    hint: HintUi,
    onHint: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val face = rememberHintFace(hint, reduceMotion)
    val shown = face.shown
    val swell = remember { Animatable(1f) }
    LaunchedEffect(face.swells) {
        if (face.swells == 0) return@LaunchedEffect
        swell.snapTo(0f)
        swell.animateTo(1f, tween(StrandsMotion.SwellMs, easing = LinearEasing))
    }
    val state = if (hint.available) StrandsCopy.hintAvailable(hint.count, hint.meterThirds) else StrandsCopy.hintUnavailable(hint.meterThirds)
    Box(modifier.width(HintWidth)) {
        Box(
            Modifier.graphicsLayer {
                val scale = keyframes(swell.value, SwellAt, StrandsMotion.SwellStops, LinearEasing)
                scaleX = scale
                scaleY = scale
            },
        ) {
            GameButton(
                onClick = onHint,
                label = StrandsCopy.HINT,
                accent = StrandsBrand.accent.takeIf { shown.available },
                size = GameButtonSize.Tall,
                modifier = Modifier
                    .width(HintWidth)
                    .semantics { stateDescription = state }
                    .testTag("strands.hint"),
            ) { ink ->
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
                        Text(StrandsCopy.HINT, style = MaterialTheme.typography.labelLarge.merge(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = ink, maxLines = 1)
                    }
                    HintDots(filled = shown.meterThirds, lit = shown.available, litInk = ink, reduceMotion = reduceMotion)
                }
            }
            if (shown.count > 0) {
                HintBadge(shown.count, reduceMotion, Modifier.align(Alignment.TopEnd).offset(x = BadgeOutX, y = BadgeUp))
            }
        }
        HintBonus(hint, reduceMotion, Modifier.align(Alignment.CenterStart))
    }
}

/** The swell's keyframes sit evenly through it. */
private val SwellAt = FloatArray(StrandsMotion.SwellStops.size) { it.toFloat() / StrandsMotion.SwellStops.lastIndex }

/** Wide enough for the icon and "Hint" over three dots. */
private val HintWidth = 132.dp

/** The badge hangs off the button's top right corner. */
private val BadgeOutX = 7.dp
private val BadgeUp = (-9).dp

/** The Tall game button's 64dp face on its 4dp lip, which the row keeps on the solve. */
internal val HintHeight = 68.dp
