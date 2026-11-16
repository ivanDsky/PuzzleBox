package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Stamp
import kotlinx.coroutines.delay

/**
 * The solve celebration drawn over Result (L2): [brand]'s glyph Stamps in over a scrim while
 * Result loads beneath. It lasts [MotionTokens.CelebrationMillis], or
 * [MotionTokens.CelebrationMomentMillis] when [moment] is true by the end of the first
 * length (a moment signalled late still gets the full length, never a restart). One tap
 * anywhere ends it at once; there is no setting. [onDone] fires exactly once, from the
 * timer or the tap, whichever comes first. Reduced motion shows the stamp without motion
 * and keeps the same lengths and the tap.
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
    var ended by remember { mutableStateOf(false) }
    var stamped by remember { mutableStateOf(false) }
    fun end() {
        if (ended) return
        ended = true
        done()
    }
    val endNow by rememberUpdatedState(::end)
    LaunchedEffect(Unit) {
        stamped = true
        delay(MotionTokens.CelebrationMillis)
        if (momentNow) delay(MotionTokens.CelebrationMomentMillis - MotionTokens.CelebrationMillis)
        endNow()
    }
    Box(
        modifier
            .fillMaxSize()
            .testTag("solve.celebration")
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = MotionTokens.NavScrimAlpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::end),
        contentAlignment = Alignment.Center,
    ) {
        Stamp(visible = stamped, modifier = Modifier.testTag("solve.celebration_stamp")) {
            GameGlyph(brand, GameGlyphSize.Hero)
        }
    }
}

// Preview: the overlay over a stand-in Result. The stamp animates in on the first frame
// and the timer only runs live, so the pane stays static (A8).
@PuzzleBoxPreviews
@Composable
internal fun SolveCelebrationPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(240.dp, 320.dp).background(MaterialTheme.colorScheme.surface)) {
            SolveCelebration(brand = GameBrand.Neutral, moment = false, onDone = {})
        }
    }
}
