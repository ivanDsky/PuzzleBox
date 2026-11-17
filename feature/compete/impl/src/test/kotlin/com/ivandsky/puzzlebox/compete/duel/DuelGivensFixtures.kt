package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.model.GameId

/** The board the stub draws. */
internal const val STUB_BOARD_TAG = "stub.board"

/**
 * A stand-in game's givens, a [rows] by [cols] checker in the theme's colours: compete-impl never sees a real game
 * (ADR-0007). [asked] lists every puzzle the page handed to [givens].
 */
internal class StubGivens(private val rows: Int = 8, private val cols: Int = 8) : GameReplayBoard {
    override val pieces = "pieces"

    val asked = mutableListOf<Puzzle>()

    override fun givens(puzzle: Puzzle): GivensBoard {
        asked += puzzle
        return GivensBoard(rows, cols) { modifier -> Checker(rows, cols, modifier) }
    }

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) = Unit
}

@Composable
private fun Checker(rows: Int, cols: Int, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.primary
    val light = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.fillMaxSize().testTag(STUB_BOARD_TAG)) {
        val cell = Size(size.width / cols, size.height / rows)
        for (r in 0 until rows) for (c in 0 until cols) {
            drawRect(if ((r + c) % 2 == 0) dark else light, Offset(c * cell.width, r * cell.height), cell)
        }
    }
}

/** A game's play UI that is only its brand and, when [replayBoard] is given, its givens. */
internal class BoardedPlay(
    override val gameId: GameId,
    accent: AccentSlot,
    override val replayBoard: GameReplayBoard?,
) : GamePlayUi {
    override val brand: GameBrand = GameBrand.Neutral.copy(accent = accent)
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = throw UnsupportedOperationException("brand only")

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}
