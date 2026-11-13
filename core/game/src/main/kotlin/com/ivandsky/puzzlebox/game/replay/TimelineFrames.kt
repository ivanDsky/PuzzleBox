package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/**
 * Serves the frames of one folded timeline. The typed half: [S] stops here, so a [Timeline] carries no
 * session type. [moves] holds only the moves that changed the board; `checkpoints[i]` is the session
 * after `i * TimelineCheckpoints.EVERY` of them.
 */
internal class TimelineFrames<S : GameSession>(
    private val replay: GameReplay<S>,
    private val facts: (S) -> ReplayFacts,
    private val moves: List<StampedMove>,
    private val kinds: List<MoveKind>,
    private val hinted: List<Set<Int>>,
    private val checkpoints: List<Checkpoint<S>>,
) {
    /** The board after the first [applied] moves; [before] is the board before the last stamp's first move. */
    private class Reached<S>(val applied: Int, val session: S, val before: S?, val hinted: Set<Int>)

    // The one mutable field. A race is harmless: every Reached is immutable and a valid place
    // to fold from, so a lost write costs steps and never a wrong frame.
    @Volatile
    private var reached = Reached(applied = 0, session = checkpoints[0].session, before = null, hinted = emptySet())

    /** `groupStart[i]` = the first move index sharing the stamp of move `i`. */
    private val groupStart = IntArray(moves.size).also { starts ->
        for (index in moves.indices) {
            starts[index] = if (index > 0 && moves[index].atMs == moves[index - 1].atMs) starts[index - 1] else index
        }
    }

    fun tickTimesMs(): LongArray = moves.map { it.atMs }.distinct().toLongArray()

    fun tickKinds(): Array<MoveKind> {
        val ticks = ArrayList<MoveKind>()
        for (index in moves.indices) {
            if (groupStart[index] == index) ticks += kinds[index] else ticks[ticks.lastIndex] = maxOf(ticks.last(), kinds[index])
        }
        return ticks.toTypedArray()
    }

    fun frame(tMs: Long, ended: Boolean): ReplayFrame {
        val applied = appliedBy(tMs)
        val board = reach(applied)
        val sinceMoveMs = if (applied == 0) tMs else tMs - moves[applied - 1].atMs
        return ReplayFrame(board.session, board.before, board.hinted, facts(board.session), tMs, sinceMoveMs, ended)
    }

    /** How many moves are stamped at or before [tMs]. */
    private fun appliedBy(tMs: Long): Int {
        var low = 0
        var high = moves.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (moves[middle].atMs <= tMs) low = middle + 1 else high = middle
        }
        return low
    }

    private fun reach(applied: Int): Reached<S> {
        val last = reached
        if (last.applied == applied) return last
        val checkpoint = TimelineCheckpoints.indexFor(applied)
        val checkpointApplied = checkpoint * TimelineCheckpoints.EVERY
        // Forward play goes on from the last frame. A seek back, or ahead past a checkpoint, starts at the checkpoint.
        val start = last.takeIf { it.applied in checkpointApplied until applied }
            ?: checkpoints[checkpoint].let { Reached(checkpointApplied, it.session, it.groupBefore, it.hinted) }
        var session = start.session
        var hintedSoFar = start.hinted
        // The board before the first move sharing the last applied move's stamp. A group that began before
        // [start] keeps start.before: the last frame's, or the checkpoint's own.
        val groupFirst = if (applied == 0) -1 else groupStart[applied - 1]
        var before = start.before
        for (index in start.applied until applied) {
            if (index == groupFirst) before = session
            // open folded these same moves, so a pure step never refuses one here; at must not throw if one does.
            session = replay.step(session, moves[index].move, moves[index].atMs) ?: session
            if (hinted[index].isNotEmpty()) hintedSoFar = hintedSoFar + hinted[index]
        }
        return Reached(applied, session, before, hintedSoFar).also { reached = it }
    }
}
