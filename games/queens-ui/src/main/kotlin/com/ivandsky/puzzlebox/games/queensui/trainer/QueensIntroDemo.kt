package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensIntro
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper

/**
 * A recorded Queens demo (spec §2.7): the step that opens a Focus board of the technique, cut into [frames]: 2 + one
 * per square the step settles. Frame 0 is the position before the step, which is the board practice opens on. Frame 1
 * lights the evidence. Then one frame per square, in the order they land, each the position with that square made; the
 * last is the position after the whole step. A frame's [Frame.board] is the position it shows, so the host can seek to
 * any of them.
 */
internal class QueensIntroDemo(
    override val variant: Int,
    val puzzle: QueensPuzzle,
    val frames: List<Frame>,
) : IntroDemo {

    /** One frame: [board] is the position, [spotlight] what is lit over it (null for none), [caption] its plain words. */
    class Frame(val board: QueensBoard, val spotlight: Spotlight?, val caption: String)

    override val captions: List<String> = frames.map { it.caption }

    companion object {

        /**
         * The demo of [variant] of [technique] on [puzzle], from the step that opens its Focus board. Null when the
         * puzzle has no such board, or the step that opens it is another variant's (Line lock's two variants are told
         * apart by the squares the step crosses).
         */
        fun of(puzzle: QueensPuzzle, technique: TechniqueId, variant: Int): QueensIntroDemo? {
            val opening = QueensIntro.opening(puzzle, technique) ?: return null
            if (variantOf(puzzle, technique, opening.step) != variant) return null
            val captions = QueensIntroCaptions.of(puzzle, technique, variant, opening)
            val evidence = evidenceRoles(opening.step)
            val base = QueensSpotlightMapper.map(opening.step, puzzle, playId = 0).copy(callout = null)
            val frames = ArrayList<Frame>(captions.size)
            frames += Frame(opening.board, null, captions.first())
            frames += Frame(opening.board, base.copy(roles = evidence), captions[1])
            // One frame per square, each with that square made on top of those before it; the last is the whole step.
            opening.landing.indices.forEach { i ->
                val board = if (i == opening.landing.lastIndex) {
                    opening.after
                } else {
                    opening.board.copy(manual = opening.board.manual + opening.landing.take(i + 1).toSet())
                }
                frames += Frame(board, null, captions[2 + i])
            }
            return QueensIntroDemo(variant, puzzle, frames)
        }

        /** The index in [QueensIntroCopy.variants] of the variant [step] shows. */
        private fun variantOf(puzzle: QueensPuzzle, technique: TechniqueId, step: QueensStep.Deduction): Int =
            if (technique == LINE_LOCK && !QueensIntro.fillsLine(puzzle, step)) QueensIntroCopy.FILL_REGION else QueensIntroCopy.FILL_LINE

        private fun evidenceRoles(step: QueensStep.Deduction): Map<Int, SpotlightRole> =
            step.evidence.associate { it.idx to SpotlightRole.EVIDENCE }

        private val LINE_LOCK = TechniqueId("line-lock")
    }
}
