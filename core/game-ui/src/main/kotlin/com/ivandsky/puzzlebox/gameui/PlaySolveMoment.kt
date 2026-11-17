package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberSolveMomentState
import com.ivandsky.puzzlebox.designsystem.solveFrame
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * [PlaySolveFrame] under [LocalSolveRestOnBoard]: the solve moment B (M12-10). The instant [solved] turns non-null
 * during play, [rest] begins (the buzz, the tap layer) and the moment plays on the same clock: the sweep band, the
 * board's pop and the frame line lapping the board in [brand]'s accent, closed 200 ms before the hand-off. [onHandOff]
 * comes [CelebrationMotion.SolveMomentMs] after the solve, or at once on a tap, wherever the motion is. There is no
 * stamp. Reduced motion shows the closed frame for the same rest. A board already solved when it first draws hands
 * off at once; a fresh board in the same place (play again) drops the frame.
 */
@Composable
internal fun PlaySolveMoment(
    brand: GameBrand,
    solved: Instant?,
    rest: SolveRestState,
    onHandOff: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    board: @Composable () -> Unit,
) {
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
        SolveRestTapLayer(rest, Modifier.matchParentSize())
    }
}
