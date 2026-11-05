package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.model.GameId

/** One Sudoku game's play registration: both games share this class and differ by [definition]. */
class SudokuPlayUi(private val definition: SudokuDefinition) : GamePlayUi {
    override val gameId: GameId get() = definition.id

    override fun createController(env: PlayEnvironment): GamePlayController = SudokuPlayController(definition, env)

    override val tutorial: List<TutorialStep> by lazy { sudokuTutorial(definition) }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        val state = (controller as SudokuPlayController).board ?: return
        SudokuMotionHost(state) { SudokuStackedBoard(state, modifier) }
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        onHowToPlay: (() -> Unit)?,
        modifier: Modifier,
    ) {
        SudokuPlayScreen(
            controller = controller as SudokuPlayController,
            title = definition.displayName,
            status = status,
            layout = layout,
            onRetry = onRetry,
            onSolved = onSolved,
            onHowToPlay = onHowToPlay,
            modifier = modifier,
        )
    }
}
