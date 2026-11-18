package com.ivandsky.puzzlebox.play.random

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId

private val Games = listOf(
    RandomGame(GameId("zip"), "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip)),
    RandomGame(GameId("tango"), "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango)),
    RandomGame(GameId("queens"), "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens)),
    RandomGame(GameId("sudoku"), "Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku)),
)

private val Actions = RandomPuzzleActions(onGame = {}, onUnsolved = {}, onPick = {})

private fun state(phase: RandomPhase) =
    RandomPuzzleState(RandomSource.Past, game = null, unsolvedOnly = true, difficulty = null, phase = phase)

@Composable
private fun Body(phase: RandomPhase, layout: PlayLayout = PlayLayout.SinglePane, modifier: Modifier = Modifier) {
    PuzzleBoxTheme {
        Surface {
            RandomPuzzleContent(state(phase), Games, layout, Actions, modifier)
        }
    }
}

/** Phone, nothing picked yet: Any is on, "Not solved yet" is on. */
@PuzzleBoxPreviews
@Composable
internal fun RandomPuzzlePhoneIdlePreview() = Body(RandomPhase.Idle)

/** Phone with a pick running: chips and switch off, the button is a spinner. */
@PuzzleBoxPreviews
@Composable
internal fun RandomPuzzlePhonePickingPreview() = Body(RandomPhase.Picking)

/** Phone after an outcome that opened nothing: the status line under the button. */
@PuzzleBoxPreviews
@Composable
internal fun RandomPuzzlePhoneStatusPreview() = Body(RandomPhase.Said(RandomMessage.NothingLeftPast))

/** Tablet at the sheet's width: chips left, "Not solved yet", Pick and the status right. */
@PuzzleBoxPreviews
@Composable
internal fun RandomPuzzleTabletPreview() = Body(RandomPhase.Said(RandomMessage.Offline), PlayLayout.TwoPane, Modifier.width(640.dp))
