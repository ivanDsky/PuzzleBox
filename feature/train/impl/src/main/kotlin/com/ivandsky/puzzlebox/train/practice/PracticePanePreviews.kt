package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// The practice pane: each status of the band, the solved page and the empty messages. The board is a stand-in: this module
// sees no game's board, so a plain 6x6 grid with a few discs is drawn from the board colors.

/** The grid side of the stand-in board. */
private const val SIDE = 6

/** The stand-in technique's line, as the list shows it. */
internal const val StandInLine = "A region sits inside one line, so the rest of that line is out."

/** A square grid as large as the space allows, in the board colors with a few pieces: the board a stand-in game draws. */
@Composable
internal fun StandInGrid(modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    BoxWithConstraints(modifier) {
        Canvas(Modifier.size(minOf(maxWidth, maxHeight))) {
            drawRect(colors.boardSurface)
            val cell = size.width / SIDE
            for (i in 0..SIDE) {
                drawLine(colors.boardGridLine, Offset(i * cell, 0f), Offset(i * cell, size.height), 1.dp.toPx())
                drawLine(colors.boardGridLine, Offset(0f, i * cell), Offset(size.width, i * cell), 1.dp.toPx())
            }
            listOf(0, 9, 22, 31).forEach { index ->
                drawCircle(colors.boardGridLine, cell * 0.3f, Offset((index % SIDE + 0.5f) * cell, (index / SIDE + 0.5f) * cell))
            }
        }
    }
}

/** A Spotlight a stand-in game would give a move: a row, its target and a few excluded squares. */
internal fun standInSpotlight(): Spotlight {
    val row = setOf(14, 15, 16, 17)
    val roles = row.associateWith { SpotlightRole.EVIDENCE } +
        listOf(2, 8, 20, 26).associateWith { SpotlightRole.EXCLUDED } +
        (15 to SpotlightRole.TARGET)
    return Spotlight(SIDE, SIDE, roles, SpotlightCallout("Line lock: the last cell of this region is in row 3, so row 3 is out for the rest.", row), StandInGlyphs, playId = 0)
}

/** A disc for a piece, an x for an excluded square. */
private data object StandInGlyphs : SpotlightGlyphs {
    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.3f
        if (role == SpotlightRole.EXCLUDED) {
            drawLine(color, c - Offset(r / 2, r / 2), c + Offset(r / 2, r / 2), r / 4)
            drawLine(color, c + Offset(r / 2, -r / 2), c - Offset(r / 2, -r / 2), r / 4)
        } else {
            drawCircle(color, r, c)
        }
    }
}

/** A practice board that is only a status and a Show me that does nothing. */
internal class StandInPracticeBoard(status: PracticeStatus) : PracticeBoard {
    override val technique = TechniqueId("line-lock")
    override val status: StateFlow<PracticeStatus> = MutableStateFlow(status)

    override fun showMe() = Unit

    override fun gotIt() = Unit
}

@Composable
private fun PracticePreviewFrame(
    state: PracticeState,
    progress: TechniqueProgress = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 2, answeredAtMs = 1L),
) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        PracticePane(
            name = "Line lock",
            line = StandInLine,
            state = state,
            progress = progress,
            showBack = true,
            onClose = {},
            board = { _, modifier -> StandInGrid(modifier) },
            onNextBoard = {},
            onTryAgain = {},
            onTryAnother = {},
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

private fun practicing(status: PracticeStatus) = PracticeState.Practicing(StandInPracticeBoard(status))

@PuzzleBoxPreviews
@Composable
private fun PracticeFindNextMovePreview() = PracticePreviewFrame(practicing(PracticeStatus.FindNextMove))

@PuzzleBoxPreviews
@Composable
private fun PracticeKeepGoingPreview() = PracticePreviewFrame(practicing(PracticeStatus.KeepGoing(left = 2, trained = true)))

@PuzzleBoxPreviews
@Composable
private fun PracticeRightPreview() =
    PracticePreviewFrame(practicing(PracticeStatus.Right(standInSpotlight(), trained = true)), TechniqueProgress(TechniqueProgress.Mark.Practicing, 3, 1L))

@PuzzleBoxPreviews
@Composable
private fun PracticeEasierPreview() = PracticePreviewFrame(practicing(PracticeStatus.Easier("Naked single", standInSpotlight())))

@PuzzleBoxPreviews
@Composable
private fun PracticeWrongPreview() = PracticePreviewFrame(practicing(PracticeStatus.Wrong("Two queens would touch.")))

@PuzzleBoxPreviews
@Composable
private fun PracticeShownPreview() = PracticePreviewFrame(practicing(PracticeStatus.Shown(standInSpotlight())))

@PuzzleBoxPreviews
@Composable
private fun PracticeSolvedPreview() = PracticePreviewFrame(practicing(PracticeStatus.Solved))

@PuzzleBoxPreviews
@Composable
private fun PracticeMasteredPreview() =
    PracticePreviewFrame(practicing(PracticeStatus.FindNextMove), TechniqueProgress(TechniqueProgress.Mark.Mastered, 7, 1L))

@PuzzleBoxPreviews
@Composable
private fun PracticeEmptyPreview() = PracticePreviewFrame(PracticeState.Empty)

@PuzzleBoxPreviews
@Composable
private fun PracticeNeedsConnectionPreview() = PracticePreviewFrame(PracticeState.NeedsConnection)

@PuzzleBoxPreviews
@Composable
private fun PracticeCantLoadPreview() = PracticePreviewFrame(PracticeState.CantLoad(alternative = 918))
