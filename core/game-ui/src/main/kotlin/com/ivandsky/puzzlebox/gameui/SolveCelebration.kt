package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The celebration stamp's constants: when it lands, where it lands from, and its size. */
internal object SolveCelebrationDefaults {
    /**
     * The stamp lands this long after the overlay's first frame: the overlay rides in on Result's own entrance,
     * so a stamp landing at once would spring while the page is still travelling.
     */
    const val LandDelayMs = 280L

    /** The design identity's stamp scale, which the small board stamp does not use (MotionTokens.StampFromScale). */
    const val StampFromScale = 0.6f

    val StampSize: Dp = 112.dp
    val StampGlyph: Dp = 64.dp
    val StampCorner: Dp = 32.dp
}

/**
 * The solve celebration drawn over Result (L2), which is complete beneath it: once Result is in, [brand]'s glyph
 * lands on the stamp spring over a scrim with one success buzz and a burst of confetti in the game's colours behind
 * it. It lasts [MotionTokens.CelebrationMillis], or [MotionTokens.CelebrationMomentMillis] when [moment] is true by
 * the end of the first length (a moment signalled late still gets the full length, never a restart), and the longer
 * one gets a second burst as the first length ends. One tap anywhere ends it at once; there is no setting. [onDone]
 * fires exactly once, from the timer or the tap, whichever comes first. Reduced motion shows the stamp at rest from
 * the first frame with the buzz and no confetti, and keeps the same lengths and the tap.
 */
@Composable
fun SolveCelebration(
    brand: GameBrand,
    moment: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done by rememberUpdatedState(onDone)
    val momentNow by rememberUpdatedState(moment)
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    var ended by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf(reduced) }
    var bursts by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(if (reduced) 1f else SolveCelebrationDefaults.StampFromScale) }
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    fun end() {
        if (ended) return
        ended = true
        done()
    }
    val endNow by rememberUpdatedState(::end)
    LaunchedEffect(Unit) {
        val wait = if (reduced) 0L else SolveCelebrationDefaults.LandDelayMs
        delay(wait)
        landed = true
        view.performSolveHaptic()
        if (!reduced) {
            bursts = 1
            launch {
                scale.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
            }
            launch { alpha.animateTo(1f, tween(MotionTokens.StampFadeDuration)) }
        }
        delay(MotionTokens.CelebrationMillis - wait)
        if (momentNow) {
            if (!reduced) bursts = 2
            delay(MotionTokens.CelebrationMomentMillis - MotionTokens.CelebrationMillis)
        }
        endNow()
    }
    val colors = solveCelebrationBurstColors(brand)
    SolveCelebrationFrame(
        brand = brand,
        landed = landed,
        stampScale = { scale.value },
        stampAlpha = { alpha.value },
        modifier = modifier
            .testTag("solve.celebration")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::end),
    ) {
        repeat(bursts) { index ->
            key(index) { SolveCelebrationBurst(index, colors, Modifier.matchParentSize()) }
        }
    }
}

/**
 * One frame of the celebration: the scrim, then [bursts], then, once [landed], the stamp at [stampScale] and
 * [stampAlpha]. Stateless, so a golden can pin any moment of it.
 */
@Composable
internal fun SolveCelebrationFrame(
    brand: GameBrand,
    landed: Boolean,
    stampScale: () -> Float,
    stampAlpha: () -> Float,
    modifier: Modifier = Modifier,
    bursts: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = MotionTokens.NavScrimAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        bursts()
        if (landed) {
            val accent = brand.accent.colors()
            Box(
                Modifier
                    .graphicsLayer {
                        val now = stampScale()
                        scaleX = now
                        scaleY = now
                        this.alpha = stampAlpha()
                    }
                    .testTag("solve.celebration_stamp")
                    .size(SolveCelebrationDefaults.StampSize)
                    .background(accent.accent, RoundedCornerShape(SolveCelebrationDefaults.StampCorner)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = brand.glyph,
                    contentDescription = null,
                    modifier = Modifier.size(SolveCelebrationDefaults.StampGlyph),
                    tint = accent.onAccent,
                )
            }
        }
    }
}

// Preview: the overlay over a stand-in Result. Nothing has landed on the first frame and the timer only runs
// live, so the pane shows the scrim alone (A8); SolveCelebrationFramePreview shows the stamp and the burst.
@PuzzleBoxPreviews
@Composable
internal fun SolveCelebrationPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(240.dp, 320.dp).background(MaterialTheme.colorScheme.surface)) {
            SolveCelebration(brand = GameBrand.Neutral, moment = false, onDone = {})
        }
    }
}

// Preview: the stamp at rest with the burst half-way out. Fixed values, so the pane is static (A8).
@PuzzleBoxPreviews
@Composable
internal fun SolveCelebrationFramePreview() {
    PuzzleBoxTheme {
        val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)
        Box(Modifier.size(360.dp, 480.dp).background(MaterialTheme.colorScheme.surface)) {
            SolveCelebrationFrame(brand = brand, landed = true, stampScale = { 1f }, stampAlpha = { 1f }) {
                SolveCelebrationBurstFrame(progress = { 0.5f }, colors = solveCelebrationBurstColors(brand), seed = 0L)
            }
        }
    }
}
