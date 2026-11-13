package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/** The session after `index * EVERY` kept moves, with [groupBefore] = the board before the stamp group of the last of them, and the hints applied so far. */
internal class Checkpoint<S>(val session: S, val groupBefore: S?, val hinted: Set<Int>)

/**
 * Owns the checkpoints of one timeline while it is folded: one every [EVERY] kept moves, patched when a
 * tap merge re-folds a stretch, sealed into [Checkpoint]s once the hints are known.
 */
internal class TimelineCheckpoints<S : GameSession>(initial: S) {
    private val sessions = arrayListOf(initial)
    private val groupBefores = arrayListOf<S?>(null)

    /** [moveCount] moves are kept now and the board is [session]; a multiple of [EVERY] takes a checkpoint. */
    fun kept(moveCount: Int, session: S, groupBefore: S?) {
        if (moveCount % EVERY != 0) return
        sessions += session
        groupBefores += groupBefore
    }

    /** A tap merge re-folded the first [moveCount] moves: the checkpoint at that count, if one was taken, now holds [session]. */
    fun refolded(moveCount: Int, session: S) {
        if (moveCount % EVERY == 0) sessions[moveCount / EVERY] = session
    }

    /** Seals the store. Hints are known only now: a move that changed nothing may still add its hints to the kept move before it. */
    fun sealed(hinted: List<Set<Int>>): List<Checkpoint<S>> {
        var hintedSoFar = emptySet<Int>()
        return sessions.indices.map { index ->
            for (move in (index - 1).coerceAtLeast(0) * EVERY until index * EVERY) {
                if (hinted[move].isNotEmpty()) hintedSoFar += hinted[move]
            }
            Checkpoint(sessions[index], groupBefores[index], hintedSoFar)
        }
    }

    companion object {
        const val EVERY = 64

        /** The checkpoint to fold from for a frame with [applied] moves applied; its own move count is `index * EVERY`. */
        fun indexFor(applied: Int): Int = (applied - 1).coerceAtLeast(0) / EVERY
    }
}
