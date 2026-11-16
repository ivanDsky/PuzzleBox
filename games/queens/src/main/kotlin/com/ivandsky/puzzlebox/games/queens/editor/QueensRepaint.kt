package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import kotlin.random.Random

/**
 * Finish for me when the author's paint cannot all stay: a fully painted board is recoloured,
 * in as few of [draft]'s painted cells as the search finds, until it has one answer through
 * the crowns that [accept] takes, with every region in one piece. A cell [draft] left
 * unpainted is free to recolour.
 *
 * - A fully painted draft is first searched exactly ([fewest]): every way to recolour one
 *   cell, then two, then three. A board from that search changes the fewest cells any board
 *   could. The search gives way after [EXACT_NODES] boards.
 * - Otherwise a descent ([around]), [ROUNDS] times over: it keeps one answer of the filled
 *   board and ends the others a move at a time, then hands back every cell whose change the
 *   result does not need. The cheapest round wins. This is a heuristic: it may change more
 *   cells than the fewest that would do.
 *
 * [budget] is polled by [QueensPinnedSearch] on every board tried and before every round.
 */
internal class QueensRepaint(
    private val draft: QueensDraft,
    private val budget: Budget,
    private val accept: (IntArray) -> Boolean,
) {
    private val n = draft.size
    private val neighbours = Array(n * n) { cell ->
        listOfNotNull(
            (cell - n).takeIf { cell / n > 0 },
            (cell + n).takeIf { cell / n < n - 1 },
            (cell - 1).takeIf { cell % n > 0 },
            (cell + 1).takeIf { cell % n < n - 1 },
        ).toIntArray()
    }
    private val search = QueensPinnedSearch(n, budget)
    private var nodes = 0

    /** The cheapest finished board found, or null. [growth] fills the draft afresh for every round. */
    fun repaint(growth: QueensPinnedGrowth, random: Random): IntArray? {
        var enough = 0
        if (QueensDraft.UNPAINTED !in draft.regions) {
            fewest(draft.regions.toIntArray(), random)?.let { return it }
            // Every board up to EXACT_CELLS changes was tried: one more is the least there is.
            if (nodes <= EXACT_NODES) enough = EXACT_CELLS + 1
        }
        var best: IntArray? = null
        for (round in 0 until ROUNDS) {
            if (budget.exhausted() || (best != null && cost(best) <= enough)) break
            val seeded = Random(random.nextLong())
            val (start, seats) = growth.fill(seeded) ?: continue
            val board = around(start, seats, seeded) ?: continue
            if (best == null || cost(board) < cost(best)) best = board
        }
        return best
    }

    /** The painted cells of the draft that [reg] holds in another region. */
    private fun cost(reg: IntArray): Int = reg.indices.count { changed(reg, it) }

    private fun changed(reg: IntArray, cell: Int): Boolean =
        draft.regions[cell] != QueensDraft.UNPAINTED && draft.regions[cell] != reg[cell]

    /** [start] with the fewest cells recoloured that finish it, [EXACT_CELLS] at most; iterative deepening. */
    private fun fewest(start: IntArray, random: Random): IntArray? {
        nodes = 0
        for (cells in 0..EXACT_CELLS) {
            val reg = start.copyOf()
            if (change(reg, cells, random)) return reg
            if (nodes > EXACT_NODES || budget.exhausted()) return null
        }
        return null
    }

    /**
     * True once [reg] is finished, after recolouring [left] cells at most. Regions may be split
     * on the way; only the finished board is checked. A change ends the answers through its
     * cell and no other, and the changes to go must end every answer seen here but one, so
     * the next cell lies on an even share of them at least, and on a board with one change to
     * go on every answer that misses a crown.
     */
    private fun change(reg: IntArray, left: Int, random: Random): Boolean {
        if (++nodes > EXACT_NODES) return false
        val answers = search.answers(reg, limit = SEEN) ?: return false.also { nodes = EXACT_NODES }
        val doomed = answers.filter { !it.containsAll(draft.crowns) }
        if (answers.size == 1 && doomed.isEmpty() && whole(reg) && accept(reg)) return true
        if (left == 0) return false
        val share = maxOf(1, (answers.size + left - 2) / left)
        val cells = when {
            answers.size <= 1 && doomed.isEmpty() -> reg.indices.toList()
            else -> answers.flatten().distinct().filter { cell -> answers.count { cell in it } >= share && (left > 1 || doomed.all { cell in it }) }
        }
        for (cell in cells.shuffled(random)) {
            val old = reg[cell]
            for (region in targets(reg, cell, left).shuffled(random)) {
                reg[cell] = region
                if (change(reg, left - 1, random)) return true
            }
            reg[cell] = old
        }
        return false
    }

    /**
     * The regions [cell] may join with [left] changes to go: with one, a neighbour's region or
     * a region no cell holds; with more, any region, since a later change can link it.
     */
    private fun targets(reg: IntArray, cell: Int, left: Int): List<Int> {
        val near = if (left > 1) (0 until n).toList() else neighbours[cell].map { reg[it] } + (0 until n).filter { it !in reg }
        return near.filter { it != reg[cell] }.distinct()
    }

    /**
     * [start] recoloured until the queens at [seats], one of its answers, are the only answer;
     * null when stuck, out of moves or budget, or [accept] turns the result down. A move hands
     * a queen cell of another answer to a neighbouring region ([wayOut]), with whatever those
     * cells alone tied to their region's seat, so every region stays in one piece around its
     * seat. The move with the most [worth] is taken, out of the [WIDTH] cells on the most
     * answers less a random third, so that rounds from one fill differ. A cell moves once.
     */
    private fun around(start: IntArray, seats: IntArray, random: Random): IntArray? {
        val reg = start.copyOf()
        val kept = seats.sorted()
        val settled = BooleanArray(n * n).also { for (seat in seats) it[seat] = true }
        repeat(MAX_MOVES) {
            val answers = search.answers(reg, FETCH) ?: return null
            val others = answers.filter { it != kept }
            if (others.isEmpty()) return trimmed(reg, random)
            val weight = IntArray(n * n)
            for (other in others) for (cell in other) if (!settled[cell]) weight[cell]++
            val cells = reg.indices.filter { weight[it] > 0 }.shuffled(random).sortedByDescending { weight[it] }.take(WIDTH)
                .let { top -> top.filter { random.nextInt(3) != 0 }.ifEmpty { top } }
            var moved = emptyList<Int>()
            var target = NONE
            var most = Double.NEGATIVE_INFINITY
            for (cell in cells) {
                val from = reg[cell]
                val way = wayOut(reg, cell, seats[from]).ifEmpty { continue }
                val leaving = leaving(reg, way, seats[from])
                for (region in neighbours[way.first()].map { reg[it] }.filter { it != from }.distinct()) {
                    for (gone in leaving) reg[gone] = region
                    val paid = leaving.count { changed(reg, it) }
                    val left = search.answers(reg, FETCH)?.size
                    for (gone in leaving) reg[gone] = from
                    val worth = worth(answers.size - (left ?: return null), paid - leaving.count { changed(reg, it) })
                    if (worth > most) {
                        most = worth
                        moved = leaving
                        target = region
                    }
                }
            }
            if (target == NONE) return null
            for (cell in moved) {
                reg[cell] = target
                settled[cell] = true
            }
        }
        return null
    }

    /**
     * How good a move is that leaves [gain] answers fewer for [price] more painted cells
     * changed: one that changes none first, then the most answers ended per cell, and a move
     * that ends none last, the cheapest of those first.
     */
    private fun worth(gain: Int, price: Int): Double = when {
        gain <= 0 -> (gain - 1.0) * (maxOf(price, 0) + 1)
        price <= 0 -> FREE + gain
        else -> gain.toDouble() / price
    }

    /**
     * [reg], which has one answer, with every changed cell handed back to its painted region
     * that the answer does not need changed. Null when [accept] takes neither that board nor [reg].
     */
    private fun trimmed(reg: IntArray, random: Random): IntArray? {
        val trimmed = reg.copyOf()
        var cells = reg.indices.filter { changed(reg, it) }.shuffled(random)
        // A cell that cannot go back yet may once a neighbour has: again until a pass returns none.
        do {
            val before = cells.size
            cells = cells.filter { cell ->
                val now = trimmed[cell]
                trimmed[cell] = draft.regions[cell]
                val needed = !whole(trimmed) || search.answers(trimmed, limit = 2)?.size != 1
                if (needed) trimmed[cell] = now
                needed
            }
        } while (cells.size < before)
        return when {
            accept(trimmed) -> trimmed
            !trimmed.contentEquals(reg) && accept(reg) -> reg
            else -> null
        }
    }

    /**
     * The cells [cell] takes with it to another region: itself when it touches one, else the
     * shortest way there through its own region and off its [seat], the far end first. Empty
     * when the seat is in the way.
     */
    private fun wayOut(reg: IntArray, cell: Int, seat: Int): List<Int> {
        val back = IntArray(n * n) { NONE }.also { it[cell] = cell }
        val queue = ArrayDeque<Int>().apply { addLast(cell) }
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            if (neighbours[at].any { reg[it] != reg[cell] }) return generateSequence(at) { step -> back[step].takeIf { it != step } }.toList()
            for (next in neighbours[at]) {
                if (next == seat || back[next] != NONE || reg[next] != reg[cell]) continue
                back[next] = at
                queue.addLast(next)
            }
        }
        return emptyList()
    }

    /** What leaves a region with [way]: its cells and whatever they alone tie to the region's queen at [seat]. */
    private fun leaving(reg: IntArray, way: List<Int>, seat: Int): List<Int> {
        val staying = BooleanArray(n * n).also { for (cell in way) it[cell] = true }
        flood(reg, seat, staying)
        for (cell in way) staying[cell] = false
        return reg.indices.filter { reg[it] == reg[seat] && !staying[it] }
    }

    /** Every region id is on [reg], each in one piece. */
    private fun whole(reg: IntArray): Boolean {
        val seen = BooleanArray(n * n)
        val met = BooleanArray(n)
        for (start in reg.indices) {
            if (seen[start]) continue
            if (met[reg[start]]) return false
            met[reg[start]] = true
            flood(reg, start, seen)
        }
        return met.all { it }
    }

    /** Marks in [seen] the cells of [start]'s region joined to it by 4-neighbour steps over cells not yet seen. */
    private fun flood(reg: IntArray, start: Int, seen: BooleanArray) {
        val queue = ArrayDeque<Int>().apply { addLast(start) }
        seen[start] = true
        while (queue.isNotEmpty()) {
            for (next in neighbours[queue.removeFirst()]) {
                if (seen[next] || reg[next] != reg[start]) continue
                seen[next] = true
                queue.addLast(next)
            }
        }
    }

    private companion object {
        const val NONE = -1

        /** Cells the exact search recolours at most. */
        const val EXACT_CELLS = 3

        /** Boards the exact search tries before it gives way to the descent. */
        const val EXACT_NODES = 10_000

        /** Answers the exact search fetches per board: the more it sees, the fewer cells are worth a change. */
        const val SEEN = 12

        /** Descents per repaint; the cheapest board wins. */
        const val ROUNDS = 12

        /** Moves per descent. */
        const val MAX_MOVES = 60

        /** Answers fetched to weigh a board: more than a move ends, few enough to stay cheap. */
        const val FETCH = 48

        /** Cells tried per move, those on the most fetched answers. */
        const val WIDTH = 8

        /** [worth] of a move that changes no painted cell, above any that changes one. */
        const val FREE = 1_000_000.0
    }
}
