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
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

private val Games = listOf(
    RandomGame(GameId("zip"), "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip)),
    RandomGame(GameId("tango"), "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango)),
    RandomGame(GameId("queens"), "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens)),
    RandomGame(GameId("sudoku"), "Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku)),
)

private val Actions = RandomPuzzleActions(onSource = {}, onGame = {}, onUnsolved = {}, onDifficulty = {}, onPick = {})

private fun state(phase: RandomPhase, source: RandomSource, difficulty: DifficultyChoice?) =
    RandomPuzzleState(source, game = null, unsolvedOnly = true, difficulty = difficulty, phase = phase)

@Composable
private fun Body(
    phase: RandomPhase,
    layout: PlayLayout = PlayLayout.SinglePane,
    modifier: Modifier = Modifier,
    source: RandomSource = RandomSource.Past,
    difficulty: DifficultyChoice? = null,
) {
    PuzzleBoxTheme {
        Surface {
            RandomPuzzleContent(state(phase, source, difficulty), if (source == RandomSource.Past) Games else Games.take(3), layout, Actions, modifier)
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

/** Phone on New today with Medium chosen: the community games and the Difficulty chips in place of "Not solved yet". */
@PuzzleBoxPreviews
@Composable
internal fun RandomPuzzlePhoneNewTodayPreview() = Body(RandomPhase.Idle, source = RandomSource.Community, difficulty = DifficultyChoice.Medium)

/** Tablet on New today: toggle and chips left, Difficulty and Pick right. */
@PuzzleBoxPreviews
@Composable
internal fun RandomPuzzleTabletNewTodayPreview() =
    Body(RandomPhase.Said(RandomMessage.NothingLeftCommunity), PlayLayout.TwoPane, Modifier.width(640.dp), RandomSource.Community)
