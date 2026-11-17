package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions

/** The board the stub draws. */
internal const val STUB_BOARD_TAG = "stub.board"

/**
 * A stand-in game's givens: a [rows] by [cols] checker, drawn in the theme's colours. feature:play:impl never sees a
 * real game (ADR-0007), so the page's own tests give it this; every real game's givens, and the rule that none of
 * them carries a solution, are tested in the games' own modules and, on the whole page, in the app's
 * FriendHiddenBoardE2ETest. [asked] lists every puzzle the page handed to [givens].
 */
internal class StubGivensBoard(private val rows: Int = 8, private val cols: Int = 8) : GameReplayBoard {
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
