package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import kotlinx.coroutines.delay

/** What the Hint button shows: [hint], as the dots fill and the button lights, and how many times it has swelled. */
@Stable
internal class HintFace(initial: HintUi) {
    var shown by mutableStateOf(initial)
        internal set
    var swells by mutableIntStateOf(0)
        internal set
}

/**
 * [hint] on its way to the screen. A hint that is earned is first the third dot filling, and
 * only [MotionTokens.DotFillDuration] plus [StrandsMotion.LightDelayMs] later the lit button
 * (dots cleared, badge up), which then swells once. Every other change shows at once, and
 * [reduceMotion] shows the earned hint at once too.
 */
@Composable
internal fun rememberHintFace(hint: HintUi, reduceMotion: Boolean): HintFace {
    val face = remember { HintFace(hint) }
    LaunchedEffect(hint, reduceMotion) {
        val earned = hint.count > face.shown.count && hint.meterThirds == 0
        if (earned && !reduceMotion) {
            face.shown = HintUi(face.shown.count, HINT_DOTS)
            delay((MotionTokens.DotFillDuration + StrandsMotion.LightDelayMs).toLong())
            face.swells++
        }
        face.shown = hint
    }
    return face
}
