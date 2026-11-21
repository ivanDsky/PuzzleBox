package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuIntro
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo

/**
 * A recorded Mini Sudoku demo (spec §2.7): one real step of a technique variant ([SudokuIntro.Recording]) cut into
 * frames, each a whole board: frame 0 is the position before the step with nothing lit; frame 1 lights the step's
 * evidence on that same position; then one frame per square of the step landing, the evidence still lit and every
 * square landed so far lit with it, the last showing the board after the whole step. [captions] has one plain line per
 * frame, two more than the step has squares.
 */
internal class SudokuIntroDemo private constructor(
    override val variant: Int,
    override val captions: List<String>,
    val puzzle: SudokuPuzzle,
    private val frames: List<Frame>,
) : IntroDemo {

    /** One frame: the [board] to show and the [spotlight] lighting the evidence over it, null when nothing is lit. */
    class Frame(val board: SudokuBoard, val spotlight: Spotlight?)

    /** Frame [index], held to the demo's first and last. */
    fun frame(index: Int): Frame = frames[index.coerceIn(frames.indices)]

    companion object {

        fun of(puzzle: SudokuPuzzle, recording: SudokuIntro.Recording): SudokuIntroDemo {
            val step = recording.step
            val evidence = evidence(step, puzzle, recording.before)
            val frames = buildList {
                add(Frame(recording.before, null))
                add(Frame(recording.before, evidence))
                recording.landings.forEachIndexed { i, board -> add(Frame(board, lighting(evidence, recording.squares.take(i + 1)))) }
            }
            return SudokuIntroDemo(recording.variant, SudokuIntroCaptions.of(step, puzzle), puzzle, frames)
        }

        /**
         * The Spotlight of the step's own evidence, the hint's mapper reading it off the position, with the callout and
         * every role but the lit area dropped: a single's target is lit with its evidence, an elimination's struck squares
         * light as they land.
         */
        private fun evidence(step: SudokuStep, puzzle: SudokuPuzzle, board: SudokuBoard): Spotlight {
            val base = SudokuSpotlightMapper.map(step, puzzle, board, playId = 0)
            val cells = base.roles.filterValues { it == SpotlightRole.EVIDENCE || (step is SudokuStep.Single && it == SpotlightRole.TARGET) }.keys
            return base.copy(roles = cells.associateWith { SpotlightRole.EVIDENCE }, callout = null)
        }

        private fun lighting(evidence: Spotlight, landed: Collection<Int>): Spotlight =
            evidence.copy(roles = evidence.roles + landed.associateWith { SpotlightRole.EVIDENCE })
    }
}
