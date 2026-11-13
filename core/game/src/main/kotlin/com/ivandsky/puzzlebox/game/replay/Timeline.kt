package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay

/**
 * One opened solve replay: the board at any play time, re-folded from the puzzle's initial session
 * through the game's step. Immutable. Safe to share across threads. It holds no thread and no
 * clock; the caller chooses the dispatcher.
 */
class Timeline private constructor(
    val game: GameId,
    val endMs: Long,
    val outcome: ReplayOutcome,
    private val fold: Fold<*>,
) {
    /** Distinct stamps of the moves that changed the board, ascending. None is above [endMs]. */
    val moveTimesMs: LongArray = fold.tickTimesMs()

    /**
     * What each tick of [moveTimesMs] was, parallel to it. Moves sharing a stamp are one tick; the
     * highest of Mistake > Hint > Undo > Normal wins. A Mistake the game calls transient
     * ([GameReplay.isTransientMistake]) and that the next tick lands within [MISTAKE_SETTLE_MS] of is
     * not one: it takes the highest non-Mistake kind of its own moves. A group that [GameReplay.continuesTap]
     * folds into the next one, which starts within [MISTAKE_SETTLE_MS], is no tick of its own: it takes
     * the next group's stamp, so one tap cycle is one tick.
     */
    val moveKindsAt: Array<MoveKind> = fold.tickKinds()

    /** The board at [tMs], clamped to 0..[endMs]. Folds at most 64 steps. Never throws. */
    fun at(tMs: Long): ReplayFrame {
        val clampedMs = tMs.coerceIn(0, endMs)
        return fold.frame(clampedMs, ended = clampedMs == endMs)
    }

    /**
     * The typed half: [S] stops here, so a timeline carries no session type. [moves] holds only the
     * moves that changed the board; `checkpoints[i]` is the session after `i * CHECKPOINT_EVERY` of them.
     */
    private class Fold<S : GameSession>(
        private val replay: GameReplay<S>,
        private val facts: (S) -> ReplayFacts,
        private val moves: List<StampedMove>,
        private val kinds: List<MoveKind>,
        private val hinted: List<Set<Int>>,
        private val checkpoints: List<Checkpoint<S>>,
    ) {
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
            val checkpoint = (applied - 1).coerceAtLeast(0) / CHECKPOINT_EVERY
            val checkpointApplied = checkpoint * CHECKPOINT_EVERY
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

    /** The session at `moves = applied` kept moves, with [groupBefore] = the board before the group of the last of them. */
    private class Checkpoint<S>(val session: S, val groupBefore: S?, val hinted: Set<Int>)

    /** The board after the first [applied] moves; [before] is the board before the last stamp's first move. */
    private class Reached<S>(val applied: Int, val session: S, val before: S?, val hinted: Set<Int>)

    /**
     * Downgrades transient Mistakes in [kinds] as stamp groups close. One pass; the pending group lives
     * in fields, so a fold allocates nothing per group.
     */
    private class SettleFilter<S : GameSession>(private val replay: GameReplay<S>, private val kinds: MutableList<MoveKind>) {
        private var pendingStart = 0
        private var pendingEnd = 0
        private var pendingBefore: S? = null
        private var pendingAfter: S? = null

        /**
         * The stamp group `start until end` is complete: [before] and [after] bracket it, and the next group
         * starts at [nextAtMs] ([NO_NEXT] = none). Settles the pending Mistake group, whose next group this was.
         */
        fun closed(start: Int, end: Int, atMs: Long, before: S, after: S, nextAtMs: Long) {
            val pendingBefore = pendingBefore
            if (pendingBefore != null) {
                if (replay.isTransientMistake(pendingBefore, checkNotNull(pendingAfter), after)) downgrade(pendingStart, pendingEnd)
                this.pendingBefore = null
                pendingAfter = null
            }
            if (nextAtMs != NO_NEXT && nextAtMs - atMs < MISTAKE_SETTLE_MS && isMistake(start, end)) {
                pendingStart = start
                pendingEnd = end
                this.pendingBefore = before
                pendingAfter = after
            }
        }

        /**
         * The group `start until end` was folded into the stamp group that follows it: [before] and [after]
         * bracket it, and [next] is the board after the first move of that group. A Mistake the game calls
         * transient over that span leaves it, as it would have left the next group's settling.
         */
        fun mergedIntoNext(start: Int, end: Int, before: S, after: S, next: S) {
            if (isMistake(start, end) && replay.isTransientMistake(before, after, next)) downgrade(start, end)
        }

        private fun isMistake(start: Int, end: Int): Boolean {
            for (index in start until end) if (kinds[index] == MoveKind.Mistake) return true
            return false
        }

        private fun downgrade(start: Int, end: Int) {
            var best = MoveKind.Normal
            for (index in start until end) if (kinds[index] != MoveKind.Mistake) best = maxOf(best, kinds[index])
            for (index in start until end) if (kinds[index] == MoveKind.Mistake) kinds[index] = best
        }

        companion object {
            const val NO_NEXT = Long.MAX_VALUE
        }
    }

    companion object {
        private const val CHECKPOINT_EVERY = 64

        /**
         * A Mistake whose next stamp group lands less than this later may be a transient one the player
         * undid at once ([GameReplay.isTransientMistake]); the live twin is MotionTokens.SettleMillis, the
         * delay before a new violation shows. core:game cannot see the design system, so the value is repeated.
         */
        const val MISTAKE_SETTLE_MS = 500L

        /**
         * Decodes [bytes] and folds every move once from [initial]; calls `replay.factsOf(initial)` once.
         * Null = no replay: unreadable bytes, another game, an Open outcome, a move the game does not
         * understand, or a Solved record that does not fold to a solved board. Never throws.
         */
        fun <S : GameSession> open(game: GameId, replay: GameReplay<S>, initial: S, bytes: SolveReplay): Timeline? {
            val record = ReplayCodec.decode(bytes.toByteArray()).getOrNull() ?: return null
            if (record.game != game || record.outcome == ReplayOutcome.Open) return null
            val facts = replay.factsOf(initial)
            val moves = ArrayList<StampedMove>(record.moves.size)
            val kinds = ArrayList<MoveKind>(record.moves.size)
            val hinted = ArrayList<Set<Int>>(record.moves.size)
            val checkpointSessions = arrayListOf(initial)
            val checkpointGroupBefores = arrayListOf<S?>(null)
            var session = initial
            var groupBefore: S? = null
            var groupStart = 0
            val settle = SettleFilter(replay, kinds)
            // The mark of the last move that changed nothing, kept while the next kept move may still share its stamp.
            var pendingAtMs = -1L
            var pendingMark = MoveMark.Plain
            var previous: ReplayMove? = null
            for (stamped in record.moves) {
                // A decoded stamp may lie past endMs. Capped there, the frame at endMs is still the final board.
                val atMs = minOf(stamped.atMs, record.endMs)
                val next = replay.step(session, stamped.move, atMs) ?: return null
                val mark = replay.markOf(session, stamped.move, next, previous)
                previous = stamped.move
                if (next === session) {
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
                    continue
                }
                val merged = if (pendingAtMs == atMs) merge(pendingMark, mark) else mark
                pendingAtMs = -1L
                pendingMark = MoveMark.Plain
                var after = next
                if (moves.isEmpty() || moves.last().atMs != atMs) {
                    if (moves.isNotEmpty() && continuesTap(replay, moves, groupStart, stamped.move, atMs)) {
                        // One tap cycle: the group so far takes this stamp, and its moves are folded again at it.
                        settle.mergedIntoNext(groupStart, moves.size, checkNotNull(groupBefore), session, next)
                        session = checkNotNull(groupBefore)
                        for (index in groupStart until moves.size) {
                            moves[index] = StampedMove(atMs, moves[index].move)
                            session = replay.step(session, moves[index].move, atMs) ?: return null
                            if ((index + 1) % CHECKPOINT_EVERY == 0) checkpointSessions[(index + 1) / CHECKPOINT_EVERY] = session
                        }
                        after = replay.step(session, stamped.move, atMs) ?: return null
                    } else {
                        if (moves.isNotEmpty()) settle.closed(groupStart, moves.size, moves.last().atMs, checkNotNull(groupBefore), session, nextAtMs = atMs)
                        groupBefore = session
                        groupStart = moves.size
                    }
                }
                session = after
                moves += StampedMove(atMs, stamped.move)
                kinds += merged.kind
                hinted += merged.hinted
                if (moves.size % CHECKPOINT_EVERY == 0) {
                    checkpointSessions += session
                    checkpointGroupBefores += groupBefore
                }
            }
            if (moves.isNotEmpty()) {
                settle.closed(groupStart, moves.size, moves.last().atMs, checkNotNull(groupBefore), session, SettleFilter.NO_NEXT)
            }
            if (record.outcome == ReplayOutcome.Solved && !facts(session).solved) return null
            // Hints are known only now: a move that changed nothing may still add its hints to the kept move before it.
            var hintedSoFar = emptySet<Int>()
            val checkpoints = checkpointSessions.indices.map { index ->
                for (move in (index - 1).coerceAtLeast(0) * CHECKPOINT_EVERY until index * CHECKPOINT_EVERY) {
                    if (hinted[move].isNotEmpty()) hintedSoFar += hinted[move]
                }
                Checkpoint(checkpointSessions[index], checkpointGroupBefores[index], hintedSoFar)
            }
            return Timeline(game, record.endMs, record.outcome, Fold(replay, facts, moves, kinds, hinted, checkpoints))
        }

        /** Whether [move], stamped [atMs], continues a tap cycle of the group `groupStart until moves.size`, which ended less than [MISTAKE_SETTLE_MS] ago. */
        private fun <S : GameSession> continuesTap(replay: GameReplay<S>, moves: List<StampedMove>, groupStart: Int, move: ReplayMove, atMs: Long): Boolean {
            if (atMs - moves.last().atMs >= MISTAKE_SETTLE_MS) return false
            for (index in moves.lastIndex downTo groupStart) if (replay.continuesTap(moves[index].move, move)) return true
            return false
        }

        private fun merge(first: MoveMark, second: MoveMark) =
            MoveMark(maxOf(first.kind, second.kind), first.hinted + second.hinted)
    }
}
