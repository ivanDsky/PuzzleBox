package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.model.GameId

/** One Sudoku game's play registration: both games share this class and differ by [definition]. */
class SudokuPlayUi(private val definition: SudokuDefinition) : GamePlayUi {
    override val gameId: GameId get() = definition.id

    override fun createController(env: PlayEnvironment): GamePlayController = SudokuPlayController(definition, env)

    override val tutorial: List<TutorialStep> by lazy { sudokuTutorial(definition) }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        SudokuControlledBoard(controller as SudokuPlayController, modifier)
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
        val sudoku = controller as SudokuPlayController
        // One result hand-off per completion: keyed on completedAt, so a replay's win hands off again.
        LaunchedEffect(sudoku.session?.completedAt) {
            val completed = sudoku.session?.takeIf { it.isComplete } ?: return@LaunchedEffect
            onSolved(completed.elapsedMs, completed.hintsUsed)
        }
        Box(modifier.fillMaxSize()) {
            AdaptivePlayScaffold(
                layout = layout,
                modifier = Modifier.fillMaxSize(),
                board = { BoardArea(sudoku, status, onRetry) },
                side = { SidePanel(sudoku, status, onHowToPlay) },
            )
        }
    }

    @Composable
    private fun BoxScope.BoardArea(controller: SudokuPlayController, status: PlayBoardStatus, onRetry: () -> Unit) {
        Box(Modifier.fillMaxSize().testTag("sudoku.board_area"), contentAlignment = Alignment.Center) {
            if (status is PlayBoardStatus.Ready && controller.session != null) {
                SudokuControlledBoard(controller, Modifier.padding(Spacing.md))
            } else {
                PlayBoardFallback(status, onRetry, tagPrefix = "sudoku")
            }
        }
    }

    @Composable
    private fun SidePanel(controller: SudokuPlayController, status: PlayBoardStatus, onHowToPlay: (() -> Unit)?) {
        val ready = status as? PlayBoardStatus.Ready
        PlayChrome(
            title = definition.displayName,
            subtitle = ready?.let { controller.session?.ref?.puzzleNumber }?.let { "Daily · #$it" } ?: "Daily",
            clock = controller,
            hints = controller,
            undo = controller,
            banner = ready?.banner,
            feedback = controller,
            onHowToPlay = onHowToPlay,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
}

@Composable
private fun SudokuControlledBoard(controller: SudokuPlayController, modifier: Modifier) {
    val session = controller.session ?: return
    SudokuPlayBoard(
        board = session.board,
        selected = controller.selected,
        onSelect = controller::select,
        onDigit = controller::place,
        modifier = modifier,
    )
}

/** The skeleton board: an N×N grid of tappable cells above one row of digit buttons. */
@Composable
internal fun SudokuPlayBoard(
    board: SudokuBoard,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = board.geometry.size
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.widthIn(max = PlayBoardMaxWidth).testTag("sudoku.board"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().border(2.dp, colors.onSurface)) {
            for (row in 0 until size) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until size) {
                        val idx = row * size + col
                        val cell = board.cells[idx]
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (idx == selected) colors.primaryContainer else colors.surface)
                                .border(0.5.dp, colors.outlineVariant)
                                .clickable { onSelect(idx) }
                                .testTag("sudoku.cell.$idx"),
                            contentAlignment = Alignment.Center,
                        ) {
                            val digit = board.digitAt(idx)
                            if (digit != 0) {
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (cell is SudokuCell.Given) FontWeight.Bold else FontWeight.Normal,
                                    color = if (cell is SudokuCell.Given) colors.onSurface else colors.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (digit in 1..size) {
                OutlinedButton(
                    onClick = { onDigit(digit) },
                    modifier = Modifier.weight(1f).testTag("sudoku.digit.$digit"),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(digit.toString())
                }
            }
        }
    }
}
