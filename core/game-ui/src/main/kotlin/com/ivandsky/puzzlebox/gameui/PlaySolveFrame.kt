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
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberSolveMomentState
import com.ivandsky.puzzlebox.designsystem.solveFrame
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * The solve moment around a game's [board] (A6, identity §2.6; the solve moment B, M12-10). The instant [solved] turns
 * non-null during play, the solve rest begins (the buzz, the tap layer) and the moment plays on the same clock: the sweep
 * band, the board's pop and a frame line lapping the board in [brand]'s accent, closed 1300 ms after the solve and held
 * 400 ms before the hand-off. As the board pops, [CelebrationMotion.BoardPopStartMs] after the solve, [brand]'s glyph
 * stamps onto the centre of the board and stays until the hand-off; it is decorative and takes no tap. [onHandOff]
 * carries the player to Result [CelebrationMotion.SolveMomentMs] after the solve, or at once on a tap on the board,
 * wherever the motion is. Reduced motion shows the closed frame for the same rest, with no stamp. [solved] is the
 * session's completion instant, so the moment plays once per solve. A board already solved when it first draws (a restored play) hands off at once; a fresh board in the same place (play again) drops the frame.
 *
 * The pop and the line's comet reach a little past the board, so it needs 6 dp clear around it; [PlayBoardPane] gives more.
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
    val rest = rememberSolveRest(enabled = true)
    val moment = rememberSolveMomentState()
    val handOff by rememberUpdatedState(onHandOff)
    // Seen unsolved in this composition: only a solve the player just made gets the moment.
    var seenOpen by remember { mutableStateOf(false) }
    LaunchedEffect(solved) {
        if (solved == null) {
            seenOpen = true
            moment.reset()
            return@LaunchedEffect
        }
        if (seenOpen) {
            val resting = rest.begin(this)
            // Not awaited: a tap hands off mid-motion, and the frame finishes closing behind it.
            launch { moment.play() }
            resting.await()
        }
        handOff()
    }
    Box(modifier.solveFrame(moment, brand.accent.colors().accent, shape), contentAlignment = Alignment.Center) {
        board()
        SolveStamp(brand, rest)
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
