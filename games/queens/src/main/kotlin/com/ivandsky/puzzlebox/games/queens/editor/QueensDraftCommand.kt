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
}

/** Paint [cells] with [region], capturing each cell's current region so the step reverts exactly. */
fun QueensDraft.paint(cells: Set<Int>, region: Int): QueensDraftCommand.Paint =
    QueensDraftCommand.Paint(cells, region, cells.associateWith { regions[it] })

private fun QueensDraft.with(changes: Map<Int, Int>): QueensDraft {
    val next = regions.toMutableList()
    changes.forEach { (cell, region) -> next[cell] = region }
    return copy(regions = next)
}
