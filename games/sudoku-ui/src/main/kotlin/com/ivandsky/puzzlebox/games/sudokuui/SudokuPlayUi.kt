package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.model.GameId

/**
 * One Sudoku game's play registration: both games share this class and differ by [definition].
 * [brand] is set at the registration line: both share the Sudoku glyph, the debug 9x9 stays
 * hueless while Mini reads teal.
 */
class SudokuPlayUi(
    private val definition: SudokuDefinition,
    private val highlights: SudokuHighlightSettings,
    override val brand: GameBrand = GameBrand.Neutral,
    private val hintMode: HintModeSource = HintModeSource { HintMode.Legacy },
) : GamePlayUi {
    override val gameId: GameId get() = definition.id

    override fun createController(env: PlayEnvironment): GamePlayController =
        SudokuPlayController(definition, env, highlights, hintMode = hintMode.current())

    override val tutorial: List<TutorialStep> by lazy { sudokuTutorial(definition) }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        val state = (controller as SudokuPlayController).board ?: return
        SudokuMotionHost(state) { SudokuStackedBoard(state, modifier) }
    }

    /** Two toggles, both on by default; they change the board's tints only, never hints or conflicts. */
    override fun overflowFor(controller: GamePlayController): List<PlayOverflowItem> {
        val sudoku = controller as SudokuPlayController
        if (sudoku.board == null) return emptyList()
        return listOf(
            PlayOverflowItem("Highlight row, column & box", checked = sudoku.peerHighlight) {
                sudoku.choosePeerHighlight(!sudoku.peerHighlight)
            },
            PlayOverflowItem("Highlight same numbers", checked = sudoku.sameDigitHighlight) {
                sudoku.chooseSameDigitHighlight(!sudoku.sameDigitHighlight)
            },
        )
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) {
        SudokuPlayScreen(
            controller = controller as SudokuPlayController,
            status = status,
            layout = layout,
            sidePanel = sidePanel,
            onRetry = onRetry,
            onSolved = onSolved,
            modifier = modifier,
        )
    }
}
