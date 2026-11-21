package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeOpening
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo

/**
 * The Tango technique-intro demo (spec §2.7): one real step on a drill [puzzle], cut into [frames], each a whole board
 * ([Frame.grid]: givens and placed squares) with the [Frame.spotlight] it wears. Frame 0 is the board practice opens
 * on and wears no Spotlight; frame 1 is the same board with the step's evidence lit; then one frame per square of the
 * step, the evidence still lit, each square landed on the grid; the last grid is the board after the whole step.
 * [captions] has one line per frame.
 */
internal class TangoIntroDemo private constructor(
    override val variant: Int,
    override val captions: List<String>,
    val puzzle: TangoPuzzle,
    val frames: List<Frame>,
) : IntroDemo {

    /** One frame: the [grid] shown and the [spotlight] over it (null: none). */
    class Frame(val grid: List<TangoCell?>, val spotlight: Spotlight?)

    companion object {

        /** The demo of [opening], a deduction of a rung (the opening of a focus board never is a reveal). */
        fun of(puzzle: TangoPuzzle, opening: TangoPracticeOpening, variant: Int): TangoIntroDemo {
            val squares = opening.step.squares
            val first = squares.first().shown as TangoStep.Deduction
            val lit = TangoSpotlightMapper.evidence(first, squares.mapTo(HashSet()) { it.idx }, puzzle, playId = 1)
            val frames = mutableListOf(Frame(opening.board, null), Frame(opening.board, lit))
            val captions = mutableListOf(TangoDemoCaptions.OPENING, TangoDemoCaptions.evidence(first))
            val grid = opening.board.toMutableList()
            squares.forEachIndexed { at, square ->
                grid[square.idx] = square.value
                frames += Frame(grid.toList(), lit)
                captions += if (at == 0) TangoDemoCaptions.reason(square.shown, puzzle) else TangoDemoCaptions.again(square.value)
            }
            return TangoIntroDemo(variant, captions, puzzle, frames)
        }
    }
}
