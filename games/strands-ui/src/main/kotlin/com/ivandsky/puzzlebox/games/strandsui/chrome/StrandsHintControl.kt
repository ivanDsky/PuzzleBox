package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.cooldownRing
import com.ivandsky.puzzlebox.gameui.rememberHintCooldown
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsBrand
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import com.ivandsky.puzzlebox.games.strandsui.board.keyframes

/**
 * The Strands hint control (SC4): an outlined "Hint" pill with three progress dots, one
 * per non-theme word toward the next hint, and a count badge once a hint is banked. A banked
 * hint fills the pill with the tint and the thick selection border; with none it is the resting outline, but stays
 * pressable, as on NYT: the press opens the A7 "Need a hint?" modal and spends nothing. A new
 * non-theme word fills a dot and rises a "+1" beside the button ([HintBonus]); the third lights
 * the button and swells it ([rememberHintFace]). TalkBack hears "Hint" and the state: how many
 * hints are banked and how many words toward the next. Under the core hint cooldown (F2) every
 * press, modal or reveal, waits it out with the shared ring ([rememberHintCooldown]).
 * Strands' Hint is its only action button, not the shared Undo-peer one, so it keeps its own earned look. [fill] takes the width the parent gives instead of [HintWidth] (the tuned control row).
 */
@Composable
internal fun StrandsHintControl(
    hint: HintUi,
    onHint: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
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
    val cooldown = rememberHintCooldown()
    val width = if (fill) Modifier.fillMaxWidth() else Modifier.width(HintWidth)
    Box(modifier.then(width)) {
        Box(
            Modifier.graphicsLayer {
                val scale = keyframes(swell.value, SwellAt, StrandsMotion.SwellStops, LinearEasing)
                scaleX = scale
                scaleY = scale
            },
        ) {
            val buttonModifier = Modifier
                .then(width)
                .semantics { stateDescription = state }
                .testTag("strands.hint")
                .cooldownRing(cooldown.ring, StrandsBrand.accent)
            StrandsHintPill(
                armed = shown.available,
                enabled = cooldown.gate(enabled = true),
                onClick = { cooldown.press(onHint) },
                modifier = buttonModifier,
            ) { ink ->
                HintFaceContent(shown, ink, reduceMotion, dotOn = LocalBoardColors.current.strandsFlatSelection)
            }
            if (shown.count > 0) {
                HintBadge(shown.count, reduceMotion, Modifier.align(Alignment.TopEnd).offset(x = BadgeOutX, y = BadgeUp))
            }
        }
        HintBonus(hint, reduceMotion, Modifier.align(Alignment.CenterStart))
    }
}

/** The icon and "Hint" over the progress dots, in [ink]; [dotOn] is the pill's solid-dot colour. */
@Composable
private fun HintFaceContent(shown: HintUi, ink: Color, reduceMotion: Boolean, dotOn: Color) {
    Column(
        Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
            Text(StrandsCopy.HINT, style = MaterialTheme.typography.labelLarge.merge(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = ink, maxLines = 1)
        }
        HintDots(filled = shown.meterThirds, lit = shown.available, litInk = ink, reduceMotion = reduceMotion, dotOn = dotOn)
    }
}

/** The swell's keyframes sit evenly through it. */
private val SwellAt = FloatArray(StrandsMotion.SwellStops.size) { it.toFloat() / StrandsMotion.SwellStops.lastIndex }

/** Wide enough for the icon and "Hint" over three dots. */
private val HintWidth = 132.dp

/** The badge hangs off the button's top right corner. */
private val BadgeOutX = 7.dp
private val BadgeUp = (-9).dp

/** The hint row's height, which the row keeps on the solve: the pill's 64dp. */
internal val HintHeight: Dp = 64.dp
