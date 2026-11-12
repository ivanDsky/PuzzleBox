package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayMove

/**
 * One recorded Strands action: what happened on the board, not what was pressed. A hint names
 * its word by position, so a replay needs no hint engine to apply it.
 */
sealed interface StrandsReplayMove {

    /** A traced path sent in, cells in spelling order. */
    data class Submit(val path: List<Coord>) : StrandsReplayMove

    /** One counted hint: [word] indexes the theme words, then the spangram at `themeWords.size`. */
    data class Hint(val word: Int, val stage: HintStage) : StrandsReplayMove {
        /** The word named; [word] is range-checked by [of], so only the spangram index is special. */
        fun wordIn(puzzle: StrandsPuzzle): StrandsWord =
            if (word == puzzle.themeWords.size) puzzle.spangram else puzzle.themeWords[word]
    }

    /** [cols] is the board width the path cells were packed with. */
    fun toReplayMove(cols: Int): ReplayMove = when (this) {
        is Submit -> ReplayMove(SUBMIT, IntArray(path.size) { path[it].row * cols + path[it].col })
        is Hint -> ReplayMove(HINT, intArrayOf(word, stage.ordinal))
    }

    companion object {
        /** Null when [move] is not a Strands move on [puzzle]'s board. */
        fun of(move: ReplayMove, puzzle: StrandsPuzzle): StrandsReplayMove? {
            val args = move.args
            return when (move.kind) {
                SUBMIT -> submit(args, puzzle)
                HINT -> hint(args, puzzle)
                else -> null
            }
        }
    }
}

// Persisted in every stored replay: never renumber, only append.
private const val SUBMIT = 0
private const val HINT = 1

private fun submit(args: IntArray, puzzle: StrandsPuzzle): StrandsReplayMove.Submit? {
    if (args.isEmpty() || args.size > ReplayCodec.MAX_ARGS) return null
    val cellCount = puzzle.rows * puzzle.cols
    if (args.any { it !in 0 until cellCount }) return null
    return StrandsReplayMove.Submit(args.map { Coord(it / puzzle.cols, it % puzzle.cols) })
}

private fun hint(args: IntArray, puzzle: StrandsPuzzle): StrandsReplayMove.Hint? {
    if (args.size != 2) return null
    val word = args[0].takeIf { it in 0..puzzle.themeWords.size } ?: return null
    val stage = HintStage.entries.getOrNull(args[1]) ?: return null
    return StrandsReplayMove.Hint(word, stage)
}
