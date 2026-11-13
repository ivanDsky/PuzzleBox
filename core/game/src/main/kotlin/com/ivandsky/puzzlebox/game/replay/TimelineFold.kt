package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/**
 * Folds the decoded moves of one record, in order, into the kept moves of a timeline: steps each move,
 * merges the marks of moves that changed nothing into the kept move they share a stamp with, and
 * re-stamps a tap cycle. Settling and checkpoints are delegated, not done here.
 */
internal class TimelineFold<S : GameSession>(private val replay: GameReplay<S>, initial: S, private val endMs: Long) {
    private val moves = ArrayList<StampedMove>()
    private val kinds = ArrayList<MoveKind>()
    private val hinted = ArrayList<Set<Int>>()
    private val checkpoints = TimelineCheckpoints(initial)
    private val settle = TimelineSettleFilter(replay, kinds)

    /** The board after every move added so far. */
    var session: S = initial
        private set
    private var groupBefore: S? = null
    private var groupStart = 0

    // The mark of the last move that changed nothing, kept while the next kept move may still share its stamp.
    private var pendingAtMs = -1L
    private var pendingMark = MoveMark.Plain
    private var previous: ReplayMove? = null
    private var previousAtMs = -1L

    /** Folds one more decoded move. False = the game does not understand it, so there is no replay. */
    fun add(stamped: StampedMove): Boolean {
        // A decoded stamp may lie past endMs. Capped there, the frame at endMs is still the final board.
        val atMs = minOf(stamped.atMs, endMs)
        val next = replay.step(session, stamped.move, atMs) ?: return false
        val mark = replay.markOf(session, stamped.move, next, previous.takeIf { previousAtMs == atMs })
        previous = stamped.move
        previousAtMs = atMs
        if (next === session) {
            markUnchanged(atMs, mark)
            return true
        }
        val merged = takePending(atMs, mark)
        var after = next
        if (moves.isEmpty() || moves.last().atMs != atMs) {
            if (moves.isNotEmpty() && continuesTap(stamped.move, atMs)) {
                after = restampGroup(stamped.move, atMs, next) ?: return false
            } else {
                openGroup(atMs)
            }
        }
        session = after
        moves += StampedMove(atMs, stamped.move)
        kinds += merged.kind
        hinted += merged.hinted
        checkpoints.kept(moves.size, session, groupBefore)
        return true
    }

    /** No more moves: closes the last stamp group. */
    fun finish() {
        if (moves.isNotEmpty()) {
            settle.closed(groupStart, moves.size, moves.last().atMs, checkNotNull(groupBefore), session, TimelineSettleFilter.NO_NEXT)
        }
    }

    /** The frame server over the folded moves; [facts] is the game's facts function for this puzzle. */
    fun frames(facts: (S) -> ReplayFacts) =
        TimelineFrames(replay, facts, moves, kinds, hinted, checkpoints.sealed(hinted))

    /** A move that changed nothing adds its mark to the kept move at its stamp, else waits for the next kept move at it. */
    private fun markUnchanged(atMs: Long, mark: MoveMark) {
        when {
            moves.isNotEmpty() && moves.last().atMs == atMs -> {
                kinds[kinds.lastIndex] = maxOf(kinds.last(), mark.kind)
                hinted[hinted.lastIndex] = hinted.last() + mark.hinted
            }
            pendingAtMs == atMs -> pendingMark = merge(pendingMark, mark)
            else -> {
                pendingAtMs = atMs
                pendingMark = mark
            }
        }
    }

    /** [mark] of a kept move, merged with the pending mark if that was stamped [atMs]; the pending mark is spent. */
    private fun takePending(atMs: Long, mark: MoveMark): MoveMark {
        val merged = if (pendingAtMs == atMs) merge(pendingMark, mark) else mark
        pendingAtMs = -1L
        pendingMark = MoveMark.Plain
        return merged
    }

    /** The kept move at [atMs] starts a new stamp group: the one before it is complete. */
    private fun openGroup(atMs: Long) {
        if (moves.isNotEmpty()) settle.closed(groupStart, moves.size, moves.last().atMs, checkNotNull(groupBefore), session, nextAtMs = atMs)
        groupBefore = session
        groupStart = moves.size
    }

    /**
     * One tap cycle: the group so far takes [atMs], and its moves are folded again at it; [move] is then
     * stepped on top. [next] is the board after [move] on the old stamps. Null = the game refuses a re-folded move.
     *
     * Kept moves of the earlier group keep the [kinds] and [hinted] computed at their old stamp. That is
     * safe: `markOf` takes no stamp, and re-folding the same moves from the same [groupBefore] reaches
     * the same board after each (a stamp reaches a session only as its completion time), so every mark
     * would be the one computed again. Only a transient Mistake can change, and [settle] has judged it.
     */
    private fun restampGroup(move: ReplayMove, atMs: Long, next: S): S? {
        settle.mergedIntoNext(groupStart, moves.size, checkNotNull(groupBefore), session, next)
        var refolded = checkNotNull(groupBefore)
        for (index in groupStart until moves.size) {
            moves[index] = StampedMove(atMs, moves[index].move)
            refolded = replay.step(refolded, moves[index].move, atMs) ?: return null
            checkpoints.refolded(index + 1, refolded)
        }
        return replay.step(refolded, move, atMs)
    }

    /** Whether [move], stamped [atMs], continues a tap cycle of the open group, which ended less than [Timeline.MISTAKE_SETTLE_MS] ago. */
    private fun continuesTap(move: ReplayMove, atMs: Long): Boolean {
        if (atMs - moves.last().atMs >= Timeline.MISTAKE_SETTLE_MS) return false
        for (index in moves.lastIndex downTo groupStart) if (replay.continuesTap(moves[index].move, move)) return true
        return false
    }

    private fun merge(first: MoveMark, second: MoveMark) = MoveMark(maxOf(first.kind, second.kind), first.hinted + second.hinted)
}
