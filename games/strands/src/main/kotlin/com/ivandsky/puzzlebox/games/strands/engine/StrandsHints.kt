package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord

/** The hint economy (A6, A7): what is earned, the meter, and the one hint decision. */
object StrandsHints {

    private const val WORDS_PER_HINT = 3
    private const val STAGES_PER_WORD = 2

    /**
     * Earned minus spent, capped by what the board can still show: two stages per unfound word,
     * less the stages already shown on the active hint.
     */
    fun available(state: StrandsState): Int {
        val earned = state.nonTheme.size / WORDS_PER_HINT - state.hintsUsed
        val unfound = state.puzzle.totalWords - state.found.size
        val showable = STAGES_PER_WORD * unfound - stagesShown(state.activeHint)
        return minOf(earned, showable).coerceAtLeast(0)
    }

    /** Thirds of the meter filled toward the next hint. */
    fun meterThirds(state: StrandsState): Int = state.nonTheme.size % WORDS_PER_HINT

    fun hintFor(state: StrandsState): StrandsHint {
        val active = state.activeHint
        if (active?.stage == HintStage.OrderRevealed) return StrandsHint.OnBoard
        if (available(state) == 0) return StrandsHint.NoneAvailable
        if (active != null) return StrandsHint.Reveal(active.copy(stage = HintStage.OrderRevealed))
        val target = target(state) ?: return StrandsHint.NoneAvailable
        return StrandsHint.Reveal(ActiveHint(target.text, HintStage.Outlined))
    }

    /** Shortest unfound theme word, feed order breaking ties; the spangram only once they are all found. */
    private fun target(state: StrandsState): StrandsWord? =
        state.puzzle.themeWords.filter { it.text !in state.found }.minByOrNull { it.text.length }
            ?: state.puzzle.spangram.takeIf { it.text !in state.found }

    private fun stagesShown(hint: ActiveHint?): Int = when (hint?.stage) {
        null -> 0
        HintStage.Outlined -> 1
        HintStage.OrderRevealed -> 2
    }
}
