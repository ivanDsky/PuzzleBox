package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * What a technique intro's demo (spec §2.7) is cut from: the step that opens the technique's Focus board on a puzzle,
 * so the demo shows the very position practice then opens on.
 */
object QueensIntro {

    /**
     * The step that opens a Focus board of the technique, as [QueensPractice.start] finds it and Show me would make it.
     * [board] is the position before the step (what practice opens on), [landing] the squares the step settles in the
     * order they land: the open squares it crosses, lowest first, or its one queen. [after] is [board] with the step
     * made.
     */
    class Opening(val board: QueensBoard, val step: QueensStep.Deduction, val landing: List<Cell>, val after: QueensBoard)

    /** The opening of [technique] on [puzzle]; null where no Focus board of it exists (see [QueensPractice.start]). */
    fun opening(puzzle: QueensPuzzle, technique: TechniqueId): Opening? {
        val board = QueensPractice.start(puzzle, technique, BoardKind.Focus) ?: return null
        val step = QueensPractice.show(puzzle, board) ?: return null
        val landing = when (val result = step.result) {
            is QueensStep.Deduction.Result.Cross -> QueensPractice.crossings(puzzle, board, step)
            is QueensStep.Deduction.Result.LastCell -> listOf(result.cell)
        }
        return Opening(board, step, landing, QueensPractice.after(puzzle, board, step))
    }

    /**
     * Whether a Line lock [step] fills the line: a color's open squares all sit in the row or column the step names, and
     * the rest of that line is crossed. False when it fills the region: that line's open squares all sit in one color,
     * and the rest of the color, outside the line, is crossed. Told apart by where the crossed squares lie.
     */
    fun fillsLine(puzzle: QueensPuzzle, step: QueensStep.Deduction): Boolean {
        val crossed = (step.result as? QueensStep.Deduction.Result.Cross)?.cells ?: return false
        val kind = step.unit.kind
        return crossed.all { cell ->
            when (kind) {
                HintReason.Kind.ROW -> cell.idx / puzzle.size
                HintReason.Kind.COLUMN -> cell.idx % puzzle.size
                HintReason.Kind.REGION -> return false
            } == step.unit.unit
        }
    }
}
