package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * The what-if behind D10 and D11 (lab `chain`): stand a queen on one open cell and play the
 * board forward a round at a time until a [HallViolation] shows it cannot stand there. A round
 * stands every current [SinglesRung] queen at once; when there is none it takes the first
 * deduction of the first cheap rung that has one. A forced queen that an earlier one of the
 * same round rules out is not stood, so the unit that forced it is left empty: a collision is
 * reported as that emptied unit, at the round that forced both.
 *
 * Like the lab, the board is checked before each of the first [CAP] rounds, and after the last
 * one only when that round collided.
 */
internal object LookAheadChain {

    /** Rounds after which a chain that has not broken proves nothing (lab `CHAIN_CAP`). */
    const val CAP = 24

    /** The queen on [cell] broke as [violation] after [rounds] rounds, having [forced] these queens in order. */
    data class Break(val cell: Int, val rounds: Int, val forced: List<Int>, val violation: HallViolation.Violation) {

        /**
         * [rung]'s exclusion of [cell], naming the violation's lowest unit. The evidence is the
         * violation's cells still open in [candidates] plus the forced queens, never [cell].
         */
        fun proof(rung: QueensRung, puzzle: QueensPuzzle, candidates: QueensCandidates): QueensProof {
            val unit = HintReason(violation.kind, violation.units.first(), rung)
            val evidence = violation.cells(puzzle).filter(candidates::isOpen).toSet() + forced - cell
            return QueensProof(QueensDeduction.Exclude(unit, setOf(cell)), evidence)
        }
    }

    /** The chain from a queen on the open [cell]: its break within [cap] rounds, or null when it stalls or runs out of rounds. */
    fun run(
        puzzle: QueensPuzzle,
        candidates: QueensCandidates,
        cell: Int,
        cheap: List<QueensRung>,
        scratch: HallViolation.Scratch = HallViolation.Scratch(puzzle),
        cap: Int = CAP,
    ): Break? {
        val chain = Chain(cell, candidates.place(puzzle, cell))
        var rounds = 0
        while (true) {
            chain.broken(rounds, cap, scratch)?.let { return it }
            if (rounds == cap || !chain.advance(puzzle, cheap)) return null
            rounds++
        }
    }

    /** One chain in flight: the queens it has forced so far and whether its last round collided. */
    private class Chain(val cell: Int, var state: QueensCandidates) {
        val forced = ArrayList<Int>()
        var collided = false

        fun broken(rounds: Int, cap: Int, scratch: HallViolation.Scratch): Break? {
            if (rounds == cap && !collided) return null
            val violation = HallViolation.smallest(state, scratch) ?: return null
            return Break(cell, rounds, forced.toList(), violation)
        }

        /** Plays one round; false when nothing applies. */
        fun advance(puzzle: QueensPuzzle, cheap: List<QueensRung>): Boolean {
            collided = false
            val singles = SinglesRung.cells(puzzle, state)
            if (singles.isNotEmpty()) {
                for (cell in singles) {
                    if (!state.isOpen(cell)) {
                        collided = true
                        continue
                    }
                    state = state.place(puzzle, cell)
                    forced += cell
                }
                return true
            }
            when (val deduction = cheap.firstNotNullOfOrNull { it.step(puzzle, state) } ?: return false) {
                is QueensDeduction.Place -> {
                    state = state.place(puzzle, deduction.cell)
                    forced += deduction.cell
                }
                is QueensDeduction.Exclude -> state = state.exclude(deduction.cells)
            }
            return true
        }
    }

    /**
     * Every open cell's chain on one state, run in lockstep: all chains are checked at one round
     * count, in ascending cell order, before any plays the next round. So the breaks come out in
     * (rounds, cell) order and a caller that stops at the first pays only for the rounds it saw.
     * One [HallViolation.Scratch] serves every chain. Not thread-safe: one per rung call, or one
     * shared by the rungs of a single [QueensRung.Press].
     */
    class Probe(val puzzle: QueensPuzzle, val candidates: QueensCandidates, val cheap: List<QueensRung>) {
        private val scratch = HallViolation.Scratch(puzzle)
        private val found = ArrayList<Break>()
        private val live = ArrayList<Chain>()
        private var rounds = 0

        // Round 0 places each probe only when it is checked, so an early round-0 break skips the rest.
        private var nextCell = 0
        private var checked = 0
        private var done = false

        /** Every break within [maxRounds] rounds, fewest rounds first, then lowest cell, computed only as far as the caller reads. */
        fun breaks(maxRounds: Int): Sequence<Break> =
            generateSequence(0) { it + 1 }.map { breakAt(it, maxRounds) }.takeWhile { it != null }.map { it!! }

        private fun breakAt(index: Int, maxRounds: Int): Break? {
            while (found.size <= index && !done) {
                if (checkNext()) continue
                if (rounds >= maxRounds) break
                nextRound()
            }
            return found.getOrNull(index)?.takeIf { it.rounds <= maxRounds }
        }

        /**
         * Every break at the shallowest round count in [minRounds]..[maxRounds], lowest cell first,
         * or empty. The chains run only until that round count has been checked in full; a later
         * call resumes where an earlier one stopped.
         */
        fun atFirstDepth(minRounds: Int, maxRounds: Int): List<Break> {
            fun shallowest(): Break? = found.firstOrNull { it.rounds in minRounds..maxRounds }
            // A depth below the current round count was checked in full by an earlier call.
            if ((shallowest()?.rounds ?: rounds) >= rounds) {
                while (!done) {
                    if (checkNext()) continue
                    if (shallowest() != null || rounds >= maxRounds) break
                    nextRound()
                }
            }
            val depth = shallowest()?.rounds ?: return emptyList()
            return found.filter { it.rounds == depth }
        }

        /** Checks the next chain at this round count; false when every chain has been checked. */
        private fun checkNext(): Boolean {
            val chain = when {
                rounds == 0 -> nextOpen()?.let { Chain(it, candidates.place(puzzle, it)) }
                checked < live.size -> live[checked]
                else -> null
            } ?: return false
            val broken = chain.broken(rounds, CAP, scratch)
            when {
                broken != null -> {
                    found += broken
                    if (rounds > 0) live.removeAt(checked)
                }
                rounds == 0 -> live += chain
                else -> checked++
            }
            return true
        }

        private fun nextOpen(): Int? {
            val cells = puzzle.regions.size
            while (nextCell < cells && !candidates.isOpen(nextCell)) nextCell++
            return if (nextCell < cells) nextCell++ else null
        }

        private fun nextRound() {
            if (rounds == CAP || live.isEmpty()) {
                done = true
                return
            }
            live.removeAll { !it.advance(puzzle, cheap) }
            rounds++
            checked = 0
        }
    }
}
