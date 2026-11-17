package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import kotlin.random.Random

/**
 * One Finish for me attempt on [draft]: the [com.ivandsky.puzzlebox.games.queens.QueensGenerator]
 * recipe with the author's pieces fixed. A painted cell never changes region; the cells the
 * attempt fills are free to move while every region stays in one piece.
 *
 * 1. Join: painted pieces of one region are linked through unpainted cells.
 * 2. Placement: a random answer through the crowns with a queen for every region
 *    ([QueensPinnedPlacement]).
 * 3. Seat: a started region whose queen lies off its cells walks to it; the other queens seed
 *    the regions the author never started.
 * 4. Growth: every other cell joins a neighbouring region, smallest region first.
 * 5. Repair: while [QueensPinnedSearch] finds a second answer, one of its queens on a filled
 *    cell is handed to a neighbouring region, which kills that answer and keeps the intended one.
 *
 * The caller checks that the crowns do not clash ([QueensCrowns.conflicts]).
 *
 * [spread] says where the unpainted cells may go (ticket 107); [Spread.Free] is the recipe above.
 */
internal class QueensPinnedGrowth(
    private val draft: QueensDraft,
    private val budget: Budget,
    private val spread: Spread = Spread.Free,
) {
    /** Which regions an unpainted cell may join. */
    enum class Spread {
        /** Any neighbouring region, the smallest first. */
        Free,

        /** A region the author never started while one has room; a painted region grows only where no other can. */
        UnusedFirst,

        /** Only a region the author never started: no painted region gains a cell, or there is no board. */
        UnusedOnly,
    }

    private val n = draft.size
    private val started = draft.regions.filter { it != QueensDraft.UNPAINTED }.distinct()
    private val neighbours = Array(n * n) { cell ->
        listOfNotNull(
            (cell - n).takeIf { cell / n > 0 },
            (cell + n).takeIf { cell / n < n - 1 },
            (cell - 1).takeIf { cell % n > 0 },
            (cell + 1).takeIf { cell % n < n - 1 },
        ).toIntArray()
    }

    private val placement = QueensPinnedPlacement(draft, neighbours, budget, seedsOnly = spread == Spread.UnusedOnly)
    private val search = QueensPinnedSearch(n, budget)

    /** A fully painted board with exactly one answer, or null when stuck or out of budget. */
    fun attempt(random: Random): QueensDraft? {
        val (reg, seats) = fill(random) ?: return null
        val isQueen = BooleanArray(n * n).also { for (queen in seats) it[queen] = true }
        return if (repair(reg, seats, isQueen, random)) QueensDraft(n, reg.toList(), draft.crowns) else null
    }

    /**
     * Steps 1 to 4, the board before any repair: every cell painted, with the queen cell of
     * each region. The queens are one of its answers, maybe one of several. [QueensRepaint]
     * starts from it. Null when the pieces cannot be joined or seated, or [Spread.UnusedOnly]
     * leaves a cell no unstarted region reaches.
     */
    fun fill(random: Random): Pair<IntArray, IntArray>? {
        val reg = draft.regions.toIntArray()
        if (!join(reg, random)) return null
        val seats = placement.seats(reg, random) ?: return null
        val isQueen = BooleanArray(n * n).also { for (queen in seats) it[queen] = true }
        if (!seat(reg, seats, isQueen, random)) return null
        grow(reg, random)
        return if (spread == Spread.UnusedOnly && QueensDraft.UNPAINTED in reg) null else reg to seats
    }

    private fun join(reg: IntArray, random: Random): Boolean {
        for (region in started) {
            while (true) {
                val piece = piece(reg, reg.indexOf(region), without = NONE)
                if (piece.size == reg.count { it == region }) break
                if (spread == Spread.UnusedOnly) return false
                val link = path(reg, piece, random) { reg[it] == region && it !in piece } ?: return false
                for (cell in link) reg[cell] = region
            }
        }
        return true
    }

    /** Gives every region its queen at [seats]: a walk for a started region, a seed cell for the others. */
    private fun seat(reg: IntArray, seats: IntArray, isQueen: BooleanArray, random: Random): Boolean {
        for (region in (0 until n).shuffled(random)) {
            val queen = seats[region]
            if (reg[queen] == region) continue
            if (region in started) {
                // An earlier walk may have shut the way the placement saw.
                val cells = reg.indices.filterTo(HashSet()) { reg[it] == region }
                val walk = path(reg, cells, random, blocked = isQueen) { it == queen } ?: return false
                for (cell in walk) reg[cell] = region
            } else {
                reg[queen] = region
            }
        }
        return true
    }

    /**
     * As the generator grows regions: the smallest region with room takes one random neighbour.
     * Off [Spread.Free] a region the author never started goes before any painted one, and with
     * [Spread.UnusedOnly] the painted ones never grow, which may leave cells unpainted.
     */
    private fun grow(reg: IntArray, random: Random) {
        val sizes = IntArray(n)
        val frontier = List(n) { LinkedHashSet<Int>() }
        fun claim(cell: Int, region: Int) {
            reg[cell] = region
            sizes[region]++
            neighbours[cell].filterTo(frontier[region]) { reg[it] == QueensDraft.UNPAINTED }
        }
        for (cell in reg.indices) if (reg[cell] != QueensDraft.UNPAINTED) claim(cell, reg[cell])
        while (true) {
            for (cells in frontier) cells.removeAll { reg[it] != QueensDraft.UNPAINTED }
            val region = when (spread) {
                Spread.Free -> (0 until n).filter { frontier[it].isNotEmpty() }.minByOrNull { sizes[it] }
                else -> (0 until n)
                    .filter { frontier[it].isNotEmpty() && (spread == Spread.UnusedFirst || it !in started) }
                    .minWithOrNull(compareBy({ it in started }, { sizes[it] }))
            } ?: return
            claim(frontier[region].elementAt(random.nextInt(frontier[region].size)), region)
        }
    }

    private fun repair(reg: IntArray, seatOf: IntArray, isQueen: BooleanArray, random: Random): Boolean {
        val intended = hashSetOf(reg.indices.filter { isQueen[it] })
        repeat(MAX_REPAIR_STEPS) {
            // The search polls the budget on entry and while it runs, so every step is budgeted.
            val answers = search.answers(reg, limit = 2) ?: return false
            if (answers.size == 1) return true
            val other = answers.firstOrNull { answer -> answer.any { !isQueen[it] } } ?: return false
            val every = ArrayList<Pair<List<Int>, Int>>()
            for (cell in other) {
                if (isQueen[cell] || draft.regions[cell] != QueensDraft.UNPAINTED) continue
                val targets = neighbours[cell].map { reg[it] }.filter { it != reg[cell] }.distinct()
                val leaving = if (targets.isEmpty()) null else leaving(reg, cell, seatOf[reg[cell]])
                if (leaving != null) targets.mapTo(every) { leaving to it }
            }
            val unused = if (spread == Spread.Free) every else every.filter { it.second !in started }
            val moves = if (spread == Spread.UnusedFirst) unused.ifEmpty { every } else unused
            if (moves.isEmpty()) {
                // No queen of the other answer can leave its region, so no repaint ends that
                // answer: it becomes the intended one, unless it drops a crown or was tried.
                if (!other.containsAll(draft.crowns) || !intended.add(other)) return false
                isQueen.fill(false)
                for (queen in other) {
                    isQueen[queen] = true
                    seatOf[reg[queen]] = queen
                }
                return@repeat
            }
            val (cells, region) = moves[random.nextInt(moves.size)]
            for (cell in cells) reg[cell] = region
        }
        return false
    }

    /**
     * What leaves a region with [cell]: the cell and whatever it alone ties to the region's
     * queen at [seat]. Null when that tail holds a cell the author painted.
     */
    private fun leaving(reg: IntArray, cell: Int, seat: Int): List<Int>? {
        val staying = piece(reg, seat, without = cell)
        val leaving = reg.indices.filter { reg[it] == reg[cell] && it !in staying }
        return leaving.takeIf { cells -> cells.all { draft.regions[it] == QueensDraft.UNPAINTED } }
    }

    /** The 4-connected cells of [start]'s region that hold [start], never stepping on [without]. */
    private fun piece(reg: IntArray, start: Int, without: Int): Set<Int> {
        val piece = linkedSetOf(start)
        val queue = ArrayDeque<Int>().apply { addLast(start) }
        while (queue.isNotEmpty()) {
            for (next in neighbours[queue.removeFirst()]) {
                if (next != without && reg[next] == reg[start] && piece.add(next)) queue.addLast(next)
            }
        }
        return piece
    }

    /**
     * The shortest walk from [from] over unpainted cells off [blocked] to the nearest cell
     * [target] accepts: the cells walked, the target among them. Null when every way is shut.
     */
    private fun path(
        reg: IntArray,
        from: Set<Int>,
        random: Random,
        blocked: BooleanArray? = null,
        target: (Int) -> Boolean,
    ): List<Int>? {
        val previous = IntArray(n * n) { NONE }
        val queue = ArrayDeque<Int>()
        for (cell in from.shuffled(random)) {
            previous[cell] = cell
            queue.addLast(cell)
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (next in neighbours[cell]) {
                if (previous[next] != NONE) continue
                val hit = target(next)
                if (!hit && (reg[next] != QueensDraft.UNPAINTED || blocked?.get(next) == true)) continue
                previous[next] = cell
                if (hit) return generateSequence(next) { previous[it] }.takeWhile { it !in from }.toList()
                queue.addLast(next)
            }
        }
        return null
    }

    private companion object {
        const val NONE = -1

        /** Repair steps per attempt: finished size 12 attempts needed at most 118, and a fresh attempt beats a longer walk. */
        const val MAX_REPAIR_STEPS = 150
    }
}
