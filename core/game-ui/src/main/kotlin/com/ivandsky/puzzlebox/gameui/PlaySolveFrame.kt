package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.rememberSolveSweepState
import com.ivandsky.puzzlebox.designsystem.solveSweep
import java.time.Instant
import kotlinx.coroutines.delay

/** How long the solved board rests before the hand-off: the stamp's spring settles, or
 *  under reduced motion the outline is seen. Only without [LocalSolveRestOnBoard]. */
private const val SOLVED_HOLD_MS = 360L

/**
 * The solve moment around a game's [board] (A6, identity §2.6). When [solved] turns
 * non-null during play, the board plays SolveSweep in [brand]'s accent, then the brand
 * glyph Stamps in over it, and only then does [onHandOff] carry the player to Result.
 * [solved] is the session's completion instant, so the moment plays
 * once per solve. A board that is already solved when it first draws (a restored play)
 * hands off at once, as before. Reduced motion shows only the accent outline, held
 * briefly, with no stamp.
 *
 * Under [LocalSolveRestOnBoard] there is no stamp: the sweep plays and the solved board rests so the moment lasts
 * [CelebrationMotion.SolveMomentMs] from the solve to the hand-off (reduced motion: the outline for the same rest),
 * and a tap on the board during the rest hands off at once.
 */
@Composable
fun PlaySolveFrame(
    brand: GameBrand,
    solved: Instant?,
    onHandOff: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    board: @Composable () -> Unit,
) {
    val sweep = rememberSolveSweepState()
    val reducedMotion = rememberReducedMotion()
    val handOff by rememberUpdatedState(onHandOff)
    val rest = rememberSolveRest()
    // Seen unsolved in this composition: only a solve the player just made gets the moment.
    var seenOpen by remember { mutableStateOf(false) }
    var stamped by remember { mutableStateOf(false) }
    LaunchedEffect(solved) {
        if (solved == null) {
            seenOpen = true
            stamped = false
            return@LaunchedEffect
        }
        if (seenOpen) {
            val resting = rest.begin(this)
            sweep.play()
            if (rest.enabled) {
                resting.await()
            } else {
                if (!reducedMotion) delay(MotionTokens.SweepDuration.toLong())
                stamped = !reducedMotion
                delay(SOLVED_HOLD_MS)
            }
        }
        handOff()
    }
    Box(modifier.solveSweep(sweep, brand.accent.colors().accent, shape), contentAlignment = Alignment.Center) {
        board()
        Stamp(visible = stamped, modifier = Modifier.testTag("play.solve_stamp")) {
            GameGlyph(brand, GameGlyphSize.Hero)
        }
        SolveRestTapLayer(rest, Modifier.matchParentSize())
    }
}

// Preview: a board mid-play. The frame adds nothing until a solve, and the solve moment
// only plays on a live solve, so the pane stays static (A8).
@PuzzleBoxPreviews
@Composable
internal fun PlaySolveFramePreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            PlaySolveFrame(brand = GameBrand.Neutral, solved = null, onHandOff = {}) {
                Box(Modifier.size(160.dp).background(MaterialTheme.colorScheme.surface))
            }
        }
    }
}
