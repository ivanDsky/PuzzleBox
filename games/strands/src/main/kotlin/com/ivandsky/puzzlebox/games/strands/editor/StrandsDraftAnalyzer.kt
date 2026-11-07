package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.engine.tiling.StrandsTilingOutcome
import com.ivandsky.puzzlebox.games.strands.engine.tiling.StrandsTilingSearch

/**
 * A Strands draft → its [Verdict]. Blank letters, unnamed paths or a missing spangram leave it
 * Incomplete; otherwise the tiling search runs for up to two tilings, the author's first. Solution
 * values are the word index per cell, row-major, and [Verdict.None] carries the cells no word can
 * cover. Strands shows no difficulty and has no guessing gate. A spent [Budget] reads as
 * [Verdict.Undetermined].
 */
object StrandsDraftAnalyzer {
    const val BLANK_CELLS: String = "strands.blank"     // count = blank letter cells
    const val UNNAMED: String = "strands.unnamed"       // count = paths still needing a word
    const val NO_SPANGRAM: String = "strands.spangram"  // count 0

    /** Every IncompleteReason id [analyze] can emit, so a copy table can prove it covers them all. */
    val reasonIds: Set<String> = setOf(BLANK_CELLS, UNNAMED, NO_SPANGRAM)

    fun analyze(draft: StrandsDraft, budget: Budget): Verdict {
        incomplete(draft)?.let { return Verdict.Incomplete(it) }
        val words = draft.paths.map { checkNotNull(it.word) }
        val outcome = StrandsTilingSearch.bounded(
            rows = StrandsDraft.ROWS,
            cols = StrandsDraft.COLS,
            letters = draft.letters,
            words = words,
            first = authorTiling(draft),
            limit = 2,
            budget = budget,
        )
        if (outcome !is StrandsTilingOutcome.Done) return Verdict.Undetermined
        return when (outcome.tilings.size) {
            0 -> Verdict.None(StrandsTilingSearch.uncoverable(StrandsDraft.ROWS, StrandsDraft.COLS, draft.letters, words))
            1 -> Verdict.Unique(Solution(outcome.tilings[0]))
            else -> {
                val (a, b) = outcome.tilings
                Verdict.Multiple(Solution(a), Solution(b), a.indices.filter { a[it] != b[it] }.toSet())
            }
        }
    }

    private fun incomplete(draft: StrandsDraft): IncompleteReason? {
        val blank = draft.letters.count { it == StrandsDraft.BLANK }
        if (blank > 0) return IncompleteReason(BLANK_CELLS, blank)
        val unnamed = draft.paths.count { it.word.isNullOrBlank() }
        if (unnamed > 0) return IncompleteReason(UNNAMED, unnamed)
        if (draft.spangram !in draft.paths.indices) return IncompleteReason(NO_SPANGRAM, 0)
        return null
    }

    /** The traced paths as a word index per cell, or null when they leave a cell untraced. */
    private fun authorTiling(draft: StrandsDraft): List<Int>? {
        val tiling = MutableList(draft.letters.length) { -1 }
        draft.paths.forEachIndexed { index, path -> path.cells.forEach { cell -> if (cell in tiling.indices) tiling[cell] = index } }
        return tiling.takeIf { -1 !in it }
    }
}
