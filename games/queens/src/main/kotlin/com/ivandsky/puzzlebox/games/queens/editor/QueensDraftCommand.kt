package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.Command

/**
 * Queens draft edits as undoable commands (spec A5, S3). Resize and clear use
 * [com.ivandsky.puzzlebox.game.editor.ReplaceDraft], not a Queens command.
 */
sealed interface QueensDraftCommand : Command<QueensDraft> {
    /** One ring pick = one step; region == UNPAINTED unpaints. */
    data class Paint(val cells: Set<Int>, val region: Int, val previous: Map<Int, Int>) : QueensDraftCommand {
        override fun apply(state: QueensDraft): QueensDraft = state.with(cells.associateWith { region })
        override fun revert(state: QueensDraft): QueensDraft = state.with(previous)
    }

    /**
     * One crown tap = one step. [placed]: apply adds [cell] to crowns; false removes it.
     * [seed]: region painted on [cell] by a placement on an UNPAINTED cell (revert unpaints it);
     * null otherwise. Removing a crown keeps its paint (ruling round 6).
     */
    data class Crown(val cell: Int, val placed: Boolean, val seed: Int?) : QueensDraftCommand {
        override fun apply(state: QueensDraft): QueensDraft = if (placed) place(state) else lift(state)
        override fun revert(state: QueensDraft): QueensDraft = if (placed) lift(state) else place(state)

        private fun place(state: QueensDraft): QueensDraft {
            val crowned = state.copy(crowns = state.crowns + cell)
            return if (seed == null) crowned else crowned.with(mapOf(cell to seed))
        }

        private fun lift(state: QueensDraft): QueensDraft {
            val lifted = state.copy(crowns = state.crowns - cell)
            return if (seed == null) lifted else lifted.with(mapOf(cell to QueensDraft.UNPAINTED))
        }
    }
}

/** Paint [cells] with [region], capturing each cell's current region so the step reverts exactly. */
fun QueensDraft.paint(cells: Set<Int>, region: Int): QueensDraftCommand.Paint =
    QueensDraftCommand.Paint(cells, region, cells.associateWith { regions[it] })

private fun QueensDraft.with(changes: Map<Int, Int>): QueensDraft {
    val next = regions.toMutableList()
    changes.forEach { (cell, region) -> next[cell] = region }
    return copy(regions = next)
}
