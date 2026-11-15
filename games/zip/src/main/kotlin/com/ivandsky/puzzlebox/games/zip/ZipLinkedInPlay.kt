package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.MoveKind

/** Encodes a solved Zip [LinkedInSolve] as the LinkedIn play record (path cell indices, backtracks, flawless flag). */
object ZipLinkedInPlay {

    private const val PATH_KEY = "zipGamePathBinding"
    private const val BACKTRACKS_KEY = "zipNumBacktracksBinding"

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
            mistakeFree = final.mistakeCount == 0 && solve.kinds.none { it in FLAWS },
        )
    }

    private val FLAWS = setOf(MoveKind.Mistake, MoveKind.Hint, MoveKind.Undo)
}
