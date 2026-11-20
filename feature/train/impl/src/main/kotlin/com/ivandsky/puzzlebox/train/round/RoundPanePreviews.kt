package com.ivandsky.puzzlebox.train.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.train.domain.RecordAnswer

// The round: a snippet before an answer, after a Right with the Spotlight over the board, the summary and the empty
// message. The board and the Spotlight are stand-ins: this module sees no game's board, so a plain 6x6 grid with a few
// discs is drawn from the board colors, and the Spotlight lights a row, its target and a few excluded squares.

/** A snippet that is only its technique and its prompt. */
internal data class StandInSnippet(
    override val technique: TechniqueId = TechniqueId("line-lock"),
    override val prompt: String = "Place a queen that Line lock proves",
) : Snippet

/** The grid side of the stand-in board. */
private const val SIDE = 6

/** A grid in the board colors with a few pieces, and the Spotlight a stand-in game would give a Right answer. */
internal val standInBoard = RoundBoard(
    board = { _, modifier -> StandInGrid(modifier) },
    spotlight = { standInSpotlight() },
)

private fun standInSpotlight(): Spotlight {
    val row = setOf(14, 15, 16, 17)
    val roles = row.associateWith { SpotlightRole.EVIDENCE } +
        listOf(2, 8, 20, 26).associateWith { SpotlightRole.EXCLUDED } +
        (15 to SpotlightRole.TARGET)
    return Spotlight(SIDE, SIDE, roles, SpotlightCallout("Line lock: the last cell of this region is in row 3, so row 3 is out for the rest.", row), StandInGlyphs, playId = 0)
}

@Composable
private fun StandInGrid(modifier: Modifier) {
    val colors = LocalBoardColors.current
    Canvas(modifier) {
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

internal fun standInQuestion(answer: RecordAnswer.Answer?, right: Int = 2) =
    RoundState.Question(listOf(StandInSnippet(), StandInSnippet(), StandInSnippet(), StandInSnippet(), StandInSnippet()), 2, answer, right)

@Composable
private fun RoundPreviewFrame(state: RoundState) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        RoundPane(
            "Line lock", state, showBack = true, onClose = {}, board = standInBoard, onShowMe = {}, onNext = {}, onTryAgain = {}, onTryAnother = {},
            onDrill = {},
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun RoundBeforeAnswerPreview() = RoundPreviewFrame(standInQuestion(answer = null))

@PuzzleBoxPreviews
@Composable
private fun RoundRightPreview() = RoundPreviewFrame(standInQuestion(RecordAnswer.Answer.Verdict(TrainerVerdict.Right), right = 3))

@PuzzleBoxPreviews
@Composable
private fun RoundSummaryPreview() = RoundPreviewFrame(RoundState.Summary(right = 4, total = 5))

@PuzzleBoxPreviews
@Composable
private fun RoundEmptyPreview() = RoundPreviewFrame(RoundState.Empty)

@PuzzleBoxPreviews
@Composable
private fun RoundNeedsConnectionPreview() = RoundPreviewFrame(RoundState.NeedsConnection)

@PuzzleBoxPreviews
@Composable
private fun RoundCantLoadPreview() = RoundPreviewFrame(RoundState.CantLoad(alternative = 918))
