package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tango.render.TangoViolations

/**
 * A guided lesson (spec A12) on one [TangoPlayController]: once [start]ed, the board ghosts
 * the answer on every open square that doesn't hold it yet, and every rule caption and mark
 * is held back, because the rules come in later lessons. The answer lives in this instance
 * only and never reaches the session bytes.
 */
internal class TangoGuide {

    /** The answer per open square; null while unguided. Compose state: the board keys on it. */
    var answer by mutableStateOf<Map<Int, TangoCell>?>(null)
        private set

    val active: Boolean get() = answer != null

    fun start(puzzle: TangoPuzzle) {
        answer = answerOf(puzzle)
    }

    /** [model] as the board draws it: unchanged while unguided, else ghosted with no rule marks. */
    fun apply(model: TangoRendererModel): TangoRendererModel {
        val answer = answer ?: return model
        val violating = TangoViolations.of(model.copy(suppressedSets = emptySet()))
            .mapTo(mutableSetOf()) { it.cells }
        return model.copy(
            suppressedSets = violating,
            ghosts = answer.filter { (idx, value) -> model.cells[idx].value != value },
        )
    }

    private companion object {
        /**
         * The answer for every open square of [puzzle], walked out from the givens by the
         * hint ladder ([TangoRules.hint]): the engine's solver only counts solutions. A
         * guided lesson is a tiny board the ladder finishes, so one it can't is a broken
         * lesson and fails loudly.
         */
        fun answerOf(puzzle: TangoPuzzle): Map<Int, TangoCell> {
            val grid = MutableList(puzzle.cellCount) { idx -> puzzle.givens[idx] }
            while (grid.any { it == null }) {
                val forced = TangoRules.hint(TangoState(puzzle, grid.toList())) as? TangoHint.Forced
                check(forced != null && grid[forced.idx] == null) { "a guided Tango board must be solvable by forced moves" }
                grid[forced.idx] = forced.value
            }
            return (0 until puzzle.cellCount).filterNot(puzzle::isGiven).associateWith { idx -> grid[idx]!! }
        }
    }
}
