package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.linkedin.LinkedInOpening
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve

/**
 * Encodes a solved Zip [LinkedInSolve] as the LinkedIn play record (path cell indices, backtracks, flawless flag)
 * and the opening play the web client sends before the win: the first two cells of the final path.
 */
object ZipLinkedInPlay {

    private const val PATH_KEY = "zipGamePathBinding"
    private const val BACKTRACKS_KEY = "zipNumBacktracksBinding"
    private const val OPENING_CELLS = 2
    private const val TICK_MS = 1_000L

    /** Null when the final path is not complete: LinkedIn only takes a finished trace. */
    fun encode(solve: LinkedInSolve<ZipSession>): LinkedInPlay? {
        val final = solve.final
        if (!final.isComplete) return null
        return LinkedInPlay(
            states = mapOf(
                PATH_KEY to LinkedInPlayValue.Strings(final.path.map { it.idx.toString() }),
                BACKTRACKS_KEY to LinkedInPlayValue.Number(final.backtrackCount),
            ),
            payloadKeys = mapOf("playRecord" to PATH_KEY, "numBacktracks" to BACKTRACKS_KEY),
            // A backtrack is a flaw too: the captured client reports it as not flawless and not mistake-free.
            mistakeFree = final.mistakeCount == 0 && final.backtrackCount == 0 && solve.mistakeFree,
            opening = LinkedInOpening(
                states = mapOf(
                    PATH_KEY to LinkedInPlayValue.Strings(final.path.take(OPENING_CELLS).map { it.idx.toString() }),
                    BACKTRACKS_KEY to LinkedInPlayValue.Number(0),
                ),
                timeMs = openingTimeMs(solve.timesMs.firstOrNull()),
            ),
        )
    }

    /** The whole second of the first move, as the client's tick reports it; never under one second. */
    private fun openingTimeMs(firstMoveMs: Long?): Long =
        ((firstMoveMs ?: 0L) / TICK_MS * TICK_MS).coerceAtLeast(TICK_MS)
}
